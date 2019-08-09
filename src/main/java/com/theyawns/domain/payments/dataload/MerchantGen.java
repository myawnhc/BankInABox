package com.theyawns.domain.payments.dataload;

import com.theyawns.domain.payments.Merchant;
import com.theyawns.launcher.BankInABoxProperties;

import java.text.DecimalFormat;

// Generate Merchants
public class MerchantGen {

    private static final DecimalFormat merchantFormat = new DecimalFormat("00000000");       // 8 digit
    // TODO: SQL Insert
    //   INSERT INTO MERCHANT ( field, field ) VALUES ( val, val );
    // TODO: SQL SELECT
    //   SELECT (ID, NAME, REPUTATION, AVG_TXN_AMOUNT, LOCATION) FROM MERCHANT WHERE ID = ?;

    // create database bankinabox;
    // create table merchant (
    //      id               char(8)       not null,
    //      name             varchar(32),
    //      reputation       smallint,         // range 1-10
    //      avg_txn_amount   float,
    //      location         varchar(10),   // geohash
    //      primary key ( id ),
    // );


    public Merchant generate(int id) {
        Merchant m = new Merchant(merchantFormat.format(id));
        return m;
    }

    public void run() {
        for (int i = 0; i< BankInABoxProperties.MERCHANT_COUNT; i++) {
            Merchant m = generate(i);
            writeToDatabase(m);
        }
    }

    public void writeToDatabase(Merchant m) {

    }

    public void writeToImap() {}
    public void writeToStream() {}

}
