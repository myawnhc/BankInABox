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

package com.theyawns.ruleengine.rules;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.controller.Constants;

import java.io.IOException;
import java.io.Serializable;

public class RuleEvaluationResult<R> implements IdentifiedDataSerializable, Serializable {

//    private long startTime;
//    private long stopTime;

    private R result;

    // Not sure this needs to be here ... not being used currently
    private transient Rule rule;

    public RuleEvaluationResult(Rule rule) {
        this.rule = rule;
//        startTime = System.nanoTime();
    }

    // for IDS Serialization
    public RuleEvaluationResult() {}

    public void setResult(R result) {
        this.result = result;
//        this.stopTime = System.nanoTime();
    }

    public R getResult() {
        return result;
    }

//    public long getElapsedNanos() {
//        return stopTime - startTime;
//    }

    public String toString() {
        return result.toString();
    }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_RULE_EVAL_RESULT;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
//        objectDataOutput.writeLong(startTime);
//        objectDataOutput.writeLong(stopTime);
        objectDataOutput.writeObject(result);
//        objectDataOutput.writeObject(rule);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
//        startTime = objectDataInput.readLong();
//        stopTime = objectDataInput.readLong();
        result = objectDataInput.readObject();
//        rule = objectDataInput.readObject();
    }
}
