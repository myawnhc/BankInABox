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


import com.theyawns.banking.Account;
import com.theyawns.banking.Transaction;

import java.io.Serializable;

public class CreditLimitCheck implements Serializable {

    private Account account;

    public void setAccount(Account acct) {
        this.account = acct;
    }

    public Boolean process(Transaction txn) {
        if (account == null) {
            throw new IllegalStateException("Account must be set prior to invoking EntryProcessor");
        }

        boolean approved = true;
        double projectedBalance = account.getBalance() + txn.getAmount();
        if (projectedBalance > account.getCreditLimit())
            approved = false;

        return approved;
    }


}
