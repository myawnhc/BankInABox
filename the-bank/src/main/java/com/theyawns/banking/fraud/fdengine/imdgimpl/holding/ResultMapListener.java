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
