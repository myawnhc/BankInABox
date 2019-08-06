package com.theyawns.rulesets;

import com.theyawns.domain.payments.Transaction;
import com.theyawns.domain.payments.TransactionFinalStatus;
import com.theyawns.rules.*;

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
        ruleResults = new ArrayList<RuleEvaluationResult<Double>>(5);
    }

    @Override
    public RuleSetEvaluationResult<Transaction,Double> apply(Transaction transaction) {
        //System.out.println("LocationBasedRuleSet.apply()");
        // Process rules.  With this simple rule we can aggregate as we go; more complex rules might
        // need a separate pass over the RERs to produce the RSER.
        double aggregatedResult = 0.0;
        for (Rule<Transaction,Double> rule : super.rules) {
            RuleEvaluationResult<Double> rer = rule.apply(transaction);
            ruleResults.add(rer);
            aggregatedResult += rer.getResult();


        }

        // Aggregate the results into a RuleSetEvaluationResult.

        RuleSetEvaluationResult rser = new RuleSetEvaluationResult(transaction,this);
        rser.setResult(aggregatedResult);

        // What value is the threshold for pass/fail?  Will be part of the design of each ruleset, so
        // definitely an implementation detail of the individual rulesets.
        // Our median reject rates of 0.1 + 0.2 + 0.3 + 0.4 + 0.5 = 1.5
        // For an initial starting point lets say < 1.0 is a reject
        if (aggregatedResult <= 1.0)
            rser.setRuleSetOutcome(TransactionFinalStatus.RejectedForFraud, "Sum of results exceeds threshold");
        else
            rser.setRuleSetOutcome(TransactionFinalStatus.Approved);


        return rser;
    }
}
