package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.Constants;
import com.theyawns.cloud.CloudConfigUtil;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.PumpGrafanaStats;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.domain.payments.database.AccountTable;
import com.theyawns.domain.payments.database.LazyPreAuthLoader;
import com.theyawns.domain.payments.database.MerchantTable;
import com.theyawns.executors.AggregationExecutor;
import com.theyawns.executors.RuleSetExecutor;
import com.theyawns.listeners.PreauthMapListener;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.pipelines.AdjustMerchantTransactionAverage;
import com.theyawns.rulesets.LocationBasedRuleSet;
import com.theyawns.rulesets.MerchantRuleSet;
import com.theyawns.util.EnvironmentSetup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Launcher {

    protected HazelcastInstance hazelcast;
    protected IExecutorService distributedES;
    protected ExecutorService localES;
    //protected CompletionService<Exception> completionService ;
    private final static ILogger log = Logger.getLogger(Launcher.class);
    private static RunMode runMode = RunMode.Demo;
    private static CloudPlatform cloudPlatform = CloudPlatform.None;
    private static boolean peered = false;
    // externalJet = client/server mode, else embedded mode
    private static boolean externalJet = false;
    private static String granfanaHost = null;

    // Change this when Jet runs in the cloud
    private static final boolean JetSupportedInCloud = false;
    private static final boolean MapLoaderSupportedInCloud = false;

    // Only here for triggering eager cache load
    private IMap<String, Merchant> merchantMap;
    private IMap<String, Account> accountMap;

    private List<Future<Exception>> executorFutures = new ArrayList<>();

    protected void init() {
    	new EnvironmentSetup();
        //ClientConfig cc = new XmlClientConfigBuilder().build();

        // TODO: should introduce a command line arg to override this
        String configname = "local";

        // Note that parseArgs called before init so cloudPlatform should be correctly set
        switch (cloudPlatform) {
            case None:                  configname = "local"; break;
            case HZC_Starter:           configname = "cloud-starter"; break;
            case HZC_Enterprise_AWS:
                if (peered)
                    configname = "cloud-enterprise-aws-peered";
                else
                    configname = "cloud-enterprise-aws-public";
                break;
            case HZC_Enterprise_Azure:
                if (peered)
                    configname = "cloud-enterprise-azure-peered";
                else
                    configname = "cloud-enterprise-azure-public";
                break;
            case HZC_Enterprise_GCP:
            default:
                throw new IllegalArgumentException("Cloud configurator not implemented " + cloudPlatform);
        }

        ClientConfig cc = CloudConfigUtil.getClientConfigForCluster(configname);


        // Clients only have one discovery mechanism
        if (cc.getNetworkConfig().getKubernetesConfig().isEnabled()
        		&& cc.getNetworkConfig().getAddresses().size() > 0) {
        	log.info("Remove listed server addresses in favour of Kubernetes discovery.");
        	cc.getNetworkConfig().setAddresses(new ArrayList<>());
        }
        
        cc.setInstanceName("Launcher");

        hazelcast = HazelcastClient.newHazelcastClient(cc);
        log.info("Getting distributed executor service");
        distributedES = hazelcast.getExecutorService("executor");
        //completionService = new ExecutorCompletionService<>(distributedES);
        localES = Executors.newFixedThreadPool(1);

        // If grafana host set by command line, override EnvironmentSetups default to localhost
        if (granfanaHost != null)
            System.setProperty("GRAFANA", granfanaHost);

        log.info("init() complete");
    }

    public static RunMode getRunMode() { return runMode; }
    public static void setRunMode(RunMode mode) { runMode = mode; }

    public static boolean getJetInClientServerMode() { return externalJet; }

    // Currently not used in this configuration, but might add payment rules back
    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
            // TODO: configure filter to take odd-numbered transactions
        }
    }

    private /*static*/ class AdjustMerchantAvgTask implements Runnable, Serializable {
        public void run() {
            System.out.println("AdjustMerchangeAvgTask Runnable has been started");
            AdjustMerchantTransactionAverage amta = new AdjustMerchantTransactionAverage();
            amta.setHazelcastInstance(hazelcast);
            amta.setJetClusterIsExternal(externalJet);
            amta.run();
        }
    }

    // Used only when run via DualLauncher
    public static boolean isEven(String txnId) {
        int numericID = Integer.parseInt(txnId);
        boolean result =  (numericID % 2) == 0;
        return result;
    }

    public static void main(String[] args) {
        System.out.println("________________________");
        System.out.println("Start: " + new java.util.Date());
        System.out.println("________________________");

        parseArgs(args);

        Launcher main = new Launcher();
        main.init();



        if (BankInABoxProperties.COLLECT_LATENCY_STATS || BankInABoxProperties.COLLECT_TPS_STATS) {
            ExecutorService executor = Executors.newCachedThreadPool();
            System.out.println("Launcher initiating PerfMonitor via non-HZ executor service");
            executor.submit(PerfMonitor.getInstance());
        }

        ///////////////////////////////////////
        // Start up the various Jet pipelines
        ///////////////////////////////////////
        // TODO: not sure there's any advantage to using IMDG executor service here
        // over plain Java
        if (JetSupportedInCloud || cloudPlatform == CloudPlatform.None || externalJet) {
            log.info("Creating AdjustMerchantAvgTask");
            AdjustMerchantAvgTask merchantAvgTask = main.new AdjustMerchantAvgTask();
            //main.distributedES.submit(merchantAvgTask);
            // This is a Jet job so doesn't need to run in the IMDG cluster ...
            log.info("Submitting AdjustMerchantAvgTask to newSingleThreadExecutor");
            ExecutorService executor = Executors.newSingleThreadExecutor();
 // FIXME:           executor.submit(merchantAvgTask);
        } else {
            log.info("Jet pipeline disabled since running in cloud");
        }

        IScheduledExecutorService dses = main.hazelcast.getScheduledExecutorService("scheduledExecutor");
        //ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        System.out.println("________________________");
        System.out.println("RUN MODE " + getRunMode());
        System.out.println("________________________");
        if (getRunMode() == RunMode.Demo) {
            String host = System.getProperty(EnvironmentSetup.GRAFANA);
            // Check for command-line override of GrafanaHost
            if (granfanaHost != null)
                host = granfanaHost;
            if (host!=null && host.length() > 0) {
                log.info("Graphite sink: '" + EnvironmentSetup.GRAFANA + "'=='" + host + "'");
            } else {
                log.info("Graphite sink: '" + EnvironmentSetup.GRAFANA + "'=='" + host
                        + "', using localhost for Graphite.");
                host = "localhost";
            }

            PumpGrafanaStats stats = new PumpGrafanaStats(host);
            try {
                dses.scheduleAtFixedRate(stats, 20, 5, TimeUnit.SECONDS);
            } catch (DuplicateTaskException dte) {
                ; // OK to ignore
            } catch (RejectedExecutionException ree) {
                log.info("PumpGrafanaStats scheduled execution rejected");
            }
        }

        // Setup Executors for RuleSets

        RuleSetExecutor locationBasedRuleExecutor = new RuleSetExecutor(Constants.QUEUE_LOCATION,
                new LocationBasedRuleSet(), Constants.MAP_PPFD_RESULTS);
        //Set<Member> members = main.hazelcast.getCluster().getMembers();
        try {
            Map<Member,CompletableFuture<Exception>> futures = main.distributedES.submitToAllMembers(locationBasedRuleExecutor);
            main.executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("Submitted RuleSetExecutor for location rules to distributed executor service (all members)");

        RuleSetExecutor merchantRuleSetExecutor = new RuleSetExecutor(Constants.QUEUE_MERCHANT,
                new MerchantRuleSet(), Constants.MAP_PPFD_RESULTS);
        try {
            Map<Member,CompletableFuture<Exception>> futures = main.distributedES.submitToAllMembers(merchantRuleSetExecutor);
            main.executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("Submitted RuleSetExecutor for merchant rules to distributed executor service (all members)");

        // TODO: add executors for Credit rules, any others

        AggregationExecutor aggregator = new AggregationExecutor();
        try {
            Map<Member,Future<Exception>> futures = main.distributedES.submitToAllMembers(aggregator);
            main.executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("Submitted AggregationExecutor to distributed executor service (all members)");

        long maploaderEagerStart = System.currentTimeMillis();
        log.info("Getting preAuth map [lazy]");
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        log.info("Getting merchant map");
        main.merchantMap = main.hazelcast.getMap(Constants.MAP_MERCHANT);
        log.info("Getting account map");
        main.accountMap = main.hazelcast.getMap(Constants.MAP_ACCOUNT);
        //log.info("Maps initialized");

        // All processing is triggered from this listener
        preAuthMap.addEntryListener(new PreauthMapListener(main.hazelcast), true);

        if (cloudPlatform != CloudPlatform.None && !MapLoaderSupportedInCloud) {
            /* This is a lot less efficient than the MapLoader implementation but will only
             * be used for a short time until MapLoader support is available in the cloud
             */
            long start = System.currentTimeMillis();
            log.info("MapLoader bypass -- Manually load merchants");
            MerchantTable merchantTable = new MerchantTable();
            List<String> merchantIDs = merchantTable.allKeys();
            Map<String,Merchant> merchantData = merchantTable.loadAll(merchantIDs);
            main.merchantMap.putAll(merchantData);
            log.info((System.currentTimeMillis() - start)+"ms to load merchants");

            start = System.currentTimeMillis();
            log.info("MapLoader bypass -- Manually load accounts");
            AccountTable accountTable = new AccountTable();
            List<String> accountIDs = accountTable.allKeys();
            Map<String,Account> accountData = accountTable.loadAll(accountIDs);
            main.accountMap.putAll(accountData);
            log.info((System.currentTimeMillis() - start)+"ms to load accounts");
        } else {
            log.info("Waiting for pre-loads to complete (Account and Merchant tables)");
            while (true) {
                // Wait until preload of Merchant and Account maps are done before starting load into preAuth
                log.info(main.merchantMap.size() + " of " + BankInABoxProperties.MERCHANT_COUNT + " merchants"); // Lazy load will hang here
                log.info(main.accountMap.size() + " of " + BankInABoxProperties.ACCOUNT_COUNT + " accounts");
                if (main.merchantMap.size() >= BankInABoxProperties.MERCHANT_COUNT &&
                        main.accountMap.size() >= BankInABoxProperties.ACCOUNT_COUNT)
                    break;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println((System.currentTimeMillis()-maploaderEagerStart) +
                    "ms for MapLoader to eagerly load Accounts and Merchants");
        }

        log.info("Beginning transaction load");
        LazyPreAuthLoader loader = new LazyPreAuthLoader(getRunMode(),
                BankInABoxProperties.PREAUTH_CHUNK_SIZE,
                BankInABoxProperties.TRANSACTION_COUNT,
                BankInABoxProperties.PREAUTH_HIGH_LIMIT,
                BankInABoxProperties.PREAUTH_CHECK_INTERVAL);
        //loader.run();
        Future<Exception> loaderExc = null;
        try {
            // Might change this to always use local, or at least any time the
            // cluster is physically colocated with the client and (more importantly) database
            if (cloudPlatform == CloudPlatform.None)
                loaderExc = main.distributedES.submit(loader);
            else {
                loader.setHazelcastInstance(main.hazelcast);
                loaderExc = main.localES.submit(loader);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        //log.info("All transactions loaded to preAuth");

        // This has no purpose other than monitoring the backlog during debug
        System.out.println("Everything has been started, just checking for exceptions from executors");
        while (true) {
            try {
                Thread.sleep(30000);
                // Look for exceptions
                try {
                    Exception e = loaderExc.get(1, TimeUnit.SECONDS);
                    if (e != null) {
                        log.severe("Error in preAuth loader", e);
                    }
                    for (Future<Exception> fe : main.executorFutures) {
                        if (fe.get(1, TimeUnit.SECONDS) != null) {
                            log.severe("Error in executor", e);
                        }
                    }
                } catch (TimeoutException to) {
                    System.out.println("Launcher finished exception checking after wakeup");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // For visibility to what's going on in cloud - gives us TPS results that
            // otherwise are written to console
            IMap<String,String> rsExecStatusMap = main.hazelcast.getMap("RuleSetExecutorStatus");
            for (String key : rsExecStatusMap.keySet()) {
                System.out.printf("%s %s\n", key, rsExecStatusMap.get(key));
            }

            System.out.println("Transaction backlog (preAuth map size) " + preAuthMap.size());
            if (preAuthMap.size() == 0) { // TODO: maybe make this benchmark mode only?
                System.out.println("All transactions processed, exiting");
                System.out.println("________________________");
                System.out.println("End: " + new java.util.Date());
                System.out.println("________________________");
                System.exit(0);
            }
        }
    }

    private static void parseArgs(String[] args) {
        // First get system properties
        String cloud = System.getProperty("bib.cloud");
        String runmode = System.getProperty("bib.runmode");
        String jetmode = System.getProperty("bib.jetmode");
        String peered = System.getProperty("bib.peered");

        // Overwrite system properties with command line values if present
        for (String arg : args) {
            if (arg.startsWith("bib.cloud"))
                cloud = arg.substring(arg.indexOf("=") + 1);
            else if (arg.startsWith("bib.runmode"))
                runmode = arg.substring(arg.indexOf("=") + 1);
            else if (arg.startsWith("bib.jetmode"))
                jetmode = arg.substring(arg.indexOf('=') + 1);
            else if (arg.startsWith("bib.peered"))
                peered = arg.substring(arg.indexOf("=") + 1);
            else if (arg.startsWith("bib.grafana"))
                granfanaHost = arg.substring(arg.indexOf("=") + 1);
            else if (arg.contains("usage") || arg.contains("help"))
                usage();
        }

        if (cloud != null) {
            cloud = cloud.toLowerCase();
            if (cloud.equals("none"))
                cloudPlatform = CloudPlatform.None;
            else if (cloud.equals("hzc_starter"))
                cloudPlatform = CloudPlatform.HZC_Starter;
            else if (cloud.equals("hzce_aws"))
                cloudPlatform = CloudPlatform.HZC_Enterprise_AWS;
            else if (cloud.equals("hzce_azure"))
                cloudPlatform = CloudPlatform.HZC_Enterprise_Azure;
            else {
                System.out.println("bib.cloud|" + cloud + "|");
                usage();
            }
        }
        if (peered != null) {
            peered = peered.toLowerCase();
            Launcher.peered = "true".equals(peered) || "yes".equals(peered);
        }
        if (runmode != null) {
            runmode = runmode.toLowerCase();
            if (runmode.equals("demo"))
                runMode = RunMode.Demo;
            else if (runmode.equals("benchmark"))
                runMode= RunMode.Benchmark;
            else {
                System.out.println("bib.runmode|" + runmode + "|");
                usage();
            }
        }
        if (jetmode != null) {
            jetmode = jetmode.toLowerCase();
            if (jetmode.equals("embedded"))
                externalJet = false;
            else if (jetmode.equals("cs"))
                externalJet = true;
            else {
                System.out.println("bib.jetmode|" + jetmode + "|");
                usage();
            }
        }

        // Debugging output
        log.info("After parsing system properties and command line arguments:");
        log.info("bib.cloud " + cloudPlatform);
        log.info("bib.runMode " + runMode);
        log.info("bib.peered " + Launcher.peered);
        log.info("bib.jetMode client/server? " + externalJet);
        log.info("bib.grafana " + granfanaHost);

    }

    private static void usage() {
        System.out.println("The following settings can be passed on the command line or as System properties\n" +
                " command line args will override properties " +
                " (keys are case sensitive, values not):");
        System.out.println("bib.cloud=[none, hzc_starter, hzce_aws, hzce_azure]"); // future: GCP, IBM
        System.out.println("bib.peered=[true,false] (false is default, uses public access)");
        System.out.println("bib.runmode=[demo, benchmark]");
        System.out.println("bib.jetmode=[embedded,cs]");
        System.out.println("bib.grafana=[hostname or ip address] (defaults to localhost)");
        System.exit(0);
    }
}
