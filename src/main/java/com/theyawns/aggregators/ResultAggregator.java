package com.theyawns.aggregators;

import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.aggregate.AggregateOperation1;
import com.theyawns.accumulators.BooleanResultAccumulator;
import com.theyawns.function.DistributedToRuleEvaluationResultFunction;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.RuleEvaluationResult;
import com.theyawns.ruleengine.RuleSetEvaluationResult;

import java.io.Serializable;

/** A Jet aggregation that operates on inputs of type RuleEvaluationResult.
 * The aggregated object will be of type RuleSetEvaluationResult.
 *
 * @param <T> The type of items in the stream
 */
public final class ResultAggregator<T extends HasID> implements Serializable {

    // Parameter Types from AggregateOperation1:
    // * @param <T> the type of the stream item             RuleEvaluationResult<stream item type, evaluation result type>
    // * @param <A> the type of the accumulator             ResultAccumulator subclass (accumulates RuleEvaluationResults)
    // * @param <R> the type of the aggregation result      was going to vary (e.g., Boolean), but now think will always wrap as RuleSetEvaluationResult

    public static <S extends HasID> AggregateOperation1<RuleEvaluationResult<S, Boolean>, BooleanResultAccumulator<S>, RuleSetEvaluationResult<S, Boolean>> anyTrue(
            DistributedToRuleEvaluationResultFunction<S, Boolean> getResultFn) {
        return AggregateOperation
                .withCreate(BooleanResultAccumulator<S>::new)
                .andAccumulate((BooleanResultAccumulator<S> a, RuleEvaluationResult<S, Boolean> rer) -> a.accumulate(getResultFn.applyAsResult(rer)))
                .andCombine(BooleanResultAccumulator<S>::combine)
                .andDeduct(BooleanResultAccumulator<S>::deduct)
                .andExportFinish(BooleanResultAccumulator<S>::anyTrue);
    }

    public static <S extends HasID> AggregateOperation1<RuleEvaluationResult<S, Boolean>, BooleanResultAccumulator<S>, RuleSetEvaluationResult<S, Boolean>> allTrue(
            DistributedToRuleEvaluationResultFunction<S, Boolean> getResultFn) {
        return AggregateOperation
                .withCreate(BooleanResultAccumulator<S>::new)
                .andAccumulate((BooleanResultAccumulator<S> a, RuleEvaluationResult<S, Boolean> rer) -> a.accumulate(getResultFn.applyAsResult(rer)))
                .andCombine(BooleanResultAccumulator<S>::combine)
                .andDeduct(BooleanResultAccumulator<S>::deduct)
                .andExportFinish(BooleanResultAccumulator<S>::allTrue);
    }
}

// TODO: implement other aggregation operations: allFalse, anyFalse, majorityTrue, majorityFalse
