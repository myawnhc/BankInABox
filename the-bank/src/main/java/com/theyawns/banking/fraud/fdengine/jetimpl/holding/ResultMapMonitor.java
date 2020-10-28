package com.theyawns.banking.fraud.fdengine.jetimpl.holding;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.controller.launcher.BankInABoxProperties;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;

import java.util.List;

/** Monitors results from Jet processing
 *
 * Not used in mainline Bank in a Box demo
 * Used by DualLauncher / PerfMonitor experimental code
 */
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

    public void common(EntryEvent<String, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        String transactionId = entryEvent.getKey();
        boolean logPerf = BankInABoxProperties.COLLECT_LATENCY_STATS;

        List<RuleEvaluationResult<Transaction, Boolean>> resultList = entryEvent.getValue();
        // TODO: This cast should always work but still should add an instanceof or change RER member variable type
        TransactionWithRules txn = (TransactionWithRules) resultList.get(0).getItem();
        int resultsExpected = txn.getExpectedRuleCount();
        int resultsReceived = resultList.size();
        if (resultsReceived < resultsExpected) {
            System.out.println("Intermediate result received");
        } else {
            //System.out.println("Final result received");
            preAuthMap.remove(transactionId);
            // TODO: we really should have an aggregation coming to us, not individual results!
            // TODO: But since we know there's just one rule alive now, treat as if it's an aggregation
            boolean txnOK = resultList.get(0).getEvaluationResult();
            if (txnOK)
                approved.set(transactionId, txn);
            else
                rejected.set(transactionId, txn);
            //txn.endToEndTime.stop();
        }
    }


    @Override
    public void entryAdded(EntryEvent<String, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        //System.out.println("Added " + entryEvent);
        common(entryEvent);
    }

    @Override
    public void entryUpdated(EntryEvent<String, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        //System.out.println("updated " + entryEvent");
        common(entryEvent);
    }

    @Override
    public void run() {
        preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        resultMap = hazelcast.getMap(Constants.MAP_RESULTS);
        // Intent is for grafana to graph these ...
        approved = hazelcast.getMap(Constants.MAP_APPROVED);
        rejected = hazelcast.getMap(Constants.MAP_REJECTED_CREDIT);
        resultMap.addEntryListener(this, true);
                //new SqlPredicate("paymentResult != null"), true);
        System.out.println("EntryListener registered");
    }
}


