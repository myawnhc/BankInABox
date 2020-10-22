package com.theyawns.banking.fraud.fdengine.imdgimpl.holding;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.listener.EntryAddedListener;
import com.theyawns.controller.Constants;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionEvaluationResult;

@Deprecated // Nope, don't think this is the way to go with this....
public class ResultMapListener implements
        EntryAddedListener<String, TransactionEvaluationResult> {

    private final static ILogger log = Logger.getLogger(ResultMapListener.class);

    //private HazelcastInstance hazelcast;
    private IMap<String, TransactionEvaluationResult> resultMap;




    public ResultMapListener(HazelcastInstance instance) {
        //hazelcast = instance;
        resultMap = instance.getMap(Constants.MAP_RESULTS);
    }


    @Override
    public void entryAdded(EntryEvent<String, TransactionEvaluationResult> entryEvent) {

        TransactionEvaluationResult ter = entryEvent.getValue();
//        Transaction txn =
//        //System.out.println("RuleSetExecutor sees RSER " + rser);
//        if (ter == null) {
//            ter = new TransactionEvaluationResult(txn, rser);
//        } else {
//            ter.addResult(rser);
//        }
//        resultMap.put(txn.getID(), ter);
//        //System.out.println("RuleSetExecutor writes result to map for " + txn.getID());
//        if (ter.checkForCompletion()) {
//            completedTransactionsQueue.offer(txn.getID());
//        }

    }
}
