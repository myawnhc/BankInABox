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

// Why can't I do ItemCarrier<T> here ?
public class RuleSetEvaluationResult<T extends HasID,R> implements IdentifiedDataSerializable, Serializable {

//    private long startTime;
//    private long stopTime;

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
//        startTime = System.nanoTime();
    }

    // for IDS Serialization
    public RuleSetEvaluationResult() {}

    public String getRuleSetName() {
        return ruleSetName;
    }

    public void setResult(R result) {
        this.result = result;
//        this.stopTime = System.nanoTime();
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
        return Constants.IDS_RULESET_EVAL_RESULT;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {

    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {

    }
}