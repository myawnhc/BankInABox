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

import com.hazelcast.map.EntryProcessor;
import com.theyawns.banking.Account;
import com.theyawns.banking.Transaction;

import java.io.Serializable;
import java.util.Map;

public class PaymentRulesEP implements EntryProcessor<String, Transaction, Boolean>, Serializable {

    private Account account;

    public void setAccount(Account acct) { this.account = acct; }

    @Override
    public Boolean process(Map.Entry<String, Transaction> entry) {
        Transaction txn = entry.getValue();
        //System.out.println("Processing PaymentRulesEP for " + txn.getID());

        // Run a credit limit check
        CreditLimitCheck clc = new CreditLimitCheck();
        clc.setAccount(account);
        boolean clcOK = clc.process(txn);

        // TODO: anticipate having a second rule here on account status

        // Aggregate the results of the rules to get overall payment status
        // Since we only have one rule implemented, this is trivial
        txn.setPaymentResult(clcOK);
        //System.out.println("Payment rules complete, end IMDG processing and record");


        //txn.processingTime.stop();
        entry.setValue(txn);  // update the transaction in the map with the result
        // Record now does TPS only, so call only at E2E completion
        //PerfMonitor.getInstance().recordTransaction("IMDG", txn);
        return clcOK;
    }

//    @Override
//    public EntryBackupProcessor<String, Transaction> getBackupProcessor() {
//        return null;
//    }
}
