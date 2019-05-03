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

    // IdentifiedDataSerializable
    public static final int IDS_FACTORY_ID = 101;

    public static final int IDS_ACCOUNT_ID     = 201;
    public static final int IDS_MERCHANT_ID    = IDS_ACCOUNT_ID + 1;
    public static final int IDS_TRANSACTION_ID = IDS_MERCHANT_ID + 1;
}
