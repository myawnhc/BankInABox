package com.theyawns.rulesets;

import com.theyawns.rules.Rule;

import java.util.List;
import java.util.function.Function;

// T - type of item to apply rules to, e.g., Transaction
// R - type of final result from the ruleset, currently either Double (fraud rules)
//     or Boolean (credit rules)
public interface RuleSet<T,R> extends Function<T, RuleSetEvaluationResult<T,R>> {

    void add(Rule<T,R> rule);
    String getName();
    String getQualifiedName();  // includes category
    int getRuleCount();
    List<Rule<T,R>> getRules();

    // Now considering this a private implementation detail, not public API
    //RuleSetEvaluationResult<R> aggregateResults();
}
