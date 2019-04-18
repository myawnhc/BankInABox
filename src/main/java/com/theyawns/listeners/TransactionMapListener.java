package com.theyawns.listeners;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.theyawns.domain.payments.Transaction;


// Listener is armed by Launcher, instance should be the non-Jet IMDG cluster

/** Listener to the preAuth map to run EntryProcessor for fraud detection */
public class TransactionMapListener implements
        EntryAddedListener<String, Transaction> {

    private HazelcastInstance hazelcast;
    private IMap<String, Transaction> preAuthMap;
//    private IMap<String,Transaction> approved;
//    private IMap<String,Transaction> rejected;
//    private IMap<String, List<RuleExecutionResult>> resultMap;

    public TransactionMapListener(HazelcastInstance instance) {
        hazelcast = instance;
        preAuthMap = preAuthMap = instance.getMap("preAuth");
    }


    @Override
    //public void entryAdded(EntryEvent<String, Transaction> entryEvent) {
    public void entryAdded(EntryEvent entryEvent) {

            System.out.println("TransactionMapListener: Added " + entryEvent);
//        String transactionId = entryEvent.getKey();
//        Transaction txn = entryEvent.getValue();
//        CreditLimitEP processor = new CreditLimitEP();
//        processor.setHazelcastInstance(hazelcast);
//        preAuthMap.executeOnKey(transactionId, new CreditLimitEP());


        // Code below here from the results map, may want to use part of it but it will need rework


//        // TODO: This cast should always work but still should add an instanceof or change RER member variable type
//        TransactionWithRules txn = (TransactionWithRules) resultList.get(0).transaction;
//        int resultsExpected = txn.getExpectedRuleCount();
//        int resultsReceived = resultList.size();
//        if (resultsReceived < resultsExpected) {
//            System.out.println("Intermediate result received");
//        } else {
//            System.out.println("Final result received");
//            preAuthMap.remove(transactionId);
//            // TODO: we really should have an aggregation coming to us, not individual results!
//            // TODO: But since we know there's just one rule alive now, treat as if it's an aggregation
//            boolean txnOK = resultList.get(0).result;
//            if (txnOK)
//                approved.put(transactionId, txn);
//            else
//                rejected.put(transactionId, txn);
        }

    }


//    @Override
//    public void run() {
//        preAuthMap = hazelcast.getMap("preAuth");
//        resultMap = hazelcast.getMap("resultMap");
//        // Intent is for grafana to graph these ...
//        approved = hazelcast.getMap("approved");
//        rejected = hazelcast.getMap("rejected");
//        resultMap.addEntryListener(this, true);
//        //new SqlPredicate("paymentResult != null"), true);
//        System.out.println("EntryListener registered");
//    }
