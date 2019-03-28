package com.theyawns.aggregators;

import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.aggregate.AggregateOperation1;
import com.theyawns.accumulators.BooleanResultAccumulator;
import com.theyawns.function.DistributedToRuleEvaluationResultFunction;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.RuleEvaluationResult;
import com.theyawns.ruleengine.RuleSetEvaluationResult;

import java.io.Serializable;

public final class ResultAggregator<T extends HasID> implements Serializable {

    // Parameter Types from AggregateOperation1:
    // * @param <T> the type of the stream item             RuleEvaluationResult<stream item type, evaluation result type>
    // * @param <A> the type of the accumulator             ResultAccumulator subclass (accumulates RuleEvaluationResults)
    // * @param <R> the type of the aggregation result      was going to vary (e.g., Boolean), but now think will always wrap as RuleSetEvaluationResult

    // TODO: this may move to a BooleanResultAggregator subclass
    public static <S extends HasID> AggregateOperation1<RuleEvaluationResult<S, Boolean>, BooleanResultAccumulator<S>, RuleSetEvaluationResult<S, Boolean>> anyTrue(
            DistributedToRuleEvaluationResultFunction<S, Boolean> getResultFn) {
        return AggregateOperation
                .withCreate(BooleanResultAccumulator<S>::new)
                .andAccumulate((BooleanResultAccumulator<S> a, RuleEvaluationResult<S, Boolean> rer) -> a.accumulate(getResultFn.applyAsResult(rer)))
                .andCombine(BooleanResultAccumulator<S>::combine)
                .andDeduct(BooleanResultAccumulator<S>::deduct)
                .andExportFinish(BooleanResultAccumulator<S>::anyTrue);
    }
}

// TODO: when above working reliably, implement other aggregation operations:
//    allTrue, allFalse, tied,
//    anyTrue, anyFalse
//    exceedsThreshold  (percentage or count?  true or false, or one for each?)
