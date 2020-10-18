package com.theyawns.banking.fraud.fdengine.imdgimpl.executors;

import java.io.Serializable;

public class ExecutorStatusMapKey implements Serializable {
    private String executorID;   // Aggregator or RuleSetExecutor name
    private String memberID;     // Member UUID

    public ExecutorStatusMapKey(String executor, String member) {
        this.executorID = executor;
        this.memberID = member;
    }

    public String toString() {
        return "[Exec " + executorID + " Member " + memberID + "]";
    }
}
