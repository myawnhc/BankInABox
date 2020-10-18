package com.theyawns.ruleengine.rules;

import java.io.Serializable;

@Deprecated
public abstract class AbstractEvaluationResult<R> implements Serializable {

    public long startTime;
    public long endTime;
    public long evaluationTime;

    // Accumulated result of rule evaluations
    // Fraud Rules will have double result type; percentage likelihood that txn is fraudulent
    // Payment rules will have a Boolean result type - true = pay, false = reject
    private R result;

    private RuleExecutionPlatform platform;

    protected void recordStart() {
        startTime = System.nanoTime();
    }

    protected void recordStop() {
        endTime = System.nanoTime();
    }

    protected long getElapsedNanos() {
        return endTime - startTime;
    }

    protected long getElapsedMillis() {
        return (endTime - startTime) / 1000;
    }

    protected R getResult() {
        return result;
    }
}
