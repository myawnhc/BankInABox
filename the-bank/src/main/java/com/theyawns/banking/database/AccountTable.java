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

package com.theyawns.banking.database;

import com.hazelcast.map.MapLoader;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.banking.Account;
import com.theyawns.controller.launcher.BankInABoxProperties;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;

public class AccountTable extends AbstractTable
        implements MapLoader<String, Account> {

    private final static ILogger log = Logger.getLogger(AccountTable.class);

    private static final DecimalFormat accountFormat  = new DecimalFormat( "0000000000");    // 10 digit

    protected String accountNumber;
    private Double creditLimit;
    private Double balance;
    private Account.AccountStatus status;
    //private Location lastReportedLocation;
    private String lastReportedLocation;

    // Index positions
    private static final int ACCT_NUMBER = 1;
    private static final int CREDIT_LIMIT = 2;
    private static final int BALANCE = 3;
    private static final int ACCT_STATUS = 4;
    private static final int LOCATION = 5;

    private static final String createTableString =
            "create table account ( " +
                    "acct_number    char(10)     not null, " +
                    "credit_limit   float, " +
                    "balance        float, " +
                    "acct_status    smallint, " +
                    "location       varchar(10), " +         // geohash
                    "primary key (acct_number) " +
                    ")";

    private static final String insertTemplate =
            "insert into account (acct_number, credit_limit, balance, acct_status, location) " +
                    " values (?, ?, ?, ?, ?)";

    private static final String selectTemplate =
            "select acct_number, credit_limit, balance, acct_status, location from account where acct_number = ?";

    private static final String selectKeysString = "select acct_number from account";

    private PreparedStatement createStatement;
    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;

    private Account generate(int id) {
        try {
            Account a = new Account(accountFormat.format(id));
            return a;
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);

            return null;
        }
    }

    public int generateAndStoreMultiple(int count) {
        for (int i = 0; i < count; i++) {
            Account a = generate(i);
            writeToDatabase(a);
        }
        return count;
    }

    public synchronized void createAccountTable()  {
        try {
            createStatement = conn.prepareStatement(createTableString);
            createStatement.executeUpdate();
            createStatement.close();
            log.info("Created Account table ");
        } catch (SQLException se) {
            se.printStackTrace();
            System.exit(-1);
        }
    }

    public synchronized void writeToDatabase(Account a) {
        try {
            if (insertStatement == null) {
                insertStatement = conn.prepareStatement(insertTemplate);
            }
            insertStatement.setString(ACCT_NUMBER, a.getAccountNumber());
            insertStatement.setDouble(CREDIT_LIMIT, a.getCreditLimit());
            insertStatement.setDouble(BALANCE, a.getBalance());
            insertStatement.setInt(ACCT_STATUS, a.getAccountStatus().ordinal());
            insertStatement.setString(LOCATION, a.getLastReportedLocation());
            insertStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public synchronized Account readFromDatabase(String id) {
        if (id == null) {
            log.warning("AccountTable.readFromDatabase(): Passed null id, returning null");
            return null;
        }
        try {
            if (selectStatement == null) {
                selectStatement = conn.prepareStatement(selectTemplate);
            }
            selectStatement.setString(ACCT_NUMBER, id);
            //log.info("readFromDatabase: " + selectStatement.toString());
            ResultSet rs = selectStatement.executeQuery();
            Account a = new Account();
            if (rs == null) {
                log.warning("AccountTable.readFromDatabase(): Null resultSet trying to read Account " + id);
                return null;
            }
            while (rs.next()) {
                a.setAccountNumber(rs.getString(ACCT_NUMBER));
                a.setCreditLimit(rs.getDouble(CREDIT_LIMIT));
                a.setBalance(rs.getDouble(BALANCE));
                int statusValue = rs.getInt(ACCT_STATUS);
                a.setAccountStatus(Account.AccountStatus.values()[statusValue]);
                a.setLastReportedLocation(rs.getString(LOCATION));
            }
            return a;
        } catch (SQLException e) {
            log.info("Error in " + selectStatement.toString() + " --> " + e.getMessage());
            //e.printStackTrace();
            //System.exit(-1);
            return null;
        }
    }

    // MapLoader interface

    @Override
    public synchronized Account load(String s) {
        if (conn == null)
            establishConnection();
        return readFromDatabase(s);
    }

    @Override
    public synchronized Map<String, Account> loadAll(Collection<String> collection) {
        //log.info("AccountTable.loadAll() with " + collection.size() + " keys");
        if (conn == null)
            establishConnection();
        Map<String,Account> results = new HashMap<>(collection.size());
        // NOTE: parallelStream here leads to SQLException in read database, so drop back here until we
        // can make that threadsafe. (Trying to use shared PreparedStatement with different parameters)
        collection.stream().forEach((String key) -> {
            Account a = load(key);
            results.put(key, a);
        });

        return results;
    }

    @Override
    public synchronized Iterable<String> loadAllKeys() {
        //log.info("loadAllKeys() on accountMap");
        if (conn == null)
            establishConnection();
        int size = BankInABoxProperties.ACCOUNT_COUNT;
        List<String> allKeys = new ArrayList<>(size);
        try (Statement statement = conn.createStatement() ) {
            ResultSet rs = statement.executeQuery(selectKeysString);
            while (rs.next()) {
                String accountNum = rs.getString(ACCT_NUMBER);
                allKeys.add(accountNum);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        log.info("MapLoader.loadAllKeys() on ACCOUNT table returning " + allKeys.size() + " keys");
        return allKeys;
    }

    public List<String> allKeys() {
        Iterable<String> keys = loadAllKeys();
        ArrayList<String> list = new ArrayList<>();
        keys.iterator().forEachRemaining(list::add);
        return list;
    }
}
