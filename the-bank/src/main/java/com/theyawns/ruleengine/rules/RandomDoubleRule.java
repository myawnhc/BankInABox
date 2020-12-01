/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

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
