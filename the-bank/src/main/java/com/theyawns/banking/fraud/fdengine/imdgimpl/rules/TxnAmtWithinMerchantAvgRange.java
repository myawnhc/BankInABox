/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.banking.fraud.fdengine.imdgimpl.rules;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.ruleengine.rules.AbstractRule;
import com.theyawns.ruleengine.rules.RuleCategory;
import com.theyawns.ruleengine.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.rulesets.RuleSet;

import java.io.Serializable;

public class TxnAmtWithinMerchantAvgRange extends AbstractRule<Transaction, Merchant.RISK> implements Serializable, HazelcastInstanceAware {

    public static final String RULE_NAME = "TxnAmtWithinMerchantAvgRange";

    private transient HazelcastInstance hazelcast;
    private ReplicatedMap<String, Merchant> merchantMap;

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
        this.merchantMap = hazelcast.getReplicatedMap(Constants.MAP_MERCHANT);
    }
}
