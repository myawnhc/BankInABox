package com.theyawns.accumulators;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.RuleEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** A Jet accumulator class to accumulate instances of RuleEvaluationResult
 *
 * @param <T> the type of the stream item
 * @param <R> the type of the result item
 */
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

    // Multiple methods support the aggregator's exportFinish function, depending on the logic required,
    // and specific to the result type.  See BooleanResultAccumulator as the initial implementation.

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
