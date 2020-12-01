/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.ruleengine.jetimpl.aggregators;

import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.aggregate.AggregateOperation1;
import com.theyawns.ruleengine.jetimpl.accumulators.BooleanResultAccumulator;
import com.theyawns.banking.fraud.fdengine.jetimpl.function.DistributedToRuleEvaluationResultFunction;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.jetimpl.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;

/** A Jet aggregation that operates on inputs of type RuleEvaluationResult.
 * The aggregated object will be of type RuleSetEvaluationResult.
 *
 * Not in active use in main BankInABox flow; used by AggregationJob in domain.payments which is
 * not currently used; however it hasn't been deprecated because it may be useful in some
 * future scenarios that could be added to the demo.
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
