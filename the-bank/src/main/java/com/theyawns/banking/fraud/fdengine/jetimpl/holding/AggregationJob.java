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

package com.theyawns.banking.fraud.fdengine.jetimpl.holding;

import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;
import com.theyawns.banking.Transaction;
import com.theyawns.ruleengine.jetimpl.aggregators.ResultAggregator;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.jetimpl.rulesets.RuleSetEvaluationResult;

import java.util.List;

/** Jet job to aggregate a set of RuleEvaluationResults, returning a RuleSetEvaluationResult.
 *
 * Originally used when Fraud Detection rules were implemented as separate Jet pipelines; when all
 * pipelines were complete the aggregation job would tally the results.
 *
 * This is not currently in use in the main retail banking demo, but I anticipate it will be useful for some
 * side projects I'd like to create running against the same basic setup.
 *
 */
public class AggregationJob {

    // TODO: not sure how this runs
    //      if run once for every commpleted txn, that

    public Pipeline buildPipeline(String forAcctNo) {
        Pipeline p = Pipeline.create();

        // Generic types of functional return, key type, item type
        // We actually only expect a single item to be in our batch source since txn id is unique
        BatchStage<List<RuleEvaluationResult<Transaction,Boolean>>> resultList =
            p.readFrom(Sources.<List<RuleEvaluationResult<Transaction,Boolean>>, String, List<RuleEvaluationResult<Transaction,Boolean>>>map("resultsMap",
                /* predicateFn */  e -> forAcctNo.equals(e.getValue().get(0).getItemId()),
                /* projectionFn */ e -> e.getValue()));

        // Flatten the list
        BatchStage<RuleEvaluationResult<Transaction,Boolean>> results = resultList.flatMap(e -> Traversers.traverseIterator(e.iterator()));

        // Grouping key isn't needed since we selected with a predicate that only returns out items of interest

        BatchStage<RuleSetEvaluationResult<Transaction,Boolean>> aggregated = results.rollingAggregate(
                ResultAggregator.allTrue(RuleEvaluationResult::getValue))
                .setName("Aggregate ruleset results");

        // TODO: drain

        return p;

    }
}
