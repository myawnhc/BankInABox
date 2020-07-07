package com.theyawns.rules;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.rulesets.RuleSet;

import java.io.Serializable;

public class TxnAmtWithinMerchantAvgRange extends AbstractRule<Transaction, Merchant.RISK> implements Serializable, HazelcastInstanceAware {

    public static final String RULE_NAME = "TxnAmtWithinMerchantAvgRange";

    private transient HazelcastInstance hazelcast;
    private IMap<String, Merchant> merchantMap;

    public TxnAmtWithinMerchantAvgRange(RuleSet merchantRuleSet) {
        super(RULE_NAME, merchantRuleSet, RuleCategory.FraudRules);

    }
    @Override
    public RuleEvaluationResult<Merchant.RISK> apply(Transaction transaction) {
        if (hazelcast == null) System.exit(-5);
        String merchantID = transaction.getMerchantId();
        Merchant merchant = merchantMap.get(merchantID);
        Double avgTxnAmount = merchant.getAvgTxnAmount();
        Double amount = transaction.getAmount();
        Merchant.RISK risk;
        int stddev = (int) (avgTxnAmount / 5);
        // Roughly 70% of transactions should be within 1 std deviation
        if (amount >= avgTxnAmount-stddev && amount <= avgTxnAmount+stddev)
            risk = Merchant.RISK.LOW;
            // Roughly 95% of transactions should be within 2 std deviations
        else if (amount >= avgTxnAmount-2*stddev && amount <= avgTxnAmount+2*stddev)
            risk = Merchant.RISK.MEDIUM;
            // Over 99% of transactions should be within 3 - currently treating everything
            // outside of 2 std deviations the same
        else
            risk = Merchant.RISK.HIGH;

        RuleEvaluationResult<Merchant.RISK> result = new RuleEvaluationResult<>(this);
        result.setResult(risk);
        return result;
    }

    // NOTE: NOT auto-injected, must be handled by the RuleSet
    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
        this.merchantMap = hazelcast.getMap(Constants.MAP_MERCHANT);
    }
}
