package com.theyawns.domain.payments;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.theyawns.Constants;
import com.theyawns.ruleengine.RuleEvaluationResult;

import java.util.List;

/** Monitors results from Jet processing
 *
 * Not used in mainline Bank in a Box demo
 * Used by DualLauncher / PerfMonitor experimental code
 */
@Deprecated
public class ResultMapMonitor implements Runnable,
        EntryAddedListener<TransactionKey, List<RuleEvaluationResult<Transaction, Boolean>>>,
        EntryUpdatedListener<TransactionKey, List<RuleEvaluationResult<Transaction, Boolean>>> {
        //EntryRemovedListener<String, Transaction> {

    private HazelcastInstance hazelcast;
    private IMap<TransactionKey,Transaction> preAuthMap;
    private IMap<TransactionKey,Transaction> approved;
    private IMap<TransactionKey,Transaction> rejected;
    private IMap<TransactionKey,List<RuleEvaluationResult<Transaction, Boolean>>> resultMap;

    public ResultMapMonitor(HazelcastInstance instance) {
        hazelcast = instance;
    }

    public void common(EntryEvent<TransactionKey, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        //String transactionId = entryEvent.getKey();
        TransactionKey key = entryEvent.getKey();

        List<RuleEvaluationResult<Transaction, Boolean>> resultList = entryEvent.getValue();
        // TODO: This cast should always work but still should add an instanceof or change RER member variable type
        TransactionWithRules txn = (TransactionWithRules) resultList.get(0).getItem();
        int resultsExpected = txn.getExpectedRuleCount();
        int resultsReceived = resultList.size();
        if (resultsReceived < resultsExpected) {
            System.out.println("Intermediate result received");
        } else {
            //System.out.println("Final result received");
            preAuthMap.remove(key);
            // TODO: we really should have an aggregation coming to us, not individual results!
            // TODO: But since we know there's just one rule alive now, treat as if it's an aggregation
            boolean txnOK = resultList.get(0).getEvaluationResult();
            if (txnOK)
                approved.set(key, txn);
            else
                rejected.set(key, txn);
        }
    }


    @Override
    public void entryAdded(EntryEvent<TransactionKey, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        //System.out.println("Added " + entryEvent);
        common(entryEvent);
    }

    @Override
    public void entryUpdated(EntryEvent<TransactionKey, List<RuleEvaluationResult<Transaction, Boolean>>> entryEvent) {
        //System.out.println("updated " + entryEvent");
        common(entryEvent);
    }

    @Override
    public void run() {
        preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        resultMap = hazelcast.getMap(Constants.MAP_RESULT);
        // Intent is for grafana to graph these ...
        approved = hazelcast.getMap(Constants.MAP_APPROVED);
        rejected = hazelcast.getMap(Constants.MAP_REJECTED_CREDIT);
        resultMap.addEntryListener(this, true);
                //new SqlPredicate("paymentResult != null"), true);
        System.out.println("EntryListener registered");
    }
}


