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
    public TransactionEvaluationResult() { }

    public synchronized void addResult(RuleSetEvaluationResult<T,?> rser) {
        String key = rser.getRuleSetName();
        Object o = results.put(key, rser);
        // non-null o means we posted a duplicate result
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

    public void setStopTime() {
        stopTime = System.currentTimeMillis();
        // No longer an issue
        if (stopTime < startTime) {
            throw new IllegalArgumentException("StopTime cannot be less than start time" +
                    "Start = " + startTime + " Stop = " + stopTime);
        }
    }

    public long getLatencyMillis() { return stopTime - startTime; }

    public synchronized boolean checkForCompletion() {
        int ruleSetsExpected = carrier.getNumberOfRuleSetsThatApply();
        int ruleSetsCompleted = getNumberOfResultsPosted();
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
