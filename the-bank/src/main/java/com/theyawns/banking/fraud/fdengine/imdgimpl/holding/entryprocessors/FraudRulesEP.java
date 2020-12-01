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

package com.theyawns.banking.fraud.fdengine.imdgimpl.holding.entryprocessors;

//import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;

import java.io.Serializable;
import java.util.Map;

public class FraudRulesEP implements EntryProcessor<String, Transaction, Integer>, Serializable {

    private Merchant merchant;

    public void setMerchant(Merchant m) { this.merchant = m; }

    @Override
    public Integer process(Map.Entry<String, Transaction> entry) {
        //System.out.println("Processing FraudRulesEP");
        if (merchant == null) {
            throw new IllegalStateException("Merchant must be set prior to invoking EntryProcessor");
        }
        Transaction txn = entry.getValue();

        MerchantTxnAvgCheck mtac = new MerchantTxnAvgCheck();
        mtac.setMerchant(merchant);

        Merchant.RISK txnAmountRisk = mtac.process(txn);


        // TODO: anticipate having several more rules here

        // Aggregate the results of the rules to get overall payment status
        // Since we only have one rule implemented, this is trivial
        // We could also weight results differently here if desired
        int fraudRisk = 0;
        switch (txnAmountRisk) {
            case HIGH: fraudRisk = 90; break;
            case MEDIUM: fraudRisk = 50; break;
            case LOW: fraudRisk = 5; break;
        }

        txn.setFraudResult(fraudRisk);
        entry.setValue(txn);  // update the transaction in the map with the result
        //System.out.println("Fraud rules complete");
        return fraudRisk;
    }

//    @Override
//    public EntryBackupProcessor<String, Transaction> getBackupProcessor() {
//        return null;
//    }
}
