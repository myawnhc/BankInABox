package com.theyawns.rules;

import com.theyawns.domain.payments.*;
import com.theyawns.rulesets.*;

import java.io.*;
import java.util.*;

public class TransactionEvaluationResult implements Serializable {

    private long startTime;
    private long stopTime;

    // DEBUGGING - don't count queued time
    private long terCreationTime;

    private Transaction transaction;
    private List<RuleSetEvaluationResult<Transaction,?>> results;

    private String rejectingRuleSet;
    private String  rejectingReason;

    public TransactionEvaluationResult(Transaction transaction, RuleSetEvaluationResult<Transaction,?> rser) {
        //System.out.println("TransactionEvaluationResult.<init>");
        this.startTime = transaction.getTimeEnqueued();
        this.transaction = transaction;
        results = new ArrayList<>();
        results.add(rser);
        // DEBUG
        this.terCreationTime = System.currentTimeMillis();
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

    public void setStopTime(long time) { stopTime = time; }

    public long getLatencyNanos() { return stopTime - startTime; }

    public boolean checkForCompletion() {
        int ruleSetsExpected = transaction.getRuleSetsToApply();
        int ruleSetsCompleted = results.size();
        debug();
        // we should probably throw an error if we got more results than expected
        //System.out.println("TER.checkForCompletion expects " + ruleSetsExpected + " has " + ruleSetsCompleted);
        return ruleSetsCompleted >= ruleSetsExpected;
    }

    private void debug() {
        long timeWaiting = System.currentTimeMillis() - this.terCreationTime;
        // If > 1 minute, we have a problem
        long secondsWaiting = timeWaiting / 1_000;
        if (timeWaiting > 60) {
            String txnId = transaction.getItemID();
            String haveResultFor = results.get(0).getRuleSetName();
            System.out.printf("Stall: Txn %s has result for %s, been waiting for %d seconds\n",
                    txnId, haveResultFor, timeWaiting);

        }
    }
}
