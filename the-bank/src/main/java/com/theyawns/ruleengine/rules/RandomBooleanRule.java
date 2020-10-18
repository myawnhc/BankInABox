package com.theyawns.ruleengine.rules;

import com.theyawns.ruleengine.rulesets.RuleSet;

import java.io.Serializable;
import java.util.Random;

public class RandomBooleanRule<T> extends AbstractRule<T, Boolean> implements Serializable {

    private int rejectPercentage;
    private Random random = new Random(42);

    public RandomBooleanRule(int rejectPercentage, RuleSet ruleSet, RuleCategory category) {
        super("RandomBoolean" + rejectPercentage, ruleSet, category);
        this.rejectPercentage = rejectPercentage;
    }

    public RuleEvaluationResult<Boolean> apply(T input) {
        RuleEvaluationResult<Boolean> result = new RuleEvaluationResult<Boolean>(this);

        int value = random.nextInt(100);
        result.setResult(value < rejectPercentage);
        return result;
    }
}
