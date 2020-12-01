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

package com.theyawns.controller;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.banking.Account;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionEvaluationResult;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.io.Serializable;

public class IDSFactory implements DataSerializableFactory, Serializable {

    @Override
    public IdentifiedDataSerializable create(int id) {
        switch(id) {
            case Constants.IDS_TRANSACTION_ID: return new Transaction();
//            case Constants.IDS_TXN_WITH_ACCT:  return new TransactionWithAccountInfo();
//            case Constants.IDS_TXN_WITH_RULES: return new TransactionWithRules();
//            case Constants.IDS_LATENCY_METRIC: return new LatencyMetric();
            case Constants.IDS_ACCOUNT_ID:     return new Account();
            case Constants.IDS_MERCHANT_ID:    return new Merchant();
//            case Constants.IDS_LOCATION:       return new Location();
            case Constants.IDS_TXN_EVAL_RESULT: return new TransactionEvaluationResult();
            case Constants.IDS_RULE_EVAL_RESULT: return new RuleEvaluationResult();
            case Constants.IDS_RULESET_EVAL_RESULT: return new RuleSetEvaluationResult();
            case Constants.IDS_CARRIER: return new ItemCarrier();
        }
        throw new IllegalArgumentException("Missing constructor invocation for type in IDSFactory");
    }
}