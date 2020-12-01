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

package com.theyawns.controller.launcher.holding;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.function.PredicateEx;
import com.hazelcast.map.IMap;
import com.theyawns.controller.Constants;
import com.theyawns.banking.payments.rules.CreditLimitRule;
import com.theyawns.banking.fraud.fdengine.jetimpl.holding.ResultMapMonitor;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.holding.TransactionMapListener;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** This is not the mainstream launcher.
 *
 * This is to run a version of the credit limit run in IMDG and in Jet, feeding each of them half of the
 * transactions, in order to see relative latency and TPS information.  This is not rigorous enough to serve as
 * any sort of a valid benchmark but nevertheless might provide interesting insight.
 */
public class DualLauncher {

    protected HazelcastInstance hazelcast;

    protected void init() {
        hazelcast = HazelcastClient.newHazelcastClient();
    }

    // The Jet implementation of the Credit Limit rule
    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            Thread.currentThread().setName("Jet-CreditLimitRuleTask");
            PredicateEx<Transaction> filter = (PredicateEx<Transaction>) transaction -> isOdd(transaction.getItemID());

            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.setFilter(filter);
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
        }
    }

    public static boolean isEven(String txnId) {
        int numericID = Integer.parseInt(txnId);
        boolean result =  (numericID % 2) == 0;
        return result;
    }

    public static boolean isOdd(String txnId) {
        return !isEven(txnId);
    }

    public static void main(String[] args) {
        DualLauncher main = new DualLauncher();
        main.init();

        ResultMapMonitor tmon = new ResultMapMonitor(main.hazelcast);
        Thread rmm = new Thread(tmon);
        rmm.setName("ResultMapMonitor");
        rmm.start();

        // This runs the Jet pipeline version of the rule.   Will fail if TxnGenMain not running.
        // Working OK, but disabling for now to focus on debugging the EntryProcessor
        ExecutorService clrexec = Executors.newSingleThreadExecutor();
        clrexec.submit(new CreditLimitRuleTask());

        // This runs the EntryProcessor version of the rule.   Not getting any hits.
        // Are we getting the map from the 'wrong' instance? (Internal vs. external?)
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        System.out.println("initial PreAuth size " + preAuthMap.size());

        // Run entry listener only on even number transactions
        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
                entry -> (isEven(entry.getValue().getItemID())), true);


        System.out.println("DualLauncher.main finishes");
        // This has no purpose other than monitoring the backlog during debug
//        while (true) {
//            try {
//                Thread.sleep(30000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            if (preAuthMap.size() > 1)
//                System.out.println("Transaction backlog (preAuth map size) " + preAuthMap.size());  // should be non-zero
//        }
    }
}
