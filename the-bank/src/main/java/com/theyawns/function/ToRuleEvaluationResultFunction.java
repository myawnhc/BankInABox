package com.theyawns.function;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.RuleEvaluationResult;

public interface ToRuleEvaluationResultFunction<T extends HasID,R> {
    RuleEvaluationResult<T,R> applyAsResult(RuleEvaluationResult<T,R> value);

}
