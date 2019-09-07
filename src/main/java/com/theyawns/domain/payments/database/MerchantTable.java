package com.theyawns.domain.payments.database;

import com.hazelcast.core.MapLoader;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.launcher.BankInABoxProperties;

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
            "create table MERCHANT ( " +
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
            "select id, name, reputation, avg_txn_amount, location from MERCHANT where id = ?";

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
    public Merchant load(String s) {
        if (conn == null)
            establishConnection();
        return readFromDatabase(s);
    }

    @Override
    public Map<String, Merchant> loadAll(Collection<String> collection) {
        log.info("MerchantTable.loadAll() with " + collection.size() + " keys");
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
        log.info("MapLoader.loadAllKeys() on merchantMap");
        if (conn == null)
            establishConnection();
        int size = BankInABoxProperties.MERCHANT_COUNT;
        List<String> allKeys = new ArrayList<>(size);
        try (Statement statement = conn.createStatement() ) {
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
}
