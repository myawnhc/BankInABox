package com.theyawns.executors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.launcher.Launcher;
import com.theyawns.launcher.RunMode;
import com.theyawns.rules.RuleCategory;
import com.theyawns.rules.TransactionEvaluationResult;
import com.theyawns.rulesets.RuleSet;
import com.theyawns.rulesets.RuleSetEvaluationResult;
import com.theyawns.rulesets.RuleSets;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AggregationExecutor implements Runnable, Serializable, HazelcastInstanceAware {

    private final static ILogger log = Logger.getLogger(AggregationExecutor.class);

    private HazelcastInstance hazelcast;

    private IQueue<String> completedTransactionIDs;
    private IMap<String, TransactionEvaluationResult> resultMap;
    private IMap<String, Transaction> preAuthMap;
    private IMap<String, TransactionEvaluationResult> approvedMap;
    private IMap<String, TransactionEvaluationResult> rejectedForFraudMap;
    private IMap<String, TransactionEvaluationResult> rejectedForCreditMap;

    // Counters for Grafana dashboard
    private PNCounter approvalCounter;
    private PNCounter rejectedForFraudCounter;
    private PNCounter rejectedForCreditCounter;

    // TODO: may need this to be an IMap ... aggregator can move due to node failure
    private Map<String, PNCounter> rejectedByRuleCounters = new HashMap<>();

    private long counter = 0;

    public AggregationExecutor() {
        System.out.println("AggregationExecutor.<init>");
    }

    @Override
    public void run() {
        log.info("AggregationExecutor.run()");
        long startTime = System.nanoTime();
        while (true) {
            try {
                long startInner = System.nanoTime();
                String txnId = completedTransactionIDs.take();
                double ms = (System.nanoTime() - startInner) / 1_000_000;
                TransactionEvaluationResult ter = resultMap.get(txnId);

                // TODO: may break processResults into more fine-grained steps
                CompletableFuture.completedFuture(ter)
                        .thenApplyAsync(this::processResults)   // update counters and/or maps
                        .thenAcceptAsync(this::cleanupMaps);   // delete txn from preAuth and PPFD Results


                counter++;
                if ((counter % 1000) == 0) {
                    double seconds = (System.nanoTime() - startTime) / 1_000_000_000;
                    double tps = counter / seconds;
                    log.info("AggregationExecutor has handled " + counter + " transactions in " + seconds + " seconds, rate ~ " + (int) tps + " TPS");
                }


            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private void cleanupMaps(String txnId) {
        preAuthMap.delete(txnId);
        resultMap.delete(txnId);
    }

    private String processResults(TransactionEvaluationResult ter) {
        boolean rejected = false;
        List<RuleSetEvaluationResult<Transaction,?>> results = ter.getResults();
        String txnId = ter.getTransaction().getID();

        // Loop over results (even though at this stage we'll only have one)
        // Any reject will break us out of the loop, if we process all without a reject, then we approve.
        for (RuleSetEvaluationResult rser : results) {
            switch (rser.getRuleSetOutcome()) {
                case RejectedForFraud:
                    rejected = true;
                    ter.setRejectingRuleSet(rser.getRuleSetName());
                    ter.setRejectingReason(rser.getOutcomeReason());
                    if (Launcher.getRunMode() == RunMode.Demo) {
                        // Benchmark doesn't care about the dashboard
                        rejectedForFraudCounter.getAndIncrement();
                        incrementRejectCountForRule(rser);
                    }
                    // This map now has eviction to allow long-running demo
                    rejectedForFraudMap.put(txnId, ter);
                    break;
                case RejectedForCredit:
                    rejected = true;
                    ter.setRejectingRuleSet(rser.getRuleSetName());
                    ter.setRejectingReason(rser.getOutcomeReason());
                    if (Launcher.getRunMode() == RunMode.Demo) {
                        // Benchmark doesn't care about the dashboard
                        rejectedForCreditCounter.getAndIncrement();
                        incrementRejectCountForRule(rser);
                    }
                    // This map now has eviction to allow long-running demo
                    rejectedForCreditMap.put(txnId, ter);
                    break;
                case Approved:
                    ;

            }
        }
        if (!rejected) {
            // This map now has eviction to allow long-running demo
            approvedMap.put(txnId, ter);
            if (Launcher.getRunMode() == RunMode.Demo) {
                // Benchmark doesn't care about the dashboard
                approvalCounter.getAndIncrement();
            }
            //System.out.println("Approved " + txnId);

        }
        return txnId;
    }

    private void incrementRejectCountForRule(RuleSetEvaluationResult rser) {
        String rsName = rser.getRuleSetName();
        PNCounter pnc = rejectedByRuleCounters.get(rsName);
        if (pnc == null) {
            pnc = hazelcast.getPNCounter(rsName);
            rejectedByRuleCounters.put(rsName, pnc);
        }
        pnc.getAndIncrement();
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
        this.resultMap = hazelcast.getMap(Constants.MAP_PPFD_RESULTS);
        this.preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        this.approvedMap = hazelcast.getMap(Constants.MAP_APPROVED);
        this.rejectedForFraudMap = hazelcast.getMap(Constants.MAP_REJECTED_FRAUD);
        this.rejectedForCreditMap = hazelcast.getMap(Constants.MAP_REJECTED_CREDIT);
        this.completedTransactionIDs = hazelcast.getQueue(Constants.QUEUE_COMPLETIONS);
        approvalCounter = hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED);
        rejectedForFraudCounter = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD);
        rejectedForCreditCounter = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT);
    }
}