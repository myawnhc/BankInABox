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
    String ruleSetId;
    T item;
    //String itemId;
    R evaluationResult;  // early implementations have either Boolean or Integer here
    String message;   // intended for cases where evaluation cannot be completed

    // Not sure whether this belongs here or somewhere else ...
    long processingTime;  //

    public RuleEvaluationResult(String ruleName, String ruleSetId, T item) {
        this.ruleName = ruleName;
        this.ruleSetId = ruleSetId;
        this.item = item;
        //this.itemId = itemId;
        evaluationResult = null;
        message = "Not evaluated";
    }

    // Methods for grouping keys
    public String getRuleId() {
        return ruleName;
    }
    public String getRuleSetId() { return ruleSetId; }
    public T getItem() { return item; }
    public String getItemId() { return item.getID(); }
    public String getRuleSetAndItem() { return ruleSetId+":"+item.getID(); };

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

    public String toString() {
        return "RuleEvaluationResult " + ruleName + " " + message;
    }
}
