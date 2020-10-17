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
    private Map<String, RuleSetEvaluationResult<Transaction,?>> results;

    private String rejectingRuleSet;
    private String  rejectingReason;

    public static TransactionEvaluationResult newInstance(Transaction txn) {
            TransactionEvaluationResult ter = new TransactionEvaluationResult();
            ter.startTime = txn.getTimeEnqueuedForRuleEngine();
            ter.terCreationTime = System.currentTimeMillis(); // not used currently
            // DEBUG
            long millisSinceEnqueued = (System.currentTimeMillis() - ter.startTime);
            if (millisSinceEnqueued > 20_000)  {
                System.out.println("TER created > 20 seconds after txn queued " + millisSinceEnqueued);
            }
            // ~DEBUG
            ter.transaction = txn;
            ter.results = new HashMap<>();
            return ter;
    }

    private TransactionEvaluationResult() { }

    public synchronized void addResult(RuleSetEvaluationResult<Transaction,?> rser) {
        // Previously a list, map helps avoid duplicate results from same set -- only
        // seen in a rare split-brain merge scenario
        String key = rser.getRuleSetName();
        results.put(key, rser);
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

    public void setStopTime(long millis) {
        stopTime = millis;

        // hopefully briefly here for debugging.  Never seen, can remove
        if (millis < startTime) {
            throw new IllegalArgumentException("StopTime cannot be less than start time" +
                    "Start = " + startTime + " Stop = " + stopTime);
        }
        long millisElapsed = (stopTime - startTime);
        if (millisElapsed > 20_000)  {
            System.out.println("TER stop > 20 seconds after TER start " + millisElapsed);
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
