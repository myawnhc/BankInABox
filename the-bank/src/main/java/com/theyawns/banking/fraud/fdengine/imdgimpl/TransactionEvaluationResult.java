package com.theyawns.banking.fraud.fdengine.imdgimpl;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.*;
import java.util.*;

public class TransactionEvaluationResult implements Serializable, IdentifiedDataSerializable {

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

    // No-arg constructor had to be made public for IDS
    public TransactionEvaluationResult() {

    }

    public synchronized void addResult(RuleSetEvaluationResult<Transaction,?> rser) {
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

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_TXN_EVAL_RESULT;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeLong(startTime);
        objectDataOutput.writeLong(stopTime);
        objectDataOutput.writeObject(transaction);
        objectDataOutput.writeObject(results);
        //Map<String, RuleSetEvaluationResult<Transaction,?>> results;
        objectDataOutput.writeUTF(rejectingRuleSet);
        objectDataOutput.writeUTF(rejectingReason);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        startTime = objectDataInput.readLong();
        stopTime = objectDataInput.readLong();
        transaction = objectDataInput.readObject();
        results = objectDataInput.readObject();
        rejectingRuleSet = objectDataInput.readUTF();
        rejectingReason = objectDataInput.readUTF();
    }
}
