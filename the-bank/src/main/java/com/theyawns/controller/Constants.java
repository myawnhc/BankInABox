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

package com.theyawns.controller;

public class Constants {

    // IMDG Data structures
    public static final String MAP_PREAUTH         = "preAuth";
    public static final String MAP_ACCOUNT         = "accountMap";
    public static final String MAP_MERCHANT        = "merchantMap";
    public static final String MAP_APPROVED        = "approved";
    public static final String MAP_REJECTED_CREDIT = "rejectedForCredit";
    public static final String MAP_REJECTED_FRAUD  = "rejectedForFraud";
    public static final String MAP_RESULTS         = "resultMap";
    public static final String MAP_RS_ROUTING      = "ruleSetRoutingMap";

    // Maps held in Jet
    public static final String MAP_WRAPPED_ITEMS   = "wrappedItemsMap";
    public static final String MAP_COMPLETIONS     = "completionsMap";
    // Jet also keeps a resultMap using the same constant defined for the IMDG result map

    public static final String TOPIC_PREAUTH       = "preAuthTopic";   // Raw transactions
    public static final String TOPIC_AUTH_ITEMS    = "authItemsTopic"; // wrapped with ItemCarrier
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
