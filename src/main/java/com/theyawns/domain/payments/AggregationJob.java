package com.theyawns.domain.payments;

import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;
import com.theyawns.aggregators.ResultAggregator;
import com.theyawns.ruleengine.RuleEvaluationResult;
import com.theyawns.ruleengine.RuleSetEvaluationResult;

import java.util.List;

// Aggregate a micro-batch of RuleEvaluationResults (all results for a particular transaction)

public class AggregationJob {

    // TODO: not sure how this runs
    //      if run once for every commpleted txn, that

    public Pipeline buildPipeline(String forAcctNo) {
        Pipeline p = Pipeline.create();

        // Generic types of functional return, key type, item type
        // We actually only expect a single item to be in our batch source since txn id is unique
        BatchStage<List<RuleEvaluationResult<Transaction,Boolean>>> resultList =
            p.drawFrom(Sources.<List<RuleEvaluationResult<Transaction,Boolean>>, String, List<RuleEvaluationResult<Transaction,Boolean>>>map("resultsMap",
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
