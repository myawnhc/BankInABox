package com.theyawns.accumulators;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.RuleEvaluationResult;
import com.theyawns.ruleengine.RuleSetEvaluationResult;

public class BooleanResultAccumulator<T extends HasID> extends ResultAccumulator<T, Boolean> {


    // This method is only for use when the parameterized type of Rules is Boolean ... is there a better abstraction?
    public RuleSetEvaluationResult<T, Boolean> anyTrue() {
        // We need to actually process the evaluation rule collection and set the RSER state accordingly
        RuleSetEvaluationResult<T, Boolean> ruleSetEvaluationResult = new RuleSetEvaluationResult<>();

        ruleSetEvaluationResult.setEvaluationResult(false);
        for (RuleEvaluationResult<T,Boolean> rer : allResults) {
            if (rer == null) {
                System.out.println("!!!!! NULL RESULT passed to ResultAccumulator.anyTrue() accumulator");
            } else {
                if (rer.getItemId() == null) {
                    System.out.println("!!!!! NULL item id in ResultAccumulator.anyTrue() accumulator");

                } else {

                    ruleSetEvaluationResult.setItem(rer.getItem());

                    if ((Boolean)rer.getEvaluationResult()) {
                        ruleSetEvaluationResult.setEvaluationResult(true);
                        return ruleSetEvaluationResult;
                    }
                }
            }
        }
        return ruleSetEvaluationResult;
    }

    public RuleSetEvaluationResult<T, Boolean> allTrue() {
        // We need to actually process the evaluation rule collection and set the RSER state accordingly
        RuleSetEvaluationResult<T, Boolean> ruleSetEvaluationResult = new RuleSetEvaluationResult<>();

        ruleSetEvaluationResult.setEvaluationResult(true);
        for (RuleEvaluationResult<T,Boolean> rer : allResults) {
            if (rer == null) {
                System.out.println("!!!!! NULL RESULT passed to ResultAccumulator.allTrue() accumulator");
            } else {
                if (rer.getItemId() == null) {
                    System.out.println("!!!!! NULL item id in ResultAccumulator.allTrue() accumulator");

                } else {

                    ruleSetEvaluationResult.setItem(rer.getItem());

                    if (!rer.getEvaluationResult()) {
                        ruleSetEvaluationResult.setEvaluationResult(false);
                        return ruleSetEvaluationResult;
                    }
                }
            }
        }
        return ruleSetEvaluationResult;
    }
}
