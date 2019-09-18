package com.theyawns.ruleengine;

// TOOO: May parameterize RuleEvaluationResult as well

public interface Rule<T extends HasID,R> {

    boolean checkPreconditions(T streamItem);

    RuleEvaluationResult<T,R> process(RuleTask<T> task);

    RuleTask<T> createTask();


    //Future<RuleEvaluationResult> proccessAsync(T transaction);
}
