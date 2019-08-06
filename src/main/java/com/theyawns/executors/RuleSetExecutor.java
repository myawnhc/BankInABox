package com.theyawns.executors;

import com.hazelcast.core.*;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.rules.TransactionEvaluationResult;
import com.theyawns.rulesets.RuleSet;
import com.theyawns.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class RuleSetExecutor<T,R> implements Runnable, Serializable, HazelcastInstanceAware,
        MessageListener<T> {

    private HazelcastInstance hazelcast;

    private String preAuthTopic;
    private String transactionsInQueue;
    private String resultsOutMap;
    private String completionQueueName = Constants.QUEUE_COMPLETIONS;

    private ITopic<T> topic;
    private IQueue<T> input;
    private RuleSet<T,R> ruleSet;
    private IMap<String, TransactionEvaluationResult> resultMap;
    private IQueue<String> completedTransactionsQueue;

    //private transient ExecutorService jvmExecutor;

    private long counter = 0;

    /** A RuleSetExecutor is initialized with a queue from which it will read
     * input transactions, and a RuleSet that it will apply to each transaction.
     *
     * MAYBE also an imap to write results to ...
     *
     * @param readFrom
     * @param apply
     */
    public RuleSetExecutor(String readFrom, RuleSet<T,R> apply, String resultMap) {
        System.out.println("RuleSetExecutor.<init>");
        this.transactionsInQueue = readFrom;
        //preAuthTopic = readFrom;
        this.ruleSet = apply;
        resultsOutMap = resultMap;
    }

    @Override
    public void run() {
        //jvmExecutor = Executors.newFixedThreadPool(10);
        long startTime = System.nanoTime();
        while (true) {
            try {
                T t = supplyTransaction();
                // Making supply step async is runaway thread creation :-)
                CompletableFuture.completedFuture(t)
                        .<RuleSetEvaluationResult<T,R>>thenApplyAsync(ruleSet::apply)
                        .thenAcceptAsync(this::consumeResult);

                counter++;
                if ((counter % 10000) == 0) {
                    double seconds = (System.nanoTime() - startTime) / 1_000_000_000;
                    double tps = counter / seconds;
                    System.out.println("RuleSetExecutor has handled " + counter + " transactions in " + seconds + " seconds, rate ~ " + (int) tps + " TPS");
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    // conforms to Supplier<U>
    private T supplyTransaction() {
        try {
            return input.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void consumeResult(RuleSetEvaluationResult<T,R> rser) {
        Transaction txn = (Transaction) rser.getItem();
        TransactionEvaluationResult ter = resultMap.get(txn.getID());
        //System.out.println("RuleSetExecutor sees RSER " + rser);
        if (ter == null) {
            ter = new TransactionEvaluationResult(txn, (RuleSetEvaluationResult<Transaction, ?>) rser);
        } else {
            ter.addResult((RuleSetEvaluationResult<Transaction, ?>) rser);
        }
        resultMap.put(txn.getID(), ter);
        //System.out.println("RuleSetExecutor writes result to map for " + txn.getID());
        if (ter.checkForCompletion()) {
            completedTransactionsQueue.offer(txn.getID());
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        System.out.println("RuleSetExecutor.setHazecastInstance");
        this.hazelcast = hazelcastInstance;
        this.input = hazelcast.getQueue(transactionsInQueue);
        //this.topic = hazelcast.getReliableTopic(preAuthTopic);
        //topic.addMessageListener(this);
        this.resultMap = hazelcast.getMap(resultsOutMap);
        this.completedTransactionsQueue = hazelcast.getQueue(completionQueueName);
    }

    // MessageListener interface for Topic - unused for now
    @Override
    public void onMessage(Message<T> message) {
        long startTime = System.nanoTime();
        System.out.println("RuleSetExecutor.onMessage()");
        T txn = message.getMessageObject();
        CompletableFuture.completedFuture(txn)
                .<RuleSetEvaluationResult<T,R>>thenApplyAsync(ruleSet::apply)
                .thenAcceptAsync(this::consumeResult);

        counter++;
        if ((counter % 10000) == 0) {
            double seconds = (System.nanoTime() - startTime) / 1_000_000_000;
            double tps = counter / seconds;
            System.out.println("RuleSetExecutor has handled " + counter + " transactions in " + seconds + " seconds, rate ~ " + (int) tps + " TPS");
        }
    }
}
