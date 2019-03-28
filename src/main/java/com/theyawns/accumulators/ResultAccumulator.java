package com.theyawns.accumulators;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.RuleEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ResultAccumulator<T extends HasID, R> implements Serializable {

    List<RuleEvaluationResult<T,R>> allResults = new ArrayList<>();

    // Supports the aggregator's accumulate function
    public ResultAccumulator<T, R> accumulate(RuleEvaluationResult<T,R> result) {
        allResults.add(result);
        return this;
    }

    // Supports the aggregator's combine function
    public ResultAccumulator<T, R> combine(ResultAccumulator<T, R> other) {
        allResults.addAll(other.allResults);
        return this;
    }

    // Supports the aggregator's deduct function
    public ResultAccumulator<T, R> deduct(ResultAccumulator<T, R> other) {
        allResults.removeAll(other.allResults);
        return this;
    }

    // Multiple methods support the aggregator's exportFinish function, depending on the logic required

//    // TODO: these depend on the type of the RuleEvaluationResult and need to be parameterized
//    // This accumulator is only for results with Boolean type
//    public Boolean majorityTrue() {
//        return false;  // for now
//    }
//
//    // This method is only for use when the parameterized type of Rules is Boolean ... is there a better abstraction?
//    public RuleSetEvaluationResult<T, Boolean> anyTrue() {
//        // We need to actually process the evaluation rule collection and set the RSER state accordingly
//        RuleSetEvaluationResult<T, Boolean> ruleSetEvaluationResult = new RuleSetEvaluationResult<T, Boolean>();
//
//        ruleSetEvaluationResult.setEvaluationResult(false);
//        for (RuleEvaluationResult<T,R> rer : allResults) {
//            if (rer == null) {
//                System.out.println("!!!!! NULL RESULT passed to ResultAccumulator.anyTrue() accumulator");
//            } else {
//                if (rer.getItemId() == null) {
//                    System.out.println("!!!!! NULL item id in ResultAccumulator.anyTrue() accumulator");
//
//                } else {
//
//                    ruleSetEvaluationResult.setItem(rer.getItem());
//
//                    if ((Boolean)rer.getEvaluationResult()) {
//                        ruleSetEvaluationResult.setEvaluationResult(true);
//                        return ruleSetEvaluationResult;
//                    }
//                }
//            }
//        }
//        return ruleSetEvaluationResult;
//    }



    // Object overrides
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (this.getClass() != o.getClass()) return false;
        ResultAccumulator other = (ResultAccumulator) o;
        if (allResults.size() != other.allResults.size() ) return false;
        for (int i=0; i<allResults.size(); i++) {
            if (!allResults.get(i).equals(other.allResults.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        if (allResults.size() == 0) return 0;
        return allResults.get(0).hashCode();
    }

    @Override
    public String toString() {
        return "ResultAccumulator(count: " + allResults.size() + ')';
    }

}
