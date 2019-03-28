package com.theyawns.function;

import com.theyawns.ruleengine.RuleSetEvaluationResult;

@Deprecated  // Think I only need an accumulator for the Rules, not the Ruleset
@FunctionalInterface
public interface ToRulesetEvaluationResultFunction<T> {
    RuleSetEvaluationResult applyAsResult(T value);
}
