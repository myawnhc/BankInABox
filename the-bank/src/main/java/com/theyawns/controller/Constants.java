package com.theyawns.controller;

public class Constants {

    // IMDG Data structures
    public static final String MAP_PREAUTH         = "preAuth";
    public static final String MAP_ACCOUNT         = "accountMap";
    public static final String MAP_MERCHANT        = "merchantMap";
    public static final String MAP_RESULT          = "resultMap";
    public static final String MAP_APPROVED        = "approved";
    public static final String MAP_REJECTED_CREDIT = "rejectedForCredit";
    public static final String MAP_REJECTED_FRAUD  = "rejectedForFraud";
    public static final String MAP_RESULTS         = "resultMap";
    public static final String MAP_RS_ROUTING      = "ruleSetRoutingMap";

    public static final String TOPIC_PREAUTH       = "preAuthTopic";
    public static final String QUEUE_LOCATION      = "locationRulesQ";
    public static final String QUEUE_MERCHANT      = "merchantRulesQ";
    public static final String QUEUE_CREDITRULES   = "creditRulesQ";

    public static final String QUEUE_COMPLETIONS   = "resultsCompleteQ";

    // Added  for debugging purposes
    public static final String MAP_EXECUTOR_STATUS = "ruleSetExecutorStatus";

    public static final String PN_COUNT_WALMART    = "pnCounterWalmart";
    public static final String PN_COUNT_AMAZON     = "pnCounterAmazon";
    public static final String PN_COUNT_APPROVED   = "pnCounterApproved";
    public static final String PN_COUNT_REJ_FRAUD  = "pnCounterRejectedForFraud";
    public static final String PN_COUNT_REJ_CREDIT = "pnCounterRejectedForCredit";
    public static final String PN_COUNT_LOADED_TO_PREAUTH = "pnCounterLoadedToPreAuth";

    public static final String PN_COUNT_TOTAL_LATENCY = "pnCounterTotalLatency"; // in nanoseconds
    public static final String PN_COUNT_LATENCY_ITEMS = "pnCounterLatencyItems"; // divisor for above
    // may also add bucketed counters to do a histogram view of latency, bucket cutoffs should be configurable
    //     thinking initial setup might be <=10ms, <=25ms, <=50ms, <=100ms, >100ms

    // IdentifiedDataSerializable
    public static final int IDS_FACTORY_ID = 101;

    public static final int IDS_ACCOUNT_ID     = 201;
    public static final int IDS_LATENCY_METRIC = 202; // UNUSED
    public static final int IDS_LOCATION       = 203;
    public static final int IDS_MERCHANT_ID    = 204;
    public static final int IDS_TRANSACTION_ID = 205;
    public static final int IDS_TXN_WITH_RULES = 206; // UNUSED
    public static final int IDS_TXN_WITH_ACCT  = 207; // UNUSED
    public static final int IDS_RULE_EVAL_RESULT  = 208;
    public static final int IDS_RULESET_EVAL_RESULT = 209;
    public static final int IDS_CARRIER = 210;
    public static final int IDS_TXN_EVAL_RESULT = 211; // UNUSED
}
