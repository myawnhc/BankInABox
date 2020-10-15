package com.theyawns.ruleengine;

// This may find use in the Jet-centric version of a RuleEngine,
// otherwise it can be removed.
// See the current Rule interface in the rules package
@Deprecated
public interface Rule<T extends HasID,R> {

    boolean checkPreconditions(T streamItem);

    RuleEvaluationResult<T,R> process(RuleTask<T> task);

    RuleTask<T> createTask();


    //Future<RuleEvaluationResult> proccessAsync(T transaction);
}
