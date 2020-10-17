package com.theyawns.banking.fraud.fdengine.jetimpl.holding;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Transaction enriched with the list of rules that apply to it (set at ingest time)
public class TransactionWithRules extends Transaction
        implements /*IdentifiedDataSerializable,*/ Serializable {

    protected Set<String> rules;
    //private long ingestTimeInMillis;

    public TransactionWithRules(Transaction txn, Set<String> rules) {
        super(txn);
        this.rules = rules;
        //this.ingestTimeInMillis = System.currentTimeMillis();

        //System.out.println("Transaction enriched with " + rules.size() + " rules, account " + getAccountNumber());
    }

    // Only for serializer
    public TransactionWithRules() {}

//    //public long getIngestTime() {
//        return ingestTimeInMillis;
//    }
    public int getExpectedRuleCount() { return rules.size(); }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_TXN_WITH_RULES;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        super.writeData(objectDataOutput);
        objectDataOutput.writeUTFArray(rules.toArray(new String[rules.size()]));
        //objectDataOutput.writeLong(ingestTimeInMillis);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        super.readData(objectDataInput);
        rules = new HashSet<>();
        rules.addAll(Arrays.asList(objectDataInput.readUTFArray()));
        //ingestTimeInMillis = objectDataInput.readLong();
    }
}
