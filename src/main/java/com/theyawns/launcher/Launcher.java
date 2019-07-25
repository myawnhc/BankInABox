package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.Constants;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.PumpGrafanaStats;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.listeners.TransactionMapListener;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.pipelines.AdjustMerchantTransactionAverage;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class Launcher {

    protected HazelcastInstance hazelcast;
    //protected ExecutorService distributedES;
    private final static ILogger log = Logger.getLogger(Launcher.class);


    protected void init() {
        hazelcast = HazelcastClient.newHazelcastClient();
        //distributedES = hazelcast.getExecutorService("executor");

    }

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
        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
                true);

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
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(merchantAvgTask);

        IScheduledExecutorService dses = main.hazelcast.getScheduledExecutorService("scheduledExecutor");
        //ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        PumpGrafanaStats stats = new PumpGrafanaStats();
        try {
            dses.scheduleAtFixedRate(stats, 10, 5, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ree) {
            log.info("PumpGrafanaStats has fallen behind, skipping execution cycle");
        }

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
