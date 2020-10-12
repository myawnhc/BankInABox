package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.HazelcastClientNotActiveException;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Member;
import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.Constants;
import com.theyawns.cloud.CloudConfig;
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
import com.theyawns.executors.ExecutorStatusMapKey;
import com.theyawns.executors.RuleSetExecutor;
import com.theyawns.listeners.PreauthMapListener;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.pipelines.AdjustMerchantTransactionAverage;
import com.theyawns.rulesets.LocationBasedRuleSet;
import com.theyawns.rulesets.MerchantRuleSet;
import com.theyawns.util.EnvironmentSetup;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
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

    private static Date runStarted;
    private static boolean verbose = false;

    protected void init() {
    	new EnvironmentSetup();
    	// ClientConfig is now built in CloudConfigUtil
        //ClientConfig cc = new XmlClientConfigBuilder().build();

        // Default config to use if no command line argument is passed
        String configname = "local";

        // Note that parseArgs is called before init so cloudPlatform should be correctly set
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

        CloudConfig cloudConfig = CloudConfigUtil.getConfig(configname);

        // Override discovery token and URL if K8S has set env variables for them
        if (System.getProperty("hz.is.cloud.enterprise", "false").equalsIgnoreCase("true")) {
            String envDiscoveryToken = System.getProperty("hz.ce.discovery.token");
            if (envDiscoveryToken != null)
                cloudConfig.setDiscoveryToken(envDiscoveryToken);
            String envUrlBase = System.getProperty("hz.ce.urlbase");
            if (envUrlBase != null)
                cloudConfig.setUrlBase(envUrlBase);
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
        //log.info("Getting distributed executor service");
        distributedES = hazelcast.getExecutorService("executor");
        localES = Executors.newFixedThreadPool(1);

        // If grafana host set by command line, override EnvironmentSetups default to localhost
        if (granfanaHost != null)
            System.setProperty("GRAFANA", granfanaHost);

        // Clear any data that might be left in the cluster from previous runs
        IMap resultMap = hazelcast.getMap(Constants.MAP_PPFD_RESULTS);
        resultMap.clear();
        IMap preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        preAuthMap.clear();
        IMap approvedMap = hazelcast.getMap(Constants.MAP_APPROVED);
        approvedMap.clear();
        IMap rejectedForFraudMap = hazelcast.getMap(Constants.MAP_REJECTED_FRAUD);
        rejectedForFraudMap.clear();
        IMap rejectedForCreditMap = hazelcast.getMap(Constants.MAP_REJECTED_CREDIT);
        rejectedForCreditMap.clear();
        IQueue completedTransactionIDs = hazelcast.getQueue(Constants.QUEUE_COMPLETIONS);
        completedTransactionIDs.clear();
        // reset doesn't clear, would have to get and then subtract if non-zero.
        PNCounter approvalCounter = hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED);
        long val = approvalCounter.get();
        if (val > 0) approvalCounter.getAndSubtract(val);
        PNCounter rejectedForFraudCounter = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD);
        val = rejectedForFraudCounter.get();
        if (val > 0) rejectedForFraudCounter.getAndSubtract(val);
        PNCounter rejectedForCreditCounter = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT);
        val = rejectedForCreditCounter.get();
        if (val > 0) rejectedForCreditCounter.getAndSubtract(val);
        PNCounter totalLatency = hazelcast.getPNCounter(Constants.PN_COUNT_TOTAL_LATENCY);
        val = totalLatency.get();
        if (val > 0) totalLatency.getAndSubtract(val);

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
            //System.out.println("AdjustMerchantAvgTask Runnable has been started");
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
        runStarted = new java.util.Date();
        System.out.println("Start: " + runStarted);
        System.out.println("________________________");

        parseArgs(args);

        Launcher main = new Launcher();
        main.init();

        // Should probably remove this code entirely, it is broken
        if (BankInABoxProperties.COLLECT_LATENCY_STATS || BankInABoxProperties.COLLECT_TPS_STATS) {
            ExecutorService executor = Executors.newCachedThreadPool();
            System.out.println("Launcher initiating PerfMonitor via non-HZ executor service");
            executor.submit(PerfMonitor.getInstance());
        }

        ///////////////////////////////////////
        // Start up the various Jet pipelines
        ///////////////////////////////////////

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
        System.out.println("________________________");
        System.out.println("RUN MODE " + getRunMode());
        System.out.println("________________________");
        // Pump stats to Grafana dashboard even during benchmark -- at least temporarily
        if (true /*getRunMode() == RunMode.Demo*/) {
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
        locationBasedRuleExecutor.setVerbose(main.verbose);
        try {
            Map<Member,CompletableFuture<Exception>> futures = main.distributedES.submitToAllMembers(locationBasedRuleExecutor);
            main.executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (main.verbose)
            System.out.println("Submitted RuleSetExecutor for location rules to distributed executor service (all members)");

        RuleSetExecutor merchantRuleSetExecutor = new RuleSetExecutor(Constants.QUEUE_MERCHANT,
                new MerchantRuleSet(), Constants.MAP_PPFD_RESULTS);
        merchantRuleSetExecutor.setVerbose(main.verbose);
        try {
            Map<Member,CompletableFuture<Exception>> futures = main.distributedES.submitToAllMembers(merchantRuleSetExecutor);
            main.executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (main.verbose)
            System.out.println("Submitted RuleSetExecutor for merchant rules to distributed executor service (all members)");

        // future: add executors for Credit rules, any others

        AggregationExecutor aggregator = new AggregationExecutor();
        aggregator.setVerbose(main.verbose);
        try {
            Map<Member,Future<Exception>> futures = main.distributedES.submitToAllMembers(aggregator);
            main.executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (main.verbose)
            System.out.println("Submitted AggregationExecutor to distributed executor service (all members)");

        long maploaderEagerStart = System.currentTimeMillis();
        log.info("Getting preAuth map [lazy]");
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        log.info("Getting merchant map");
        main.merchantMap = main.hazelcast.getMap(Constants.MAP_MERCHANT);
        log.info("Getting account map");
        main.accountMap = main.hazelcast.getMap(Constants.MAP_ACCOUNT);
        //log.info("Maps initialized");

        // Because this is eventually consistent, it may not work for the tracking I'm
        // attempting to do -- this may get backed out.
        PNCounter completions = main.hazelcast.getPNCounter(Constants.PN_COUNT_LATENCY_ITEMS);
        PNCounter loaded = main.hazelcast.getPNCounter(Constants.PN_COUNT_LOADED_TO_PREAUTH);

        // All processing is triggered from this listener
        preAuthMap.addEntryListener(new PreauthMapListener(main.hazelcast), true);

        if (cloudPlatform != CloudPlatform.None && !MapLoaderSupportedInCloud) {
            /* This is less efficient than the MapLoader implementation but will only
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
        loader.setVerbose(verbose);
        Future<Exception> loaderExc = null;
        try {
            // Might change this to always use local, or at least any time the
            // cluster is physically co-located with the client and (more importantly) database
            if (cloudPlatform == CloudPlatform.None)
                loaderExc = main.distributedES.submit(loader);
            else {
                // have not tested access from HZCE to MariaDB, so load
                // preAuth from a local thread rather than a remote one.
                loader.setHazelcastInstance(main.hazelcast);
                loaderExc = main.localES.submit(loader);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        //log.info("All transactions loaded to preAuth");

        // This has no purpose other than monitoring the backlog during debug
        if (verbose)
            System.out.println("Everything has been started, monitoring executors status");
        int currentPreAuthSize = 0;
        long currentCompletions = 0;
        int lastPreAuthSize = 0;
        long lastCompletions = 0;
        long lastLoaded = 0;
        long currentLoaded = 0;
        int stallCount = 0;
        boolean loaderFinished = false;
        int timesPreAuthBelow20 = 0;
        PNCounter loadedToPreAuth = main.hazelcast.getPNCounter(Constants.PN_COUNT_LOADED_TO_PREAUTH);
        while (true) {
            try {
                Thread.sleep(30000);
                // Look for exceptions
                try {
                    Exception e = loaderExc.get(1, TimeUnit.SECONDS);
                    if (e != null) {
                        log.severe("Error in preAuth loader", e);
                    } else {
                        // If loader exits with no exception we are in benchmark mode and
                        // have loader the requested number of transactions.
                        loaderFinished = true;
                    }

                    for (Future<Exception> fe : main.executorFutures) {
                        if (fe.get(1, TimeUnit.SECONDS) != null) {
                            log.severe("Error in executor", e);
                        }
                    }
                } catch (TimeoutException to) {
                    ; // System.out.println("Launcher finished exception checking after wakeup");
                } catch (HazelcastClientNotActiveException hcne) {
                    if (loaderFinished)
                        ; // have seen this exception (rarely) on a normal shutdown
                    else
                        hcne.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // For visibility to what's going on in cloud - gives us TPS results that
            // otherwise are written to console
            if (main.verbose) {
                IMap<ExecutorStatusMapKey, String> rsExecStatusMap = main.hazelcast.getMap(Constants.MAP_EXECUTOR_STATUS);
                for (ExecutorStatusMapKey key : rsExecStatusMap.keySet()) {
                    String status = rsExecStatusMap.get(key);
                    System.out.printf("[Remote status] %s %s\n", key, status);
                }
            }

            System.out.println("Transaction backlog (preAuth map size) " + preAuthMap.size() + ", total loaded " + loadedToPreAuth.get());
            lastPreAuthSize = currentPreAuthSize;
            lastCompletions = currentCompletions;
            lastLoaded = currentLoaded;
            currentPreAuthSize = preAuthMap.size();
            currentCompletions = completions.get();
            currentLoaded = loaded.get();

            // No longer needed, and only useful in a single-node deployment.
            if (main.verbose) {
                if (runMode == RunMode.Benchmark) {
                    long loadedChange = currentLoaded - lastLoaded;
                    long completionsChange = currentCompletions - lastCompletions;
                    long preAuthChange = currentPreAuthSize - lastPreAuthSize;
                    long expectedPreAuthSize = lastPreAuthSize + loadedChange - completionsChange;
                    if (currentPreAuthSize != expectedPreAuthSize) {
                        System.out.printf("Expected: old size %d + loaded %d - completed %d = new size %d. ", lastPreAuthSize, loadedChange, completionsChange, expectedPreAuthSize);
                        System.out.printf("Actual:  %d, difference: %d\n", currentPreAuthSize, expectedPreAuthSize - currentPreAuthSize);
                    }
                }
            }

            // Normally commented out, can use to debug if something is falling behind
//            IQueue mQueue = main.hazelcast.getQueue(Constants.QUEUE_MERCHANT);
//            IQueue lQueue = main.hazelcast.getQueue(Constants.QUEUE_LOCATION);
//            IQueue aQueue = main.hazelcast.getQueue(Constants.QUEUE_COMPLETIONS);
//            Map ppfdMap = main.hazelcast.getMap(Constants.MAP_PPFD_RESULTS);
//            System.out.println("Merchant Rules queue size " + mQueue.size());
//            System.out.println("Location Rules queue size " + lQueue.size());
//            System.out.println("Intermediate results map size " + ppfdMap.size());
//            System.out.println("Awaiting aggregation queue size " + aQueue.size());
//            long latencyNanos = main.hazelcast.getPNCounter(Constants.PN_COUNT_TOTAL_LATENCY).get();
//            long latencyMillis = latencyNanos / 1_000_000;
//            long approved = main.hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED).get();
//            long rejFraud = main.hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD).get();
//            long rejCredit = main.hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT).get();
//            long completions = approved + rejFraud + rejCredit;
//            System.out.println("Average latency " + latencyMillis / completions + " ms");
            // back to regularly scheduled programming

            if (runMode == RunMode.Benchmark && loaderFinished && currentPreAuthSize == 0) {
                System.out.println("All transactions processed, exiting");
                main.printResults();
                main.hazelcast.shutdown();
                System.exit(0);
            }

            // Odd behavior where preAuth is almost empty but then seems to bounce around
            // between 1 and 20 items, even though loader has finished so count should
            // only be decreasing.   Definitely a bug, need to track it down, but for
            // now just terminate the run.
            if (runMode == RunMode.Benchmark && loaderFinished && currentPreAuthSize < 20) {
                if (timesPreAuthBelow20 >= 5) {
                    System.out.println("Failed to drain preAuth");
                    main.printResults();
                    try {
                        main.hazelcast.shutdown();
                    } catch (HazelcastClientNotActiveException e) {
                        ; // ignore
                    } finally {
                        System.exit(0);
                    }
                } else {
                    timesPreAuthBelow20++;
                }
            }
        }
    }

    private void printResults() {
        System.out.println("________________________");
        Date runFinished = new java.util.Date();
        System.out.println("End: " + runFinished);
        Duration d = Duration.between(runStarted.toInstant(), runFinished.toInstant());
        String elapsed = String.format("%02d:%02d:%02d.%03d", d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart(), d.toMillisPart());
        System.out.println("Elapsed: " + elapsed);
        // Note: No rounding done, should get nanos and increment this if over half a second
        long elapsedWallSeconds = d.getSeconds();
        int elapsedWallNanos = d.getNano();
        if (elapsedWallNanos >= 500_000_000)
            elapsedWallSeconds++;
        long elapsedNanos = hazelcast.getPNCounter(Constants.PN_COUNT_TOTAL_LATENCY).get();
        long elapsedMS = elapsedNanos / 1_000_000;
        //long elapsedSeconds = elapsedMS / 1000;
        //System.out.printf("Total elapsed nanos %d millis %d seconds %d\n", elapsedNanos, elapsedMS, elapsedSeconds);
//        long approved = hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED).get();
//        long rejFraud = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD).get();
//        long rejCredit = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT).get();
//        //long outcomes = approved + rejFraud + rejCredit; // seems to overcount, same txn may match multiple rejection criteria
        long completions = hazelcast.getPNCounter(Constants.PN_COUNT_LATENCY_ITEMS).get();
//        System.out.printf("%d approved + %d rej4fraud + %d rej4credit = %d\n", approved, rejFraud, rejCredit, completions);
        System.out.printf("Ran for %d seconds, completed %d txns, throughput %d TPS\n", elapsedWallSeconds, completions, completions / elapsedWallSeconds);
        System.out.printf("Average latency %d ms\n", elapsedMS / completions);
        System.out.println("________________________");

//        IMap<String, LatencyTracking> latencyMap = hazelcast.getMap(Constants.MAP_LATENCY);
//        System.out.println("==== DETAILED LATENCY BREAKDOWN =====");
//        long valueCount = 0;
//        long waitingForLocationRuleset = 0;
//        long waitingForMerchantRuleset = 0;
//        long locationEPExecution = 0;
//        long merchantEPExecution = 0;
//        long waitingForAggregator = 0;
//        long cleaningMaps = 0;
//        for (LatencyTracking l : latencyMap.values()) {
//            valueCount++;
//            waitingForLocationRuleset += l.timeWaitingForTakeByRuleset(LatencyTracking.LOCATION_RULESET_INDEX);
//            waitingForMerchantRuleset += l.timeWaitingForTakeByRuleset(LatencyTracking.MERCHANT_RULESET_INDEX);
//            locationEPExecution += l.timeWaitingForEntryProcessor(LatencyTracking.LOCATION_RULESET_INDEX);
//            merchantEPExecution += l.timeWaitingForEntryProcessor(LatencyTracking.MERCHANT_RULESET_INDEX);
//            waitingForAggregator += l.timeWaitingOnCompletionQueue();
//            cleaningMaps += l.timeCleaningUp();
//        }
//
//            System.out.println("Queued for Location Ruleset " + waitingForLocationRuleset / valueCount);
//            System.out.println("Queued for Merchant Ruleset " + waitingForMerchantRuleset / valueCount);
//            System.out.println("Executing Location EntryProcessor " + locationEPExecution / valueCount);
//            System.out.println("Executing Merchant EntryProcessor " + merchantEPExecution / valueCount);
//            System.out.println("Queued for aggregator " + waitingForAggregator / valueCount);
//            System.out.println("Cleaning up " + cleaningMaps / valueCount);
    }

    private static void parseArgs(String[] args) {
        // First get system properties
        String cloud = System.getProperty("bib.cloud");
        String runmode = System.getProperty("bib.runmode");
        String jetmode = System.getProperty("bib.jetmode");
        String peered = System.getProperty("bib.peered");
        String verbose = System.getProperty("bib.verbose");

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
            else if (arg.startsWith("bib.verbose"))
                verbose = arg.substring(arg.indexOf("=") + 1);

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
        // Set default based on runmode, then override if specified via arg
        if (runMode == RunMode.Benchmark)
            Launcher.verbose = false;
        else
            Launcher.verbose = true;
        if (verbose != null) {
            if (verbose.equalsIgnoreCase("true"))
                Launcher.verbose = true;
            else if (verbose.equalsIgnoreCase("false"))
                Launcher.verbose = false;
        }

        // Debugging output
        log.info("After parsing system properties and command line arguments:");
        log.info("bib.cloud " + cloudPlatform);
        log.info("bib.runMode " + runMode);
        log.info("bib.peered " + Launcher.peered);
        log.info("bib.jetMode client/server? " + externalJet);
        log.info("bib.grafana " + granfanaHost);
        log.info("bib.verbose " + verbose);

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
