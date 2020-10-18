package com.theyawns.banking.fraud.fdengine.imdgimpl;

import com.theyawns.banking.Transaction;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.*;
import java.util.*;

public class TransactionEvaluationResult implements Serializable {

    private long startTime;
    private long stopTime;

    // DEBUGGING - don't count queued time
    private long terCreationTime;
    // openshift debugging
    private static long duplicateResults = 0;

    private Transaction transaction;
    private Map<String, RuleSetEvaluationResult<Transaction,?>> results;

    private String rejectingRuleSet;
    private String  rejectingReason;

    public static TransactionEvaluationResult newInstance(Transaction txn) {
            TransactionEvaluationResult ter = new TransactionEvaluationResult();
            ter.startTime = txn.getTimeEnqueuedForRuleEngine();
            ter.transaction = txn;
            ter.results = new HashMap<>();
            return ter;
    }

    // private no-arg constructor
    private TransactionEvaluationResult() {

    }

    public synchronized void addResult(RuleSetEvaluationResult<Transaction,?> rser) {
        // maybe results should be keyed by ruleset in case we somehow get multiple
        // results for same set?  Seems fine everywhere but OpenShift where we get
        // more completions than we have transactions - a logical impossibility
        String key = rser.getRuleSetName();
        Object o = results.put(key, rser);
        // This apparently happens only on OpenShift, and possibly only after
        // we start to be throttled by some as-yet-unidentified mechanism.
        if (o != null) {
            duplicateResults++;
            if (duplicateResults % 1000 == 0) {
                System.out.printf("TER has seen %d attempts to post duplicate results\n", duplicateResults);
            }
        }
    }

    public Transaction getTransaction() { return transaction; }

    public synchronized List<RuleSetEvaluationResult<Transaction,?>> getResults() {
        List<RuleSetEvaluationResult<Transaction, ?>> a = new ArrayList<>();
        a.addAll(results.values());
        return a;
    }

    private synchronized int getNumberOfResultsPosted() {
        return results.size();
    }

    public void setRejectingRuleSet(String rsName) { rejectingRuleSet = rsName; }
    public void setRejectingReason(String s) { rejectingReason = s; }

    public void setStopTime(long time) {
        stopTime = time;

        // hopefully briefly here for debugging
        if (time < startTime) {
            throw new IllegalArgumentException("StopTime cannot be less than start time" +
                    "Start = " + startTime + " Stop = " + stopTime);
        }

    }

    public long getLatencyMillis() { return stopTime - startTime; }

    public synchronized boolean checkForCompletion() {
        int ruleSetsExpected = transaction.getNumberOfRuleSetsThatApply();
        int ruleSetsCompleted = getNumberOfResultsPosted();
        //debug();
        return ruleSetsCompleted >= ruleSetsExpected;
    }

    // Keep if needed in the future; stalls called by thread safety issue where more than
    // one ruleset could create the TransactionEvaluationResult entry, and the completion condition
    // of all rulesets having posted results was never satisfied.
    private void debug() {
        long timeWaiting = System.currentTimeMillis() - this.terCreationTime;
        // If > 1 minute, we have a problem
        long secondsWaiting = timeWaiting / 1_000;
        if (secondsWaiting > 60) {
            String txnId = transaction.getItemID();
            String haveResultFor = results.get(0).getRuleSetName();
            System.out.printf("Stall: Txn %s has result for %s, been waiting for %d seconds\n",
                    txnId, haveResultFor, secondsWaiting);
        }
    }
}