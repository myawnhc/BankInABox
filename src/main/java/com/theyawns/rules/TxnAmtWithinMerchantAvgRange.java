package com.theyawns.rules;

import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.rulesets.RuleSet;

import java.io.Serializable;

public class TxnAmtWithinMerchantAvgRange extends AbstractRule<Transaction, Double> implements Serializable {

    public static final String RULE_NAME = "TxnAmtWithinMerchantAvgRange";

    public TxnAmtWithinMerchantAvgRange(RuleSet merchantRuleSet) {
        super(RULE_NAME, merchantRuleSet, RuleCategory.FraudRules);

    }
    @Override
    public RuleEvaluationResult<Double> apply(Transaction transaction) {
        String merchantID = transaction.getMerchantId();

        return null;
    }
}
