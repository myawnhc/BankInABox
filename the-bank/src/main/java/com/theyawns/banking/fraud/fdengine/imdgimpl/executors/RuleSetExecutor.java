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
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionEvaluationResult;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rulesets.RuleSet;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class RuleSetExecutor<T extends HasID,R> implements Callable<Exception>, Serializable, HazelcastInstanceAware,
        MessageListener<ItemCarrier<T>> {

    private HazelcastInstance hazelcast;

    private String preAuthTopic;
    private String transactionsInQueue;
    private String resultsOutMap;
    private String completionQueueName = Constants.QUEUE_COMPLETIONS;

    private ITopic<T> topic;
    private IQueue<ItemCarrier<T>> input;
    private RuleSet<T,R> ruleSet;
    private IMap<String, TransactionEvaluationResult> resultMap;
    private IQueue<String> completedTransactionsQueue;

    private IMap<ExecutorStatusMapKey,String> statusMap;
    private boolean verbose = true;

    private long operationCounter = 0;

    /** A RuleSetExecutor is initialized with a queue from which it will read
     * input transactions, a RuleSet that it will apply to each transaction, and
     * the map to which results will be written
     *
     * Note that this is called on client side, can't use instance.
     *
     * @param readFrom
     * @param apply
     * @param resultMap
     */
    public RuleSetExecutor(String readFrom, RuleSet<T,R> apply, String resultMap) {
        this.transactionsInQueue = readFrom;
        //preAuthTopic = readFrom;
        ruleSet = apply;
        resultsOutMap = resultMap;
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    // Normally runs until terminated, only returns in case of an exception
    @Override
    public Exception call() {
        if (hazelcast == null) {
            return new IllegalStateException("RuleSetExecutor: HazelcastInstance has not been set");
        }
        long startTime = System.nanoTime();
        operationCounter = 0;
        long messageCounter = 0;

        String memberId = hazelcast.getCluster().getLocalMember().getUuid().toString().substring(0, 4);
        ExecutorStatusMapKey esmkey = new ExecutorStatusMapKey(ruleSet.getName(), memberId);

        while (true) {
            try {
                ItemCarrier<T> t = supplyCarrierItem();

                // Making supply step async is runaway thread creation :-)
                CompletableFuture.completedFuture(t)
                        .thenApplyAsync(this::recordTransactionQueueLatency)
                        .<RuleSetEvaluationResult<T,R>>thenApplyAsync(ruleSet::apply)
                        .thenAcceptAsync(this::consumeResult);

                operationCounter++;
                if (verbose) {
                    if ((operationCounter % 10000) == 0) {
                        Duration d = Duration.ofNanos(System.nanoTime() - startTime);
                        String elapsed = String.format("%02d:%02d:%02d.%03d", d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart(), d.toMillisPart());
                        final double tps = operationCounter / d.toSeconds();
                        System.out.println("RuleSetExecutor " + ruleSet.getName() + " has handled " + operationCounter + " transactions in " + elapsed + ", rate ~ " + (int) tps + " TPS");
                        // Makes visible to cloud clients that don't see console output
                        String messageID = "[" + messageCounter++ + "] ";
                        statusMap.put(esmkey, messageID + "Handled " + operationCounter + " transactions in " + elapsed + ", rate ~ " + (int) tps + " TPS");
                    }
                }

            } catch (Exception e) {
                IMap emap = hazelcast.getMap("Exceptions");
                emap.put("RuleSetExecutor", e);
                e.printStackTrace();
                return e;
                //System.exit(-1);
            }
        }
    }

    // conforms to Supplier<U>
    private ItemCarrier<T> supplyCarrierItem() {
        try {
            return input.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ItemCarrier<T> recordTransactionQueueLatency(ItemCarrier<T> carrier) {
        carrier.setTimeEnqueuedForExecutor();
        return carrier;
    }

    public static class RSEUpdater<T extends HasID> implements EntryProcessor<String, TransactionEvaluationResult, Boolean> {
        //private Transaction txn;
        private ItemCarrier<T> carrier;
        private RuleSetEvaluationResult rser;
        public RSEUpdater(ItemCarrier<T> carrier, RuleSetEvaluationResult rser) {
            this.carrier = carrier;
            this.rser = rser;
        }
        @Override
        public Boolean process(Map.Entry<String, TransactionEvaluationResult> entry) {
            TransactionEvaluationResult result = entry.getValue();
            if (result == null) {
                result = TransactionEvaluationResult.newInstance(carrier);
            }
            result.addResult(rser);
            entry.setValue(result);
            return result.checkForCompletion();
        }
    }

    private void consumeResult(RuleSetEvaluationResult<T,R> rser) {
        ItemCarrier<T> carrier = rser.getCarrier();
        RSEUpdater<T> rseu = new RSEUpdater(carrier, rser);
        String txnID = carrier.getItem().getItemID();
        // Transaction will be complete if all rulesets have posted results
        boolean txnComplete = resultMap.executeOnKey(txnID, rseu);

        if (txnComplete) {
            carrier.setTimeEnqueuedForAggregator();
            // LT: offer to completions queue
            completedTransactionsQueue.offer(txnID);
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
        this.input = hazelcast.getQueue(transactionsInQueue);
        //this.topic = hazelcast.getReliableTopic(preAuthTopic);
        //topic.addMessageListener(this);
        this.resultMap = hazelcast.getMap(resultsOutMap);
        this.completedTransactionsQueue = hazelcast.getQueue(completionQueueName);

        if (ruleSet instanceof HazelcastInstanceAware) {
            ((HazelcastInstanceAware) ruleSet).setHazelcastInstance(hazelcastInstance);
        }

        this.statusMap = this.hazelcast.getMap(Constants.MAP_EXECUTOR_STATUS);
    }

    // MessageListener interface for Topic - unused for now
    @Override
    public void onMessage(Message<ItemCarrier<T>> message) {
        long startTime = System.nanoTime();
        System.out.println("RuleSetExecutor.onMessage()");
        ItemCarrier<T> carrier = message.getMessageObject();
        CompletableFuture.completedFuture(carrier)
                .<RuleSetEvaluationResult<T,R>>thenApplyAsync(ruleSet::apply)
                .thenAcceptAsync(this::consumeResult);

        operationCounter++;
        if ((operationCounter % 10000) == 0) {
            Duration d = Duration.ofNanos(System.nanoTime() - startTime);
            String elapsed = String.format("%02d:%02d:%02d.%03d", d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart(), d.toMillisPart());
            final double tps = operationCounter / d.toSeconds();
            System.out.println("RuleSetExecutor " + ruleSet.getName() + " has handled " + operationCounter + " transactions in " + elapsed + ", rate ~ " + (int) tps + " TPS");
        }
    }
}
