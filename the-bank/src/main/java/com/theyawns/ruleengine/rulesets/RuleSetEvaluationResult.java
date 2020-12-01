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

package com.theyawns.ruleengine.rulesets;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionFinalStatus;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;

import java.io.IOException;
import java.io.Serializable;

public class RuleSetEvaluationResult<T extends HasID,R> implements IdentifiedDataSerializable, Serializable {

    private long startTime;
    private long stopTime;

    private ItemCarrier<T> carrier;
    private R result;
    private TransactionFinalStatus ruleSetOutcome;
    private String reason;

    //private transient RuleSet ruleSet;
    private String ruleSetName;

    public RuleSetEvaluationResult(ItemCarrier<T> carrier, String ruleSetName) {
        this.carrier = carrier;
        //this.ruleSet = ruleSet;
        this.ruleSetName = ruleSetName;
        startTime = System.nanoTime();
    }

    // for IDS Serialization
    public RuleSetEvaluationResult() {}

    public String getRuleSetName() {
        return ruleSetName;
    }

    public void setResult(R result) {
        this.result = result;
        this.stopTime = System.nanoTime();
    }

    public R getResult() {
        return result;
    }

    public ItemCarrier<T> getCarrier() { return carrier; }

    // Can restore if needed but suspect carrier will always be preferred
    //public T getItem() { return carrier.getItem(); }

    public void setRuleSetOutcome(TransactionFinalStatus passFail) {
        setRuleSetOutcome(passFail, null);
    }

    public void setRuleSetOutcome(TransactionFinalStatus passFail, String reason) {
        this.ruleSetOutcome = passFail;
        this.reason = (reason == null) ? "No explanation" : reason;
    }

    public TransactionFinalStatus getRuleSetOutcome() {
        return ruleSetOutcome;
    }
    public String getOutcomeReason() { return reason; }

    public long getElapsedNanos() {
        return stopTime - startTime;
    }

    public String toString() {
        return result.toString();
    }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_RULESET_EVAL_RESULT;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeLong(startTime);
        objectDataOutput.writeLong(stopTime);
        objectDataOutput.writeObject(carrier);
        objectDataOutput.writeObject(result);
        objectDataOutput.writeObject(ruleSetOutcome);
        objectDataOutput.writeUTF(reason);
        objectDataOutput.writeUTF(ruleSetName);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        startTime = objectDataInput.readLong();
        stopTime = objectDataInput.readLong();
        carrier = objectDataInput.readObject();
        result = objectDataInput.readObject();
        ruleSetOutcome = objectDataInput.readObject();
        reason = objectDataInput.readUTF();
        ruleSetName = objectDataInput.readUTF();
    }
}