package com.theyawns.ruleengine.rules;

import com.theyawns.ruleengine.rulesets.RuleSet;

import java.io.Serializable;
import java.util.Random;

public class RandomDoubleRule<T> extends AbstractRule<T,Double> implements Serializable {

    private double rejectRate;
    private Random random = new Random(12345);

    public RandomDoubleRule(double rejectRate, RuleSet ruleSet, RuleCategory category) {
        super("RandomDoubleRule" + rejectRate, ruleSet, category);
        this.rejectRate = rejectRate;
    }

    //@Override
    public RuleEvaluationResult<Double> apply(T input) {
        // Not truly random .. will be normally distributed around average
        RuleEvaluationResult<Double> result = new RuleEvaluationResult<Double>(this);
        double stddev = rejectRate / 5;
        double amount = random.nextGaussian() * stddev + rejectRate;
        result.setResult(amount);
        return result;
    }

    // Test
    public static void main(String args[]) {
        RandomDoubleRule pointFive = new RandomDoubleRule(0.5, null, RuleCategory.FraudRules );
        //double[] values = new double[10];
        for (int i=0; i<10; i++)
        {
            System.out.println(pointFive.apply(null));
        }
    }
}
