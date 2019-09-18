package com.theyawns.rules;

import java.io.Serializable;

public class RuleEvaluationResult<R> implements Serializable {

    private long startTime;
    private long stopTime;

    private R result;

    private Rule rule;

    public RuleEvaluationResult(Rule rule) {
        this.rule = rule;
        startTime = System.nanoTime();
    }

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
}
