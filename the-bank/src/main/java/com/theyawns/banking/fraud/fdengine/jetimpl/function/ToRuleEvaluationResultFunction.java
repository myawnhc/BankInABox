package com.theyawns.banking.fraud.fdengine.jetimpl.function;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;

public interface ToRuleEvaluationResultFunction<T extends HasID,R> {
    RuleEvaluationResult<T,R> applyAsResult(RuleEvaluationResult<T,R> value);

}
