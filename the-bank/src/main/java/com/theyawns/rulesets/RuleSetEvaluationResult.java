package com.theyawns.rulesets;

import com.theyawns.domain.payments.TransactionFinalStatus;

import java.io.Serializable;

public class RuleSetEvaluationResult<T,R> implements Serializable {

    private long startTime;
    private long stopTime;

    private T item;
    private R result;
    private TransactionFinalStatus ruleSetOutcome;
    private String reason;

    //private transient RuleSet ruleSet;
    private String ruleSetName;

    public RuleSetEvaluationResult(T item, String ruleSetName) {
        this.item = item;
        //this.ruleSet = ruleSet;
        this.ruleSetName = ruleSetName;
        startTime = System.currentTimeMillis();
    }

    public String getRuleSetName() {
        return ruleSetName;
    }

    public void setResult(R result) {
        this.result = result;
        this.stopTime = System.currentTimeMillis();
    }

    public R getResult() {
        return result;
    }

    public T getItem() { return item; }

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

    public long getElapsedMillis() {
        return stopTime - startTime;
    }

    public String toString() {
        return result.toString();
    }
}