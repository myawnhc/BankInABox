package com.theyawns.rules;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.domain.payments.TransactionFinalStatus;
import com.theyawns.rulesets.RuleSet;
import com.theyawns.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TransactionEvaluationResult implements Serializable {

    private long startTime;
    private long stopTime;

    private Transaction transaction;
    private List<RuleSetEvaluationResult<Transaction,?>> results;

    private String rejectingRuleSet;
    private String  rejectingReason;

    public TransactionEvaluationResult(Transaction transaction, RuleSetEvaluationResult<Transaction,?> rser) {
        //System.out.println("TransactionEvaluationResult.<init>");
        this.startTime = System.nanoTime();
        this.transaction = transaction;
        results = new ArrayList<>();
        addResult(rser);
    }

    public void addResult(RuleSetEvaluationResult<Transaction,?> rser) {
        results.add(rser);
        //checkForCompletion();
    }

    public Transaction getTransaction() { return transaction; }

    public List<RuleSetEvaluationResult<Transaction,?>> getResults() {
        return results;
    }

    public void setRejectingRuleSet(String rsName) { rejectingRuleSet = rsName; }
    public void setRejectingReason(String s) { rejectingReason = s; }

    public boolean checkForCompletion() {
        // is the number of rulesets going to be treated as hard coded?
        // TODO: since only location ruleset initially active, our first result is also our last, for now
        boolean complete = true;
        return complete;
    }
}
