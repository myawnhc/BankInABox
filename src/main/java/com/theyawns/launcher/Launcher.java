package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.Constants;
import com.theyawns.domain.payments.*;
import com.theyawns.domain.payments.database.LazyPreAuthLoader;
import com.theyawns.executors.AggregationExecutor;
import com.theyawns.executors.RuleSetExecutor;
import com.theyawns.listeners.PreauthMapListener;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.pipelines.AdjustMerchantTransactionAverage;
import com.theyawns.rules.TransactionEvaluationResult;
import com.theyawns.rulesets.LocationBasedRuleSet;
import com.theyawns.rulesets.MerchantRuleSet;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class Launcher {

    protected HazelcastInstance hazelcast;
    protected IExecutorService distributedES;
    private final static ILogger log = Logger.getLogger(Launcher.class);
    private static RunMode runMode = RunMode.Demo;

    // Only here for triggering eager cache load
    private IMap<String, Merchant> merchantMap;
    private IMap<String, Account> accountMap;

    private IMap<String, TransactionEvaluationResult> resultMap;

    protected void init() {
        ClientConfig cc = new XmlClientConfigBuilder().build();
        cc.setInstanceName("Launcher");
        hazelcast = HazelcastClient.newHazelcastClient(cc);
        log.info("Getting distributed executor service");
        distributedES = hazelcast.getExecutorService("executor");

        log.info("init() complete");
//        locationRulesQueue = hazelcast.getQueue(Constants.QUEUE_LOCATION);
//        merchantRulesQueue = hazelcast.getQueue(Constants.QUEUE_MERCHANT);
//        paymentRulesQueue  = hazelcast.getQueue(Constants.QUEUE_CREDITRULES);
//        resultMap = hazelcast.getMap(Constants.MAP_PPFD_RESULTS);
    }

    public static RunMode getRunMode() { return runMode; }
    public static void setRunMode(RunMode mode) { runMode = mode; }

    // Currently not used in this configuration, but might add payment rules back
    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
            // TODO: configure filter to take odd-numbered transactions
        }
    }

    private static class AdjustMerchantAvgTask implements Runnable, Serializable {
        public void run() {
            System.out.println("AdjustMerchangeAvgTask Runnable has been started");
            AdjustMerchantTransactionAverage amta = new AdjustMerchantTransactionAverage();
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
        AdjustMerchantAvgTask merchantAvgTask = new AdjustMerchantAvgTask();
        //main.distributedES.submit(merchantAvgTask);
        // This is a Jet job so doesn't need to run in the IMDG cluster ...
        ExecutorService executor = Executors.newSingleThreadExecutor();
        // TODO:  executor.submit(merchantAvgTask);

        IScheduledExecutorService dses = main.hazelcast.getScheduledExecutorService("scheduledExecutor");
        //ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        if (getRunMode() == RunMode.Demo) {
            PumpGrafanaStats stats = new PumpGrafanaStats();
            try {
                dses.scheduleAtFixedRate(stats, 20, 5, TimeUnit.SECONDS);
            } catch (DuplicateTaskException dte) {
                ; // OK to ignore
            } catch (RejectedExecutionException ree) {
                log.info("PumpGrafanaStats has fallen behind, skipping execution cycle");
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

//        log.info("")
//        try {
//            main.merchantMap.get("1");
//            main.accountMap.get("1"); // invalid key, done just to trigger MapLoader to begin caching the maps
//        } catch (Exception e) {
//            ; // ignore
//        }

        log.info("Waiting for pre-loads (Account and Merchant tables)");
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
                System.exit(0);
            }
        }
    }
}
