package com.theyawns.domain.payments;

import java.util.Set;

// Transaction enriched with the list of rules that apply to it (set at ingest time)
public class TransactionWithRules extends Transaction {

    Set<String> rules;
    private long ingestTimeInMillis;

    public TransactionWithRules(Transaction txn, Set<String> rules) {
        super(Integer.parseInt(txn.getID()));
        this.rules = rules;
        this.ingestTimeInMillis = System.currentTimeMillis();

        //System.out.println("Transaction enriched with " + rules.size() + " rules");
    }

    public long getIngestTime() {
        return ingestTimeInMillis;
    }
}
