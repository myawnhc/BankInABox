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

package com.theyawns.banking.fraud.fdengine.jetimpl.holding;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Account;

import java.io.IOException;
import java.io.Serializable;

public class TransactionWithAccountInfo extends TransactionWithRules
        implements /*IdentifiedDataSerializable,*/ Serializable {

    protected Account enrichedAccount;

    public TransactionWithAccountInfo(TransactionWithRules twr) {
        super(twr, twr.rules);
    }

    // for serializer use only
    public TransactionWithAccountInfo() {}

    public Account getAccountInfo() { return enrichedAccount; }
    public void setAccountInfo(Account account) { enrichedAccount = account; }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_TXN_WITH_ACCT;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        super.writeData(objectDataOutput);
        // TODO: more efficient to write just account id and fetch it at readObject
        objectDataOutput.writeObject(enrichedAccount);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        super.readData(objectDataInput);
        enrichedAccount = objectDataInput.readObject(Account.class);
    }
}
