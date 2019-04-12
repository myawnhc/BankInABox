package com.theyawns.domain.payments;

import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;

import java.util.List;

public class AggregationJob {

    // TODO: not sure how this runs
    //      if run once for every commpleted txn, that

    public Pipeline buildPipeline(String forAcctNo) {
        Pipeline p = Pipeline.create();

        // Generic types of functional return, key type, item type
        // We actually only expect a single item to be in our batch source since txn id is unique
        BatchStage<List<RuleExecutionResult>> resultList =
            p.drawFrom(Sources.<List<RuleExecutionResult>, String, List<RuleExecutionResult>>map("resultsMap",
                /* predicateFn */  e -> forAcctNo.equals(e.getValue().get(0).getTransactionID()),
                /* projectionFn */ e -> e.getValue()));

        // Flatten the list
        BatchStage<RuleExecutionResult> results = resultList.flatMap(e -> Traversers.traverseIterator(e.iterator()));

        // Grouping key isn't needed since we selected with a predicate that only returns out items of interest

        // TODO: aggregate.  For the transaction to pass, we require that all rules pass.
        // BooleanResultAccumulator.allTrue
        // ResultAggregator.allTrue
        // TODO: Both of above use RuleEvalationResult as input, have switched to somewhat different RuleExecutionResult.
        // Update the accumulator and aggregator
        // Will also need to update [Distributed]ToRuleEvaluationResultFunction
        // .. enough changes that it might be worth revisiting/refactoring RuleEvaluationResult to take on the
        //  role of RuleExecutionResult, and continue to use RuleSetEvaluationResult as before.

        return p;

    }
}
