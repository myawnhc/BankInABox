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

package com.theyawns.banking.fraud.fdengine.jetimpl.holding;

import com.hazelcast.map.IMap;
import com.theyawns.banking.Account;
import com.theyawns.banking.Transaction;
import com.theyawns.ruleengine.jetimpl.rules.Rule;
import com.theyawns.ruleengine.holding.RuleTask;

public class RuleTaskWithAccountInfo<R> extends RuleTask<Transaction> {

    // Need better abstraction for domain objects that enrich the stream -- could be zero or many
    protected Account enrichedAccount;
    //protected String accountNumber;

    public RuleTaskWithAccountInfo(Rule rule, String ruleSetID, Transaction item) {
        super(rule, ruleSetID, item);
    }

    // For prototyping, putting this in RuleTask.  In finished version, any task requiring enriched data would
    // need to subtask RuleTask, provide the enrichment-accepting method like this one, and also pass info
    // to the rule engine about the enrichment source (exact mechanism not yet designed)
    // In the finished version:
    // - name would be generalized, probably setEnrichmentInfo
    // - type would be parameterized
    public RuleTask<Transaction> setAccountInfo(IMap<String, Account> accountMap, RuleTask<Transaction> task) {
        Transaction txn = task.getStreamItem();
        enrichedAccount = accountMap.get(txn.getAccountNumber());
        //System.out.println("SetAccountInfo " + txn.getID() + " " + txn.getAccountNumber() + " " + enrichedAccount);
        return this;
    }

    public Account getAccountInfo() {
        return enrichedAccount;
    }

}
