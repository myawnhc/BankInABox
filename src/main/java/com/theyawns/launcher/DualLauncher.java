package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.function.PredicateEx;
import com.theyawns.Constants;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.ResultMapMonitor;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.listeners.TransactionMapListener;
import com.theyawns.perfmon.PerfMonitor;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** This is not the mainstream launcher.
 *
 * This is to run a version of the credit limit run in IMDG and in Jet, feeding each of them half of the
 * transactions, in order to see relative latency and TPS information.  This is not rigorous enough to serve as
 * any sort of a valid benchmark but nevertheless might provide interesting insight.
 */
public class DualLauncher {

    protected HazelcastInstance hazelcast;

    protected void init() {
        hazelcast = HazelcastClient.newHazelcastClient();
    }

    // The Jet implementation of the Credit Limit rule
    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            Thread.currentThread().setName("Jet-CreditLimitRuleTask");
            PredicateEx<Transaction> filter = (PredicateEx<Transaction>) transaction -> isOdd(transaction.getID());

            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.setFilter(filter);
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
        }
    }

    public static boolean isEven(String txnId) {
        int numericID = Integer.parseInt(txnId);
        boolean result =  (numericID % 2) == 0;
        return result;
    }

    public static boolean isOdd(String txnId) {
        return !isEven(txnId);
    }

    public static void main(String[] args) {
        DualLauncher main = new DualLauncher();
        main.init();

        ResultMapMonitor tmon = new ResultMapMonitor(main.hazelcast);
        Thread rmm = new Thread(tmon);
        rmm.setName("ResultMapMonitor");
        rmm.start();

        // This runs the Jet pipeline version of the rule.   Will fail if TxnGenMain not running.
        // Working OK, but disabling for now to focus on debugging the EntryProcessor
        ExecutorService clrexec = Executors.newSingleThreadExecutor();
        clrexec.submit(new CreditLimitRuleTask());

        // This runs the EntryProcessor version of the rule.   Not getting any hits.
        // Are we getting the map from the 'wrong' instance? (Internal vs. external?)
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        System.out.println("initial PreAuth size " + preAuthMap.size());

        // Run entry listener only on even number transactions
        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
                entry -> (isEven(entry.getValue().getID())), true);

        // Start performance monitoring.  Just based on laptop performance 'feel', seems this
        // is fairly intrusive and probably should not be on by default.
        if (BankInABoxProperties.COLLECT_LATENCY_STATS || BankInABoxProperties.COLLECT_TPS_STATS) {
            ExecutorService executor = Executors.newCachedThreadPool();
            System.out.println("DualLauncher initiating PerfMonitor via non-HZ executor service");
            executor.submit(PerfMonitor.getInstance());
        }

        System.out.println("DualLauncher.main finishes");
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
