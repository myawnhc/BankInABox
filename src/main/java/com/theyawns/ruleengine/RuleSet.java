package com.theyawns.ruleengine;

import com.hazelcast.jet.pipeline.GeneralStage;

import java.io.Serializable;
import java.util.Set;

// T is the stream item type that Rules will be applied to
// R is the result type for the final ruleset evaluation

// (there may be an intermediate result type for specific rules - so far not represented here

public abstract class RuleSet<T extends HasID> implements Serializable {

    protected String name;
    protected Set<Rule<T,?>> rules;
    abstract protected T getItem();

    protected GeneralStage enrichForPreconditions(GeneralStage<T> input) {
        return input;
    }
    protected GeneralStage enrichForProcessing(GeneralStage<T> input) {
        return input;
    }

    /** nop aggregation will be overridden in rulesets */
//    protected GeneralStage addAggregationStage(GeneralStageWithKey<RuleEvaluationResult<T>, String> input) {
//        return (GeneralStage) input;
//    }

    //abstract public AggregateOperation1<T, ResultAccumulator, RuleSetEvaluationResult> getAggregator();

}
