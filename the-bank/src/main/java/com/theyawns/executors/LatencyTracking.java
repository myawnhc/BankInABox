package com.theyawns.executors;

import java.io.Serializable;

public class LatencyTracking implements Serializable {
    // Stored values are nanotime

    public static final int LOCATION_RULESET_INDEX = 0;
    public static final int MERCHANT_RULESET_INDEX = 1;

    // Set by PreauthMapListener.entryAdded
    public long timeEnqueued;
    // Pair set by RuleSetExecutor.call()
    public long[] timeTakenFromQueueByRuleSet = new long[2];
    // Following are all set by RuleSetExecutor.consumeResult - for which EP?
    public long[] timeEvaluationEntryProcessorSubmitted = new long[2];
    public long[] timeEvaluationEntryProcessorCompleted = new long[2];
    public long timeOfferedToCompletionQueue;
    public long timeTakenFromCompletionQueue;
    public long timeMapCleanupStarts;
    public long timeMapCleanupEnds;

    public synchronized void setTimeTakenFromQueueByRuleset(int index) {
        timeTakenFromQueueByRuleSet[index] = System.nanoTime();
    }

    //////////////////// Remaining methods are called at end of run to analyze latencies

    // All returned values are milliseconds
    public int timeWaitingForTakeByRuleset(int index) {
        return (int) (timeTakenFromQueueByRuleSet[index] - timeEnqueued) / 1_000_000;
    }

    public int timeWaitingForEntryProcessor(int index) {
        return (int) (timeEvaluationEntryProcessorCompleted[index]- timeEvaluationEntryProcessorSubmitted[index]) / 1_000_000;
    }

    public int timeWaitingOnCompletionQueue() {
        return (int) (timeTakenFromCompletionQueue - timeOfferedToCompletionQueue) / 1_000_000;
    }

    public int timeCleaningUp() {
        return (int) (timeMapCleanupEnds - timeMapCleanupStarts) / 1_000_000;
    }
}
