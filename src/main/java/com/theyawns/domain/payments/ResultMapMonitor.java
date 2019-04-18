package com.theyawns.domain.payments;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.theyawns.ruleengine.RuleEvaluationResult;

import java.util.List;

public class ResultMapMonitor implements Runnable,
        EntryAddedListener<String, List<RuleEvaluationResult<Transaction, Boolean>>>,
        EntryUpdatedListener<String, List<RuleEvaluationResult<Transaction, Boolean>>> {
        //EntryRemovedListener<String, Transaction> {

    private HazelcastInstance hazelcast;
    private IMap<String,Transaction> preAuthMap;
    private IMap<String,Transaction> approved;
    private IMap<String,Transaction> rejected;
    private IMap<String,List<RuleEvaluationResult<Transaction, Boolean>>> resultMap;

    public ResultMapMonitor(HazelcastInstance instance) {
        hazelcast = instance;
    }


    @Override
    public void entryAdded(EntryEvent<String, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        //System.out.println("Added " + entryEvent);
        String transactionId = entryEvent.getKey();
        List<RuleEvaluationResult<Transaction, Boolean>> resultList = entryEvent.getValue();
        // TODO: This cast should always work but still should add an instanceof or change RER member variable type
        TransactionWithRules txn = (TransactionWithRules) resultList.get(0).getItem();
        int resultsExpected = txn.getExpectedRuleCount();
        int resultsReceived = resultList.size();
        if (resultsReceived < resultsExpected) {
            System.out.println("Intermediate result received");
        } else {
            System.out.println("Final result received");
            preAuthMap.remove(transactionId);
            // TODO: we really should have an aggregation coming to us, not individual results!
            // TODO: But since we know there's just one rule alive now, treat as if it's an aggregation
            boolean txnOK = resultList.get(0).getEvaluationResult();
            if (txnOK)
                approved.put(transactionId, txn);
            else
                rejected.put(transactionId, txn);
        }

    }
//
//    @Override
//    public void entryRemoved(EntryEvent<String, Transaction> entryEvent) {
//        System.out.println("Removed " + entryEvent);
//
//    }

    @Override
    public void entryUpdated(EntryEvent<String, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        System.out.println("updated " + entryEvent);
        // TODO: figure out why 'original' has the new value, and 'update' is null.
//        String key = entryEvent.getKey();
//        Transaction original = entryEvent.getOldValue();
//        Transaction update = entryEvent.getMergingValue();
//        boolean passed = original.getPaymentResult();
//        preAuthMap.remove(key);
//        System.out.println("Updated " + key + " payment " + (passed ? "approved" : "rejected") + " pending count " + preAuthMap.size());
//        //System.out.println("original " + original + " result " + original.getPaymentResult());
//        if (passed)
//            approved.set(key, original);
//        else
//            rejected.set(key, original);
//
//        if (update != null) {
//            System.out.println("update " + update + " result " + update.getPaymentResult());
//            preAuthMap.remove(update.getID());
//            System.out.println("Updated, pending size " + preAuthMap.size());
//        }

    }

    @Override
    public void run() {
        preAuthMap = hazelcast.getMap("preAuth");
        resultMap = hazelcast.getMap("resultMap");
        // Intent is for grafana to graph these ...
        approved = hazelcast.getMap("approved");
        rejected = hazelcast.getMap("rejected");
        resultMap.addEntryListener(this, true);
                //new SqlPredicate("paymentResult != null"), true);
        System.out.println("EntryListener registered");
    }
}


