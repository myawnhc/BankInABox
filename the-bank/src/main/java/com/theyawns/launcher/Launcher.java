package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.Constants;
import com.theyawns.domain.payments.*;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class Launcher {

    protected HazelcastInstance hazelcast;
    protected IExecutorService distributedES;
    private final static ILogger log = Logger.getLogger(Launcher.class);
    private static RunMode runMode = RunMode.Demo;
    private static CloudPlatform cloudPlatform = CloudPlatform.None;
    // externalJet = client/server mode, else embedded mode
    private static boolean externalJet = true;

    // Change this when Jet runs in the cloud
    private static final boolean JetSupportedInCloud = false;
    private static final boolean MapLoaderSupportedInCloud = false;

    // Only here for triggering eager cache load
    private IMap<String, Merchant> merchantMap;
    private IMap<String, Account> accountMap;

    protected void init() {
    	new EnvironmentSetup();
        ClientConfig cc = new XmlClientConfigBuilder().build();
        
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

        log.info("Getting preAuth map [lazy]");
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        log.info("Getting merchant map");  // Eager load will hang here ...
        main.merchantMap = main.hazelcast.getMap(Constants.MAP_MERCHANT);
        log.info("Getting account map");
        main.accountMap = main.hazelcast.getMap(Constants.MAP_ACCOUNT);
        log.info("Maps initialized");

        preAuthMap.addEntryListener(new PreauthMapListener(main.hazelcast), true);

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
            executor.submit(merchantAvgTask);
        } else {
            log.info("Jet pipeline disabled since running in cloud");
        }

        IScheduledExecutorService dses = main.hazelcast.getScheduledExecutorService("scheduledExecutor");
        //ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        System.out.println("________________________");
        System.out.println("RUN MODE " + getRunMode());
        System.out.println("________________________");
        if (getRunMode() == RunMode.Demo) {
            PumpGrafanaStats stats = new PumpGrafanaStats();
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
        main.distributedES.executeOnAllMembers(locationBasedRuleExecutor);
        System.out.println("Submitted RuleSetExecutor for location rules to distributed executor service (all members)");

        RuleSetExecutor merchantRuleSetExecutor = new RuleSetExecutor(Constants.QUEUE_MERCHANT,
                new MerchantRuleSet(), Constants.MAP_PPFD_RESULTS);
        main.distributedES.executeOnAllMembers(merchantRuleSetExecutor);
        System.out.println("Submitted RuleSetExecutor for merchant rules to distributed executor service (all members)");

        // TODO: add executors for Credit rules, any others

        AggregationExecutor aggregator = new AggregationExecutor();
        main.distributedES.executeOnAllMembers(aggregator);
        System.out.println("Submitted AggregationExecutor to distributed executor service (all members)");

        if (cloudPlatform != CloudPlatform.None && !MapLoaderSupportedInCloud) {
            /* This is a lot less efficient than the MapLoader implementation but will only
             * be used for a short time until MapLoader support is available in the cloud
             */
            log.info("MapLoader bypass -- Manually load merchants");
            MerchantTable merchantTable = new MerchantTable();
            Iterable<String> merchantIDs = merchantTable.loadAllKeys();
            for (String merchantID : merchantIDs) {
                main.merchantMap.set(merchantID, merchantTable.load(merchantID));
            }

            log.info("MapLoader bypass -- Manually load accounts");
            AccountTable accountTable = new AccountTable();
            Iterable<String> accountIDs = accountTable.loadAllKeys();
            for (String accountID : accountIDs) {
                main.accountMap.set(accountID, accountTable.load(accountID));
            }
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
        }

        log.info("Beginning transaction load");
        LazyPreAuthLoader loader = new LazyPreAuthLoader();
        //loader.run();
        main.distributedES.submit(loader);
        //log.info("All transactions loaded to preAuth");


        // This has no purpose other than monitoring the backlog during debug
        while (true) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Transaction backlog (preAuth map size) " + preAuthMap.size());
            if (preAuthMap.size() == 0) {
                System.out.println("All transactions processed, exiting");
                System.out.println("________________________");
                System.out.println("End: " + new java.util.Date());
                System.out.println("________________________");
                System.exit(0);
            }
        }
    }

    private static void parseArgs(String[] args) {
        String cloud = System.getProperty("bib.cloud");
        String runmode = System.getProperty("bib.runmode");
        String jetmode = System.getProperty("bib.jetmode");

        for (String arg : args) {
            if (arg.startsWith("bib.cloud"))
                cloud = arg.substring(arg.indexOf("=") + 1);
            else if (arg.startsWith("bib.runmode"))
                runmode = arg.substring(arg.indexOf("=") + 1);
            else if (arg.startsWith("bib.jetmode"))
                jetmode = arg.substring(arg.indexOf('=') + 1);
            else if (arg.contains("usage") || arg.contains("help"))
                usage();
        }

        if (cloud != null) {
            cloud = cloud.toLowerCase();
            if (cloud.equals("none"))
                cloudPlatform = CloudPlatform.None;
            else if (cloud.equals("hce"))
                cloudPlatform = CloudPlatform.HZC_Enterprise_AWS;
            else {
                System.out.println("bib.cloud|" + cloud + "|");
                usage();
            }
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
        log.info("bib.jetMode client/server? " + externalJet);

    }

    private static void usage() {
        System.out.println("The following settings can be passed on the command line or as System properties\n" +
                " command line args will override properties " +
                " (keys are case sensitive, values not):");
        System.out.println("bib.cloud=[none, hce]"); // future: AWS, GCP, Azure, maybe HCS
        System.out.println("bib.runmode=[demo, benchmark]");
        System.out.println("bib.jetmode=[embedded,cs]");
    }
}
