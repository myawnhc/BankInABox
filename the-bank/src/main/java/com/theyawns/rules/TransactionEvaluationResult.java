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

    private long startTime; // TODO
    private long stopTime;  // TODO

    private Transaction transaction;
    private List<RuleSetEvaluationResult<Transaction,?>> results;

    private String rejectingRuleSet;
    private String  rejectingReason;

    public TransactionEvaluationResult(Transaction transaction, RuleSetEvaluationResult<Transaction,?> rser) {
        //System.out.println("TransactionEvaluationResult.<init>");
        this.startTime = System.nanoTime();
        this.transaction = transaction;
        results = new ArrayList<>();
        results.add(rser);
    }

    public void additionalResult(RuleSetEvaluationResult<Transaction,?> rser) {
        results.add(rser);
    }

    public Transaction getTransaction() { return transaction; }

    public List<RuleSetEvaluationResult<Transaction,?>> getResults() {
        return results;
    }

    public void setRejectingRuleSet(String rsName) { rejectingRuleSet = rsName; }
    public void setRejectingReason(String s) { rejectingReason = s; }

    public boolean checkForCompletion() {
        int ruleSetsExpected = transaction.getRuleSetsToApply();
        int ruleSetsCompleted = results.size();
        // we should probably throw an error if we got more results than expected
        //System.out.println("TER.checkForCompletion expects " + ruleSetsExpected + " has " + ruleSetsCompleted);
        return ruleSetsCompleted >= ruleSetsExpected;
    }
}
