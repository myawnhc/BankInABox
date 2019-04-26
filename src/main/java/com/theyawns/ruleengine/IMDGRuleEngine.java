package com.theyawns.ruleengine;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Deprecated
public class IMDGRuleEngine<T extends HasID> extends BaseRuleEngine<T> {

    // private Transaction Queue - in BaseRuleEngine
    // private Rule Task Q
    //private HazelcastInstance hazelcast;
    private IQueue<RuleTask> ruleTaskQueue;
    private IMap<String, Integer> pendingTransactionsMap;
    private IMap<String, List<RuleEvaluationResult>> ruleResults;

    private int transactionsProcessed = 0;

    public IMDGRuleEngine(HazelcastInstance instance) {
       super(instance);
        ruleTaskQueue = instance.getQueue("ruleTaskQ");
        pendingTransactionsMap = instance.getMap("pendingTxnsMap");
        ruleResults = instance.getMap("ruleResults");
    }
    // private Task Results Map
    //     one entry per task, or one entry per transaction with list ?

    // Task Thread - configurable # of instances
    // - Read transactions from Transaction Queue
    // - Create a RuleTask for each Rule in RuleSet, add to RuleTask Q
    // - Create entry in Pending Transactions map showing # of rules pending

    // Worker Thread - configurable # of instances
    // - Pull Task from RuleTask Q, execute, write results to Task Results Map
    //       and update Pending transactions map
    //       with new count

    // PendingTransactions listener
    // Listen to pending rults map
    // Trigger when pending count goes to zero
    //    Accumulate results
    //    Return value via Future

    // Take from TxnQ, put individual tasks into RuleTaskQ; create Pending Txns map entry
    private Runnable createRuleTasks = () -> {
        long start = System.currentTimeMillis();
        while (true) {    // TODO: figure a better run control stategy that a counter
            T t = getNextInput();
            if (t == null) {
                System.out.println("All transactions queued for evaluation");
                break;
            }
            if (t.getID().endsWith("0000")) {
                System.out.println("createRuleTasks handling item " + t.getID());
            }
// TODO: requires rework to new multiple ruleset design
//            int pendingCount = ruleSet.size();
//            pendingTransactionsMap.put(t.getRuleId(), pendingCount);
//            for (Rule rule : ruleSet) {
//                RuleTask task = new RuleTask(rule, t);
//                ruleTaskQueue.add(task);
//            }
            transactionsProcessed++;
            //System.out.println("Created tasks for " + t.getTransactionId() + " taskQ size " + ruleTaskQueue.size());
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(transactionsProcessed + " txns queued for rule processing in " + elapsed + "ms");
    };

    public void startRuleTaskCreation() {
        System.out.println("startRuleTaskCreation");
        Thread t = new Thread(createRuleTasks);
        t.start();
    }

    // Execute a task off RuleTaskQ, write intermediate results and update pending txns
    private Runnable ruleTaskWorker = () -> {
        long start = System.currentTimeMillis();
        int processedCount = 0;
        while (true) {
            try {
                RuleTask task = ruleTaskQueue.poll(10, TimeUnit.SECONDS);
                if (task == null) {
                    System.out.println("Null task - timeout?");
                    break;
                }
// these would need to be restored to use task with IMDG
//                task.setPendingTransactionsMap(pendingTransactionsMap);
//                task.setRuleResultsMap(ruleResults);
                task.run();
                processedCount++;

            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }

            if (processedCount % 10000 == 0) {
                System.out.println("Processed " + processedCount + " rules, queue size " + ruleTaskQueue.size());
            }

        }
        // TODO: must init both maps before running the task!

    };

    public void startRuleEvaluation() {
        System.out.println("startRuleEvaluation");
        Thread t = new Thread(ruleTaskWorker);
        t.start();
    }

//    @Deprecated
//    public RuleEvaluationResult process(Transaction transaction) {
//        // TODO: apply all rules to the transaction
//
//        // 1 thread per rule seems overkill, but 1 thread all rules doesn't scale.
//        // What's the best way to scale here?  Some scatter/gather process?
//        return null;  // TODO:
//    }

    static class AddResultEntryProcessor implements EntryProcessor<String, List<RuleEvaluationResult>> {
        RuleEvaluationResult newRuleEvaluationResult;

        public AddResultEntryProcessor(RuleEvaluationResult newRuleEvaluationResult) {
            this.newRuleEvaluationResult = newRuleEvaluationResult;
        }

        @Override
        public Object process(Map.Entry<String, List<RuleEvaluationResult>> entry) {
            List<RuleEvaluationResult> ruleEvaluationResults = entry.getValue();
            if (ruleEvaluationResults == null)
                ruleEvaluationResults = new ArrayList<>();
            ruleEvaluationResults.add(newRuleEvaluationResult);
            entry.setValue(ruleEvaluationResults);
            return null;
        }

        @Override
        public EntryBackupProcessor<String, List<RuleEvaluationResult>> getBackupProcessor() {
            return null;
        }
    }

    static class DecrementValueEntryProcessor implements EntryProcessor<String, Integer> {

        @Override
        public Object process(Map.Entry<String, Integer> entry) {
            int value = entry.getValue();
            entry.setValue(--value);
            return value;
        }

        @Override
        public EntryBackupProcessor<String, Integer> getBackupProcessor() {
            return null;
        }
    }
    static DecrementValueEntryProcessor decrementingEP = new DecrementValueEntryProcessor();


}
