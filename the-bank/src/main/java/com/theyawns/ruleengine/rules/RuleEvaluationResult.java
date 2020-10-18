package com.theyawns.ruleengine.rules;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.controller.Constants;

import java.io.IOException;
import java.io.Serializable;

public class RuleEvaluationResult<R> implements IdentifiedDataSerializable, Serializable {

    private long startTime;
    private long stopTime;

    private R result;

    private Rule rule;

    public RuleEvaluationResult(Rule rule) {
        this.rule = rule;
        startTime = System.nanoTime();
    }

    // for IDS Serialization
    public RuleEvaluationResult() {}

    public void setResult(R result) {
        this.result = result;
        this.stopTime = System.nanoTime();
    }

    public R getResult() {
        return result;
    }

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
        return Constants.IDS_RER;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {

    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {

    }
}
