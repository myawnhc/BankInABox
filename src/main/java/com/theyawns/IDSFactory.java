package com.theyawns;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class IDSFactory implements DataSerializableFactory {

    @Override
    public IdentifiedDataSerializable create(int id) {
        switch(id) {
            //case Constants.IDS_TRANSACTION_ID: return new Transaction();

            //case RESULT_ID: return new Result();

            // FUTURE: can only do this if we have a zero-arg constructor.
            //case ENTRY_PROCESSOR_ID: return new FraudDetectionEntryProcessor();
        }
        throw new IllegalArgumentException();
    }
}