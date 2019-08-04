package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.Constants;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.PumpGrafanaStats;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.executors.AggregationExecutor;
import com.theyawns.executors.RuleSetExecutor;
import com.theyawns.listeners.PreauthMapListener;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.pipelines.AdjustMerchantTransactionAverage;
import com.theyawns.rules.TransactionEvaluationResult;
import com.theyawns.rulesets.LocationBasedRuleSet;

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
    private static RunMode runMode = RunMode.Benchmark;

//    private IQueue<Transaction> locationRulesQueue;
//    private IQueue<Transaction> merchantRulesQueue;
//    private IQueue<Transaction> paymentRulesQueue;

    private IMap<String, TransactionEvaluationResult> resultMap;

    protected void init() {
        hazelcast = HazelcastClient.newHazelcastClient();
        distributedES = hazelcast.getExecutorService("executor");
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

        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        System.out.println("initial PreAuth size " + preAuthMap.size());

        // Fraud and/or payment rules are invoked by the MapListener
        // TODO: Remove this when new workflow via PreauthMapListener is fully implemented
//        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
//                true);

        preAuthMap.addEntryListener(new PreauthMapListener(main.hazelcast), true);

        if (BankInABoxProperties.COLLECT_LATENCY_STATS || BankInABoxProperties.COLLECT_TPS_STATS) {
            ExecutorService executor = Executors.newCachedThreadPool();
            System.out.println("Launcher initiating PerfMonitor via non-HZ executor service");
            executor.submit(PerfMonitor.getInstance());
        }

        // Start up the various Jet pipelines
        // TODO: not sure there's any advantage to using IMDG executor service here
        // over plain Java
        AdjustMerchantAvgTask merchantAvgTask = new AdjustMerchantAvgTask();
        //main.distributedES.submit(merchantAvgTask);
        // This is a Jet job so doesn't run in the IMDG cluster ...
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(merchantAvgTask);

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
        Set<Member> members = main.hazelcast.getCluster().getMembers();
        main.distributedES.executeOnAllMembers(locationBasedRuleExecutor); // 3 will start
        //main.distributedES.executeOnAllMembers(locationBasedRuleExecutor); // now 6 will start
        //main.distributedES.executeOnAllMembers(locationBasedRuleExecutor); // now 9 will start

        System.out.println("Submitted RuleSetExecutor to distributed executor service");

        // TODO: add executors for Merchant rules, Credit rules

        AggregationExecutor aggregator = new AggregationExecutor();
        main.distributedES.executeOnAllMembers(aggregator);
        System.out.println("Submitted AggregationExecutor to distributed executor service");


        // This has no purpose other than monitoring the backlog during debug
//        while (true) {
//            try {
//                Thread.sleep(30000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            if (preAuthMap.size() > 1)
//                System.out.println("Transaction backlog (preAuth map size) " + preAuthMap.size());  // should be non-zero
//        }
    }
}
