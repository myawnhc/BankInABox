package com.theyawns.domain.payments;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.theyawns.Constants;

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
