package com.theyawns.banking.fraud.fdengine.jetimpl.function;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;

import java.io.Serializable;

/** This could just be collapsed with ToRuleEvaluationResultFunction, but keeping the distinction to follow
 *  the model established by Jet functional interfaces that extend the Java function library
 *
 *  @param <T>
 */
@FunctionalInterface
public interface DistributedToRuleEvaluationResultFunction<T extends HasID,R> extends ToRuleEvaluationResultFunction<T,R>, Serializable {

    RuleEvaluationResult<T,R> applyAsResultEx(RuleEvaluationResult<T,R> value) throws Exception;

    @Override
    default RuleEvaluationResult<T,R> applyAsResult(RuleEvaluationResult<T, R> value) {
        try {
            return applyAsResultEx(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);  // convert to unchecked
        }
    }
}