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
import com.theyawns.banking.Merchant;
import com.theyawns.controller.launcher.BankInABoxProperties;

import java.sql.*;

import java.text.DecimalFormat;
import java.util.*;


// Generate Merchants
public class MerchantTable extends AbstractTable
                        implements MapLoader<String, Merchant> {

    private final static ILogger log = Logger.getLogger(MerchantTable.class);

    private static final DecimalFormat merchantFormat = new DecimalFormat("00000000");       // 8 digit

    // Index positions
    private static final int ID = 1;
    private static final int NAME = 2;
    private static final int REPUTATION = 3;
    private static final int AVG_TXN_AMOUNT = 4;
    private static final int LOCATION = 5;

    private static final String createTableString =
            "create table merchant ( " +
                    "id             char(8)     not null, " +
                    "name           varchar(32), " +
                    "reputation     smallint, " +
                    "avg_txn_amount float, " +
                    "location       varchar(10), " +         // geohash
                    "primary key (id) " +
                    ")";

    private static final String insertTemplate =
            "insert into merchant (id, name, reputation, avg_txn_amount, location) " +
                    " values (?, ?, ?, ?, ?)";

    private static final String selectTemplate =
            "select id, name, reputation, avg_txn_amount, location from merchant where id = ?";

    private static final String selectKeysString = "select id from merchant";

    private PreparedStatement createStatement;
    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;

    private Merchant generate(int id) {
        try {
            Merchant m = new Merchant(merchantFormat.format(id));
            return m;
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);

            return null;
        }
    }

    public int generateAndStoreMultiple(int count) {
        for (int i = 0; i < count; i++) {
            Merchant m = generate(i);
            writeToDatabase(m);
        }
        return count;
    }

    public synchronized void createMerchantTable()  {
        try {
            createStatement = conn.prepareStatement(createTableString);
            createStatement.executeUpdate();
            createStatement.close();
            log.info("Created Merchant table ");
        } catch (SQLException se) {
            se.printStackTrace();
            System.exit(-1);
        }
    }

    public synchronized void writeToDatabase(Merchant m) {
        try {
            if (insertStatement == null) {
                insertStatement = conn.prepareStatement(insertTemplate);
            }
            insertStatement.setString(ID, m.getMerchantId());
            insertStatement.setString(NAME, m.getMerchantName());
            insertStatement.setInt(REPUTATION, m.getReputation());
            insertStatement.setDouble(AVG_TXN_AMOUNT, m.getAvgTxnAmount());
            insertStatement.setString(LOCATION, m.getLocation());
            insertStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private synchronized Merchant readFromDatabase(String id) {
        //log.info("MerchantTable.readFromDatabase(" + id + ")");
        if (id == null) {
            log.warning("MerchantTable.readFromDatabase(): Passed null id, returning null");
            return null;
        }
        try {
            if (selectStatement == null) {
                selectStatement = conn.prepareStatement(selectTemplate);
            }
            selectStatement.setString(ID, id);
            //log.info("readFromDatabase: " + selectStatement.toString());
            ResultSet rs = selectStatement.executeQuery();
            Merchant m = new Merchant();
            if (rs == null) {
                log.warning("MerchantTable.readFromDatabase(): Null resultSet trying to read Merchant " + id);
                return null;
            }
            while (rs.next()) {
                m.setMerchantID(rs.getString(ID));
                m.setMerchantName(rs.getString(NAME));
                m.setReputation(rs.getInt(REPUTATION));
                m.setAvgTxnAmount(rs.getDouble(AVG_TXN_AMOUNT));
                m.setLocation(rs.getString(LOCATION));
            }
            return m;
        } catch (SQLException e) {
            log.severe("Error in " + selectStatement.toString() + " --> " + e.getMessage());
            //e.printStackTrace();
            //System.exit(-1);
            return null;
        }
    }

    // MapLoader interface

    @Override
    public synchronized Merchant load(String s) {
        //log.info("MerchantTable.load(" + s + ")");
        if (conn == null)
            establishConnection();
        return readFromDatabase(s);
    }

    @Override
    public synchronized Map<String, Merchant> loadAll(Collection<String> collection) {
        //log.info("MerchantTable.loadAll() with " + collection.size() + " keys");
        if (conn == null)
            establishConnection();
        Map<String,Merchant> results = new HashMap<>(collection.size());
        // NOTE: parallelStream here leads to SQLException in read database, so drop back here until we
        // can make that threadsafe. (Trying to use shared PreparedStatement with different parameters)
        collection.stream().forEach((String key) -> {
            Merchant m = load(key);
            results.put(key, m);
        });

        return results;
    }

    @Override
    public synchronized Iterable<String> loadAllKeys() {
        log.info("MerchantTable.loadAllKeys()");
        if (conn == null)
            establishConnection();
        int size = BankInABoxProperties.MERCHANT_COUNT;
        List<String> allKeys = new ArrayList<>(size);
        try (Statement statement = conn.createStatement() ) {
            log.info("Executing select query");
            ResultSet rs = statement.executeQuery(selectKeysString);
            while (rs.next()) {
                String merchantID = rs.getString(ID);
                allKeys.add(merchantID);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        log.info("MapLoader.loadAllKeys() on MERCHANT table returning " + allKeys.size() + " keys");
        return allKeys;
    }

    // When bypassing MapLoader we prefer a list to an iterator
    public List<String> allKeys() {
        Iterable<String> keys = loadAllKeys();
        ArrayList<String> list = new ArrayList<>();
        keys.iterator().forEachRemaining(list::add);
        return list;
    }
}
