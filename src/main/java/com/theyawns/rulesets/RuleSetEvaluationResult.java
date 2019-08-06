package com.theyawns.rulesets;

import com.theyawns.domain.payments.TransactionFinalStatus;
import com.theyawns.rules.TransactionEvaluationResult;

import java.io.Serializable;

public class RuleSetEvaluationResult<T,R> implements Serializable {

    private long startTime;
    private long stopTime;

    private T item;
    private R result;
    private TransactionFinalStatus ruleSetOutcome;
    private String reason;

    private RuleSet ruleSet;

    public RuleSetEvaluationResult(T item, RuleSet ruleSet) {
        this.item = item;
        this.ruleSet = ruleSet;
        startTime = System.nanoTime();
    }

    public RuleSet getRuleSet() {
        return ruleSet;
    }

    public void setResult(R result) {
        this.result = result;
        this.stopTime = System.nanoTime();
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

    public long getElapsedNanos() {
        return stopTime - startTime;
    }

    public String toString() {
        return result.toString();
    }
}