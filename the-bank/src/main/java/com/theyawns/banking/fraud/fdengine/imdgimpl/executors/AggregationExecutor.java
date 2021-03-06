/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.banking.fraud.fdengine.imdgimpl.executors;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionEvaluationResult;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class AggregationExecutor implements Callable<Exception>, Serializable, HazelcastInstanceAware {

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
    private PNCounter totalLatency;
    private PNCounter latencyItems;

    private IMap<ExecutorStatusMapKey,String> statusMap;

    private boolean verbose = true;

    // TODO: may need this to be an IMap ... aggregator can move due to node failure
    private Map<String, PNCounter> rejectedByRuleCounters = new HashMap<>();

    private long counter = 0;

    public AggregationExecutor() {
        System.out.println("AggregationExecutor.<init>");
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    // Normally runs until terminated, only returns in case of an exception
    @Override
    public Exception call() {
        log.info("AggregationExecutor.run()");
        long startTime = System.nanoTime();
        counter = 0;
        long messageCounter = 0;
        String memberId = hazelcast.getCluster().getLocalMember().getUuid().toString().substring(0, 4);
        ExecutorStatusMapKey esmkey = new ExecutorStatusMapKey("AggregationExecutor", memberId);

        while (true) {
            try {
                String txnId = completedTransactionIDs.take();
                TransactionEvaluationResult ter = resultMap.get(txnId);

                // TODO: may break processResults into more fine-grained steps
                CompletableFuture.completedFuture(ter)
                        .thenApplyAsync(this::processResults)   // update counters and/or maps
                        .thenAcceptAsync(this::cleanupMaps);   // delete txn from preAuth and PPFD Results

                // END of PROCESSING - everything else is just reporting back to Launcher

                counter++;
                if (verbose) {
                    if ((counter % 10000) == 0) {
                        Duration d = Duration.ofNanos(System.nanoTime() - startTime);
                        String elapsed = String.format("%02d:%02d:%02d.%03d", d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart(), d.toMillisPart());
                        final double tps = counter / d.toSeconds();

                        long latencyMillis = totalLatency.get();
                        long latencyItemCount = latencyItems.get();
                        String messageID = "[" + messageCounter++ + "]";

                        if (latencyItemCount > 0) {
                            double average = latencyMillis / latencyItems.get();
                            log.info("Average latency is " + average + " ms");
                            log.info("AggregationExecutor has handled " + counter + " transactions in " + elapsed + ", rate ~ " + (int) tps + " TPS, " + average + " ms latency");
                            statusMap.put(esmkey, messageID + " has handled " + counter + " transactions in " + elapsed + ", rate ~ " + (int) tps + " TPS, " + average + " ms latency");
                        } else {
                            statusMap.put(esmkey, messageID + " has handled " + counter + " transactions in " + elapsed + " but latencyItems is zero while millis is " + latencyMillis);
                        }
                    }
                }
            } catch (Exception e) {
                IMap emap = hazelcast.getMap("Exceptions");
                emap.put("AggregationExecutor", e);
                e.printStackTrace();
                return e;
            }
        }
    }

    public static class TxnDeleter implements EntryProcessor<String, Transaction, Void> {
        @Override
        public Void process(Map.Entry<String, Transaction> entry) {
            entry.setValue(null);
            return null;
        }
    }

    public static class TERDeleter implements EntryProcessor<String, TransactionEvaluationResult, Void> {
        @Override
        public Void process(Map.Entry<String, TransactionEvaluationResult> entry) {
            entry.setValue(null);
            return null;
        }
    }

    public static TxnDeleter txnDeleter = new TxnDeleter();
    public static TERDeleter terDeleter = new TERDeleter();

    private void cleanupMaps(String txnId) {
        // In some (CPU limited) environments, the clean up of maps is lagging
        // very far behind ... submitting via EP improves this considerably
        preAuthMap.executeOnKey(txnId, txnDeleter);
        resultMap.executeOnKey(txnId, terDeleter);
//        preAuthMap.delete(txnId);
//        resultMap.delete(txnId);
    }

    private String processResults(TransactionEvaluationResult ter) {
        boolean rejected = false;
        List<RuleSetEvaluationResult<Transaction,?>> results = ter.getResults();
        //String txnId = ter.getTransaction().getItemID();
        String txnId = ter.getCarrier().getItem().getItemID();

        // Loop over results (even though at this stage we'll only have one)
        // Any reject will break us out of the loop, if we process all without a reject, then we approve.
        for (RuleSetEvaluationResult rser : results) {
            switch (rser.getRuleSetOutcome()) {
                case RejectedForFraud:
                    rejected = true;
                    ter.setRejectingRuleSet(rser.getRuleSetName());
                    ter.setRejectingReason(rser.getOutcomeReason());
                    ter.setStopTime();
                    rejectedForFraudCounter.getAndIncrement();
                    incrementRejectCountForRule(rser);
                    // This map now has eviction to allow long-running demo
                    rejectedForFraudMap.set(txnId, ter);
                    break; // no need to check other results
                case RejectedForCredit:
                    rejected = true;
                    ter.setRejectingRuleSet(rser.getRuleSetName());
                    ter.setRejectingReason(rser.getOutcomeReason());
                    ter.setStopTime();
                    rejectedForCreditCounter.getAndIncrement();
                    incrementRejectCountForRule(rser);
                    // This map now has eviction to allow long-running demo
                    rejectedForCreditMap.set(txnId, ter);
                    break; // no need to check other results
                case Approved:
                    // Because we have multiple rulesets, we can't do the
                    // approval logic unless all rulesets have processed and
                    // none reject the transaction - so approval is handled
                    // after we complete the evaluation loop.
                    continue; // check other results
            }
        }
        if (!rejected) {
            // This map now has eviction to allow long-running demo
            ter.setStopTime();
            approvedMap.set(txnId, ter);
            approvalCounter.getAndIncrement();
            //System.out.println("Approved " + txnId);
        }
        //log.info("Transaction " + ter.getTransaction().getItemID() + " completed in " + ter.getLatencyNanos() / 1_000_000 + " ms");

        // Protect against negative values throwing off results;
        // TER will now throw exception if a stop time < start time is set.
        if (ter.getLatencyMillis() >= 0) {
            totalLatency.getAndAdd(ter.getLatencyMillis());
            latencyItems.getAndIncrement();
        } else {
            // No longer happening; did see this when using nanotime as some nodes
            // could have a negative time offset.  Lesson learned - don't use
            // nanotime in a distributed environment!
            System.out.printf("Negative value %d not added to latency\n", ter.getLatencyMillis());
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
        this.resultMap = hazelcast.getMap(Constants.MAP_RESULTS);
        this.preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        this.approvedMap = hazelcast.getMap(Constants.MAP_APPROVED);
        this.rejectedForFraudMap = hazelcast.getMap(Constants.MAP_REJECTED_FRAUD);
        this.rejectedForCreditMap = hazelcast.getMap(Constants.MAP_REJECTED_CREDIT);
        this.completedTransactionIDs = hazelcast.getQueue(Constants.QUEUE_COMPLETIONS);
        approvalCounter = hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED);
        rejectedForFraudCounter = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD);
        rejectedForCreditCounter = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT);
        totalLatency = hazelcast.getPNCounter(Constants.PN_COUNT_TOTAL_LATENCY);
        latencyItems = hazelcast.getPNCounter(Constants.PN_COUNT_LATENCY_ITEMS);
        this.statusMap = this.hazelcast.getMap(Constants.MAP_EXECUTOR_STATUS);
    }
}
