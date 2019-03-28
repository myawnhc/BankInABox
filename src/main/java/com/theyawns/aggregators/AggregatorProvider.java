package com.theyawns.aggregators;

import com.hazelcast.jet.aggregate.AggregateOperation1;
import com.theyawns.accumulators.ResultAccumulator;
import com.theyawns.ruleengine.RuleSetEvaluationResult;

// Not in use, consider deprecating
public interface AggregatorProvider<T> {
    AggregateOperation1<T, ResultAccumulator, RuleSetEvaluationResult> getAggregator();
}