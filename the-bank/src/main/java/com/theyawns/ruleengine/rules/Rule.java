package com.theyawns.ruleengine.rules;

import java.util.function.Function;

// T will always be Transaction in this demo, keeping signature generic to show applicability to general RuleEngine case
public interface Rule<T,R> extends Function<T, RuleEvaluationResult<R>> {

    public String getName();
    // Category name : RuleSet name : Rule Name
    public String getQualifiedName(); // includes category and ruleset

}
