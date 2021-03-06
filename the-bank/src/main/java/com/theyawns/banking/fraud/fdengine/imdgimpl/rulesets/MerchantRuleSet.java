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

package com.theyawns.banking.fraud.fdengine.imdgimpl.rulesets;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionFinalStatus;
import com.theyawns.banking.fraud.fdengine.imdgimpl.rules.TxnAmtWithinMerchantAvgRange;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rules.Rule;
import com.theyawns.ruleengine.rules.RuleCategory;
import com.theyawns.ruleengine.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.rulesets.AbstractRuleSet;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MerchantRuleSet extends AbstractRuleSet<Transaction,Merchant.RISK> implements Serializable, HazelcastInstanceAware {

    private static final String RULESET_NAME = "Merchant Rules";
    private List<RuleEvaluationResult<Merchant.RISK>> ruleResults;
    private transient HazelcastInstance hazelcast;
    //private IMap<String, Merchant> merchantMap;

    public MerchantRuleSet() {
        super(RULESET_NAME, RuleCategory.FraudRules);
        super.add(new TxnAmtWithinMerchantAvgRange(this));
        ruleResults = new ArrayList<>(1);
    }

    @Override
    public RuleSetEvaluationResult<Transaction, Merchant.RISK> apply(ItemCarrier<Transaction> carrier) {
        //System.out.println("MerchantRuleSet.apply()");

        // Process rules.  With this simple rule we can aggregate as we go; more complex rules might
        // need a separate pass over the RERs to produce the RSER.
        Transaction transaction = carrier.getItem();
        // Each rule returns a High, Medium or Low risk score
        // We will reject on a single High or multiple mediums.
        int mediumRiskCount = 0;
        Merchant.RISK aggregatedRisk = Merchant.RISK.LOW;
        for (Rule<Transaction,Merchant.RISK> rule : super.rules) {
            RuleEvaluationResult<Merchant.RISK> rer;
            try {
                rer = rule.apply(transaction);
                if (rer == null) {
                    System.out.println("Null result from " + rule.getName());
                    throw new IllegalStateException("Null result from " + rule.getName());
                }
                ruleResults.add(rer);
                if (rer.getResult() == Merchant.RISK.HIGH) {
                    aggregatedRisk = Merchant.RISK.HIGH;
                    break;   // NO need to keep looking
                } else if (rer.getResult() == Merchant.RISK.MEDIUM) {
                    mediumRiskCount++;
                    if (mediumRiskCount > 1) {
                        aggregatedRisk = Merchant.RISK.HIGH;
                    } else {
                        aggregatedRisk = Merchant.RISK.MEDIUM;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // TODO: make have finally clause with result that adds some error-indicative result
            // to the RSER so we don't leave transaction in some pending-resolution state.
        }

        // Aggregate the individual rule results into a RuleSetEvaluationResult.

        RuleSetEvaluationResult rser = new RuleSetEvaluationResult(carrier, getQualifiedName());
        rser.setResult(aggregatedRisk);

        // Merchant rules are using Merchant.RISK as outcome
        if (aggregatedRisk == Merchant.RISK.HIGH)
            rser.setRuleSetOutcome(TransactionFinalStatus.RejectedForFraud, "Rated as high risk by merchant rules");
        else
            rser.setRuleSetOutcome(TransactionFinalStatus.Approved);

        return rser;
    }

    // TODO: push up to AbstractRuleSet along with HazelcastInstanceAware interface and field
    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        //System.out.println("Setting HazelcastInstance for MerchantRuleSet");
        this.hazelcast = hazelcastInstance;
        // Some, but not all, rules need HazelcastInstance, they need to be marked acccordingly
        for (Rule r : getRules()) {
            if (r instanceof HazelcastInstanceAware) {
                ((HazelcastInstanceAware) r).setHazelcastInstance(hazelcastInstance);
            }
        }
    }
}
