package com.theyawns.ruleengine;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.Future;

public class RandomRule<T extends HasID, R> extends AbstractRule<T,R> implements Serializable {
        // implements Rule<T>, Serializable {

    private Random generator = new Random();

    public RandomRule(RuleSet set) {
        this.ruleSet = set;
    }

    @Override
    public boolean checkPreconditions(T streamItem) {
        return generator.nextBoolean();
    }

    @Override
    public RuleEvaluationResult process(RuleTask<T> task) {
        T streamItem = task.getStreamItem();
        if (generator.nextBoolean()) {
            return pass(streamItem);
        } else {
            return fail(streamItem);
        }
    }

    private RuleEvaluationResult pass(T streamItem) {
        RuleEvaluationResult ruleEvaluationResult = new RuleEvaluationResult<T,Boolean>("Random", ruleSet.name, streamItem);
        ruleEvaluationResult.evaluationResult = true;
        return ruleEvaluationResult;
    }

    private RuleEvaluationResult fail(T streamItem) {
        RuleEvaluationResult ruleEvaluationResult = new RuleEvaluationResult<T,Boolean>("Random", ruleSet.name, streamItem);
        ruleEvaluationResult.evaluationResult = false;
        return ruleEvaluationResult;
    }

    // TODO future
    //@Override
    public Future<RuleEvaluationResult> proccessAsync(T transaction) {
        return null;
    }

    @Override
    public String toString() {
        return "Random rule";
    }


}
