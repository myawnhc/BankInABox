package com.theyawns.ruleengine.holding;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.jetimpl.rules.Rule;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;

import java.io.Serializable;

// TODO: better serialization
@Deprecated
public class RuleTask<T extends HasID> implements Runnable, Serializable {
    // For each transaction, create one RuleTask for each Rule.   Queue these up for the worker pool.
    // Each completed RuleTask notes completion in some structure (probably map keyed by transaction ruleName)
    // When all tasks for a transaction are complete, the RuleEvaluationResult can be created and posted.

    // IMDG client should thus be waiting on a Future
    String ruleSetID;
    Rule rule;
    protected T item;
    RuleEvaluationResult<T,? extends Object> ruleEvaluationResult;




//    // NO - don't want these here.  Want to inject an instance instead ...
//    transient private IMap<String, Integer> pendingTransactionsMap;
//    transient private IMap<String, List<RuleEvaluationResult>> ruleResults;

    public RuleTask(Rule rule, String ruleSetID, T item) {
        this.rule = rule;
        this.ruleSetID = ruleSetID;
        this.item = item;
        //this.pendingTransactionsMap = pendingMap;
       // this.ruleResults = resultsMap;
    }

    public static String getRuleSetID(RuleTask task) {
        return task.ruleSetID;
    }

    public static String getItemID(RuleTask task) {
        return task.item.getItemID();
    }

    public T getStreamItem() { return item; }

//    public void setPendingTransactionsMap(IMap<String, Integer> map) {
//        pendingTransactionsMap = map;
//    }
//
//    public void setRuleResultsMap(IMap<String, List<RuleEvaluationResult>> map) {
//        ruleResults = map;
//    }

    public boolean checkPreconditions(T item) {
        return rule.checkPreconditions(item);
    }

    public boolean checkPreconditions() {
        return rule.checkPreconditions(item);
    }

    // for IMDG
    public void run() {
//        ruleEvaluationResult = rule.process(item);
//        IMDGRuleEngine.AddResultEntryProcessor ep = new IMDGRuleEngine.AddResultEntryProcessor(ruleEvaluationResult);
//        ruleResults.executeOnKey(item.getRuleId(), ep);
//        Integer value = (Integer) pendingTransactionsMap.executeOnKey(item.getRuleId(), IMDGRuleEngine.decrementingEP );
//        if (value == 0) {
//            pendingTransactionsMap.remove(item.getRuleId());
//            //if (item.getTransactionId().endsWith("0000"))
//                //System.out.println("Finished with " + item.getTransactionId() + ", should do something with Results");
////        } else if (item.getTransactionId().endsWith("0000")) {
////            //System.out.println(item.getTransactionId() + " has " + value + " rules still pending");
//        }
    }

    // for Jet
    public RuleEvaluationResult<T,? extends Object> getRuleEvaluationResult() {
        ruleEvaluationResult = rule.process(this);
        return ruleEvaluationResult;
    }



    @Override
    public String toString() {
        return "Task for " + rule + " with " + item;
    }

}
