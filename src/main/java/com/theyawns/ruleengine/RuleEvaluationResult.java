package com.theyawns.ruleengine;

import java.io.Serializable;

// TODO: IdentifiedDataSerializable
// TODO: might become ? extends ResultType if we want to introduce a ResultType hierarchy

/** This is the result of evaluating a single rule.
 *  It is parameterized by the type of result the rule will return; for example, there may be rules that
 *  return true/false (is this transaction suspected to be fraudulent?), or rules that return a numeric score
 *  (how strongly does purchase of item in the cart correlate with purchase of some other order), etc.
 *
 * @param <R> Result type
 */
public class RuleEvaluationResult<T extends HasID, R> implements Serializable {

    String ruleName;
    String ruleSetId; // not used by new rule design
    T item;
    //String itemId;
    R evaluationResult;  // early implementations have either Boolean or Integer here
    String message;   // intended for cases where evaluation cannot be completed
    long elapsedTime;

    // Not sure whether this belongs here or somewhere else ...
    long processingTime;  //

    public RuleEvaluationResult(T item, String ruleName) {
        this.item = item;
        this.ruleName = ruleName;
        message = "Not evaluated";
    }

    // Used by deprecated code, will probably deprecate and drop this eventually
    public RuleEvaluationResult(String ruleName, String ruleSetId, T item) {
        this.ruleName = ruleName;
        this.ruleSetId = ruleSetId;
        this.item = item;
        //this.itemId = itemId;
        evaluationResult = null;
        message = "Not evaluated";
    }

    public void setElapsed(long timeInMillis) {
        elapsedTime = timeInMillis;
    }

    // Methods for grouping keys
    public T getItem() { return item; }
    public String getItemId() { return item.getID(); }

    // might not need these methods
    public RuleEvaluationResult<T,R> getValue() {
        return this;
    }

    public R getEvaluationResult() {
        return evaluationResult;
    }

    public void setEvaluationResult(R result) {
        this.evaluationResult = result;
        this.message = "evaluated to: " + evaluationResult;
    }
    // end possible deprecatable methods

    public String toString() {
        return "RuleEvaluationResult " + ruleName + " " + message;
    }
}
