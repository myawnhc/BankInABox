package com.theyawns;

public class Constants {

    // IMDG Data structures
    public static final String MAP_PREAUTH         = "preAuth";
    public static final String MAP_ACCOUNT         = "accountMap";
    public static final String MAP_MERCHANT        = "merchantMap";
    public static final String MAP_RESULT          = "resultMap";
    public static final String MAP_APPROVED        = "approved";
    public static final String MAP_REJECTED_CREDIT = "rejectedForCredit";
    public static final String MAP_REJECTED_FRAUD  = "rejectedForFraud";

    public static final String MAP_PPFD_RESULTS    = "ppfdResultMap";

    public static final String TOPIC_PREAUTH       = "preAuthTopic";
    public static final String QUEUE_LOCATION      = "locationRulesQ";
    public static final String QUEUE_MERCHANT      = "merchantRulesQ";
    public static final String QUEUE_CREDITRULES   = "creditRulesQ";

    public static final String QUEUE_COMPLETIONS   = "resultsCompleteQ";

    // Added these for debugging purposes but keeping them as they can be
    // generally useful
    public static final String MAP_EXECUTOR_STATUS = "ruleSetExecutorStatus";
    // this is broken down for each processing step, vs. end-to-end in PNCounters
    public static final String MAP_LATENCY = "latencyMap";

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

    // Location based rules.  Might move these constants into the Rule class
    // instead of having to detail them here.
//    public static final String PN_COUNT_R4F_LOC1   = "pnCounterRej4Fraud_Loc_1";
//    public static final String PN_COUNT_R4F_LOC2   = "pnCounterRej4Fraud_Loc_2";
//    public static final String PN_COUNT_R4F_LOC3   = "pnCounterRej4Fraud_Loc_3";
//    public static final String PN_COUNT_R4F_LOC4   = "pnCounterRej4Fraud_Loc_4";
//    public static final String PN_COUNT_R4F_LOC5   = "pnCounterRej4Fraud_Loc_5";


    // IdentifiedDataSerializable
    public static final int IDS_FACTORY_ID = 101;

    public static final int IDS_ACCOUNT_ID     = 201;
    public static final int IDS_LATENCY_METRIC = 202;
    public static final int IDS_LOCATION       = 203;
    public static final int IDS_MERCHANT_ID    = 204;
    public static final int IDS_TRANSACTION_ID = 205;
    public static final int IDS_TXN_WITH_RULES = 206;
    public static final int IDS_TXN_WITH_ACCT  = 207;
}
