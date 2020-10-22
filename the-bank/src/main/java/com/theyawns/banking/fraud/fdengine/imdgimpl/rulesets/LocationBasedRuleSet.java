package com.theyawns.banking.fraud.fdengine.imdgimpl.rulesets;

import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionFinalStatus;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rules.RandomDoubleRule;
import com.theyawns.ruleengine.rules.Rule;
import com.theyawns.ruleengine.rules.RuleCategory;
import com.theyawns.ruleengine.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.rulesets.AbstractRuleSet;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** RuleSet contain rules that need Location information for the evaluation.
 *
 *  Initially filling this ruleset with random rules as a placeholder
 */
public class LocationBasedRuleSet extends AbstractRuleSet<Transaction,Double> implements Serializable {

    private List<RuleEvaluationResult<Double>> ruleResults;

    public LocationBasedRuleSet() {
        super("LocationBasedRules", RuleCategory.FraudRules);
        Rule<Transaction,Double> r1 = new RandomDoubleRule<Transaction>(0.1, this, RuleCategory.FraudRules);
        Rule<Transaction,Double> r2 = new RandomDoubleRule<Transaction>(0.2, this, RuleCategory.FraudRules);
        Rule<Transaction,Double> r3 = new RandomDoubleRule<Transaction>(0.3, this, RuleCategory.FraudRules);
        Rule<Transaction,Double> r4 = new RandomDoubleRule<Transaction>(0.4, this, RuleCategory.FraudRules);
        Rule<Transaction,Double> r5 = new RandomDoubleRule<Transaction>(0.5, this, RuleCategory.FraudRules);
        super.add(r1);
        super.add(r2);
        super.add(r3);
        super.add(r4);
        super.add(r5);
        ruleResults = new ArrayList<>(5);
    }

    @Override
    public RuleSetEvaluationResult<Transaction,Double> apply(ItemCarrier<Transaction> carrier) {
        // Create this first to start the timer
        RuleSetEvaluationResult rser = new RuleSetEvaluationResult(carrier, getQualifiedName());

        //System.out.println("LocationBasedRuleSet.apply()");
        // Process rules.  With this simple rule we can aggregate as we go; more complex rules might
        // need a separate pass over the RERs to produce the RSER.
        double aggregatedResult = 0.0;
        for (Rule<Transaction,Double> rule : super.rules) {
            RuleEvaluationResult<Double> rer = rule.apply(carrier.getItem());
            if (rer == null) {
                System.out.println("Null result from " + rule.getName());
                throw new IllegalStateException("Null result from " + rule.getName());
            }
            ruleResults.add(rer);
            aggregatedResult += rer.getResult();
        }

        // What value is the threshold for pass/fail?  Will be part of the design of each ruleset, so
        // definitely an implementation detail of the individual rulesets.
        // Our median reject rates of 0.1 + 0.2 + 0.3 + 0.4 + 0.5 = 1.5
        // For an initial starting point lets say < 1.0 is a reject
        if (aggregatedResult <= 1.0)
            rser.setRuleSetOutcome(TransactionFinalStatus.RejectedForFraud, "Sum of results exceeds threshold");
        else
            rser.setRuleSetOutcome(TransactionFinalStatus.Approved);

        // Aggregate the results into a RuleSetEvaluationResult.
        // Do last so all ruleset processing is captured in the timing info
        rser.setResult(aggregatedResult);

        return rser;
    }
}
