package com.theyawns.domain.payments;

import com.hazelcast.core.IMap;
import com.theyawns.ruleengine.Rule;
import com.theyawns.ruleengine.RuleTask;

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
