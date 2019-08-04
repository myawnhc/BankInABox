package com.theyawns.executors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.crdt.pncounter.PNCounter;
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

public class AggregationExecutor implements Runnable, Serializable, HazelcastInstanceAware {

    private HazelcastInstance hazelcast;

//    private String queueName;
//    private String mapName;

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
    //private PNCounter[] rejectedByRuleCounters;

    // TODO: may need this to be an IMap ... aggregator can move due to node failure
    private Map<String, PNCounter> rejectedByRuleCounters = new HashMap<>();

    private long counter = 0;

    public AggregationExecutor() {
        System.out.println("AggregationExecutor.<init>");
    }

    @Override
    public void run() {
        System.out.println("AggregationExecutor.run()");
        long startTime = System.nanoTime();
        while (true) {
            try {
                String txnId = completedTransactionIDs.take();
                TransactionEvaluationResult ter = resultMap.get(txnId);
                // TODO: verify non null
                //System.out.println("AggregationExecutor processing " + ter);

                boolean rejected = false;
                List<RuleSetEvaluationResult<?>> results = ter.getResults();

                // Loop over results (even though at this stage we'll only have one)
                // Any reject will break us out of the loop, if we process all without a reject, then we approve.
                for (RuleSetEvaluationResult rser : results) {
                    switch (rser.getRuleSetOutcome()) {
                        case RejectedForFraud:
                            rejected = true;
                            if (Launcher.getRunMode() == RunMode.Benchmark) {
                                // Keeping in long-running demo will lead to OOME
                                rejectedForFraudMap.put(txnId, ter);
                            } else {
                                // Keeping in long-running demo will lead to OOME
                                rejectedForFraudCounter.getAndIncrement();
                                incrementRejectCountForRule(rser);
                            }
                            break;
                        case RejectedForCredit:
                            rejected = true;
                            if (Launcher.getRunMode() == RunMode.Benchmark) {
                                rejectedForCreditMap.put(txnId, ter);
                            } else {
                                rejectedForCreditCounter.getAndIncrement();
                                incrementRejectCountForRule(rser);
                            }
                            break;
                        case Approved:
                            ; // do nothing

                    }
                }
                if (!rejected) {
                    if (Launcher.getRunMode() == RunMode.Benchmark) {
                        // Keeping in long-running demo will lead to OOME
                        approvedMap.put(txnId, ter);
                    } else {
                        approvalCounter.getAndIncrement();
                    }
                    //System.out.println("Approved " + txnId);

                }

                preAuthMap.delete(txnId);
                counter++;
                if ((counter % 1000) == 0) {
                    double seconds = (System.nanoTime() - startTime) / 1_000_000_000;
                    double tps = counter / seconds;
                    System.out.println("AggregationExecutor has handled " + counter + " transactions in " + seconds + " seconds, rate ~ " + (int) tps + " TPS");
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private void incrementRejectCountForRule(RuleSetEvaluationResult rser) {
        String rsName = rser.getRuleSet().getQualifiedName();
        PNCounter pnc = rejectedByRuleCounters.get(rsName);
        if (pnc == null) {
            pnc = hazelcast.getPNCounter(rsName);
            rejectedByRuleCounters.put(rsName, pnc);
        }
        pnc.getAndIncrement();
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        System.out.println("AggregatonExecutor.setHazecastInstance " + hazelcastInstance);
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
