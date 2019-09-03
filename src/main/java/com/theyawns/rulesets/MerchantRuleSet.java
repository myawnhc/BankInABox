package com.theyawns.rulesets;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.domain.payments.TransactionFinalStatus;
import com.theyawns.rules.Rule;
import com.theyawns.rules.RuleCategory;
import com.theyawns.rules.RuleEvaluationResult;
import com.theyawns.rules.TxnAmtWithinMerchantAvgRange;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public class MerchantRuleSet extends AbstractRuleSet<Transaction,Double> implements Serializable, HazelcastInstanceAware {

    private static final String RULESET_NAME = "Merchant Rules";
    private List<RuleEvaluationResult<Double>> ruleResults;
    private transient HazelcastInstance hazelcast;
    private IMap<String, Merchant> merchantMap;


    public MerchantRuleSet() {
        super(RULESET_NAME, RuleCategory.FraudRules);
        rules.add(new TxnAmtWithinMerchantAvgRange(this));
    }

    @Override
    public RuleSetEvaluationResult<Transaction, Double> apply(Transaction transaction) {
        System.out.println("MerchantRuleSet.apply()");
        // Process rules.  With this simple rule we can aggregate as we go; more complex rules might
        // need a separate pass over the RERs to produce the RSER.

        // Not a big efficiency, but we'll take every little edge ... get the merchant just once rather than
        // fetching it for every rule evaluation
        Merchant m = merchantMap.get(transaction.getMerchantId());
        double aggregatedResult = 0.0;
        for (Rule<Transaction,Double> rule : super.rules) {
            RuleEvaluationResult<Double> rer = rule.apply(transaction);
            ruleResults.add(rer);
            aggregatedResult += rer.getResult();
        }

        // Aggregate the results into a RuleSetEvaluationResult.

        RuleSetEvaluationResult rser = new RuleSetEvaluationResult(transaction, getQualifiedName());
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

    // TODO: push up to AbstractRuleSet along with HazelcastInstanceAware interface
    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        System.out.println("Setting HazelcastInstance for MerchantRuleSet");
        this.hazelcast = hazelcastInstance;
        merchantMap = hazelcast.getMap(Constants.MAP_MERCHANT);
    }
}
