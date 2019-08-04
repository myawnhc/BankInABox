package com.theyawns.executors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.rules.TransactionEvaluationResult;
import com.theyawns.rulesets.RuleSet;
import com.theyawns.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;

// TODO: as currently implemented this might be more accurately named RuleSetExecutor
public class RuleSetExecutor<T,R> implements Runnable, Serializable, HazelcastInstanceAware {

    private HazelcastInstance hazelcast;

    private String transactionsInQueue;
    private String resultsOutMap;
    private String completionQueueName = Constants.QUEUE_COMPLETIONS;

    private IQueue<T> input;
    private RuleSet<T,R> ruleSet;
    private IMap<String, TransactionEvaluationResult> resultMap;
    private IQueue<String> completedTransactionsQueue;

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
        transactionsInQueue = readFrom;
        this.ruleSet = apply;
        resultsOutMap = resultMap;
    }

    @Override
    public void run() {
        long startTime = System.nanoTime();
        System.out.println("RuleSetExecutor.run()");
        while (true) {
            try {
                // TODO future: if RuleSetExecutor is to stay generic, need to make our class signature <T extends HasID>,
                // and similarly use a generic superclass of TransactionEvaluationResult to assign the final result.
                Transaction txn = (Transaction) input.take();
                RuleSetEvaluationResult<R> rser = ruleSet.apply((T)txn);
                TransactionEvaluationResult ter = resultMap.get(txn.getID());
                //System.out.println("RuleSetExecutor sees RSER " + rser);
                if (ter == null) {
                    ter = new TransactionEvaluationResult(txn, rser);
                } else {
                    ter.addResult(rser);
                }
                resultMap.put(txn.getID(), ter);
                //System.out.println("RuleSetExecutor writes result to map for " + txn.getID());
                if (ter.checkForCompletion()) {
                    completedTransactionsQueue.offer(txn.getID());
                }
                counter++;
                if ((counter % 1000) == 0) {
                    double seconds = (System.nanoTime() - startTime) / 1_000_000_000;
                    double tps = counter / seconds;
                    System.out.println("RuleSetExecutor has handled " + counter + " transactions in " + seconds + " seconds, rate ~ " + (int) tps + " TPS");
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        System.out.println("RuleSetExecutor.setHazecastInstance");
        this.hazelcast = hazelcastInstance;
        this.input = hazelcast.getQueue(transactionsInQueue);
        this.resultMap = hazelcast.getMap(resultsOutMap);
        this.completedTransactionsQueue = hazelcast.getQueue(completionQueueName);
    }
}
