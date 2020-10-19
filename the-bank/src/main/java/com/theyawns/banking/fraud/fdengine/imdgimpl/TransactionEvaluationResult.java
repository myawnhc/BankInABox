package com.theyawns.banking.fraud.fdengine.imdgimpl;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionEvaluationResult<T extends HasID> implements Serializable, IdentifiedDataSerializable {

    private long startTime;
    private long stopTime;

    // DEBUGGING - don't count queued time
    private long terCreationTime;
    // openshift debugging
    private static long duplicateResults = 0;

    private ItemCarrier<T> carrier;
    //private Transaction transaction;
    private Map<String, RuleSetEvaluationResult<T,?>> results;

    private String rejectingRuleSet;
    private String  rejectingReason;

    public static <T extends HasID> TransactionEvaluationResult<T> newInstance(ItemCarrier<T> carrier) {
            TransactionEvaluationResult ter = new TransactionEvaluationResult();
            ter.startTime = carrier.getTimeEnqueuedForRouting();
            ter.carrier = carrier;
            ter.results = new HashMap<>();
            return ter;
    }

    // No-arg constructor had to be made public for IDS
    public TransactionEvaluationResult() {

    }

    public synchronized void addResult(RuleSetEvaluationResult<T,?> rser) {
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

    public ItemCarrier<T> getCarrier() { return carrier; }
    //public Transaction getTransaction() { return transaction; }

    public synchronized List<RuleSetEvaluationResult<T,?>> getResults() {
        List<RuleSetEvaluationResult<T, ?>> a = new ArrayList<>();
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
        int ruleSetsExpected = carrier.getNumberOfRuleSetsThatApply();
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
        objectDataOutput.writeObject(carrier);
        objectDataOutput.writeObject(results);
        //Map<String, RuleSetEvaluationResult<Transaction,?>> results;
        objectDataOutput.writeUTF(rejectingRuleSet);
        objectDataOutput.writeUTF(rejectingReason);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        startTime = objectDataInput.readLong();
        stopTime = objectDataInput.readLong();
        carrier = objectDataInput.readObject();
        results = objectDataInput.readObject();
        rejectingRuleSet = objectDataInput.readUTF();
        rejectingReason = objectDataInput.readUTF();
    }
}
