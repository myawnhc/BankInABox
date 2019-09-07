package com.theyawns.domain.payments.database;

import com.hazelcast.core.MapLoader;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.domain.payments.*;
import com.theyawns.launcher.BankInABoxProperties;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;

public class TransactionTable extends AbstractTable
                           implements MapLoader<String, Transaction> {

    private final static ILogger log = Logger.getLogger(TransactionTable.class);

    private static final DecimalFormat merchantFormat = new DecimalFormat("00000000");       // 8 digit
    private static final DecimalFormat accountFormat  = new DecimalFormat( "0000000000");    // 10 digit
    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    // Index positions
    private static final int ID = 1;
    private static final int ACCT_NUMBER = 2;
    private static final int MERCHANT_ID = 3;
    private static final int AMOUNT = 4;
    private static final int LOCATION = 5;

    private static final String createTableString =
            "create table TRANSACTION ( " +
                    "id             char(14)     not null, " +
                    "acct_number    varchar(10), " +         // foreign key but not marking as such
                    "merchant_id    varchar(8), " +          // foreign key but not marking as such
                    "amount         float, " +
                    "location       varchar(10), " +         // geohash
                    "primary key (id) " +
                    ")";

    private static final String insertTemplate =
            "insert into transaction (id, acct_number, merchant_id, amount, location) " +
                    " values (?, ?, ?, ?, ?)";

    private static final String selectTemplate =
            "select id, acct_number, merchant_id, amount, location from TRANSACTION where id = ?";

    private static final String selectKeysString = "select id from transaction";

    private PreparedStatement createStatement;
    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;

    private Transaction generate(int id) {
        try {
            Transaction t = new Transaction(txnFormat.format(id));
            return t;
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);

            return null;
        }
    }

    public int generateAndStoreMultiple(int count) {
        Random acctRandom = new Random(123);
        Random merchantRandom = new Random(456);
        //AccountTable aTable = new AccountTable();
        MerchantTable mTable = new MerchantTable();
        for (int i = 0; i < count; i++) {
            Transaction t = new Transaction(txnFormat.format(i));
            int acctNum = acctRandom.nextInt(BankInABoxProperties.ACCOUNT_COUNT);
            String acctId = accountFormat.format(acctNum);
            int merchantNum = merchantRandom.nextInt(BankInABoxProperties.MERCHANT_COUNT);
            String merchantId = merchantFormat.format(merchantNum);
            t.setAccountNumber(acctId);
            t.setMerchantId(merchantId);
            Merchant merchant = mTable.load(merchantId);
            Double txnAmount = merchant.getRandomTransactionAmount(); // Distributed normally around avg txn amount
            t.setAmount(txnAmount);
            // TODO: set a geohash location value
            t.setLocation("");
            writeToDatabase(t);
        }
        return count;
    }

    public synchronized void createTransactionTable()  {
        try {
            createStatement = conn.prepareStatement(createTableString);
            createStatement.executeUpdate();
            createStatement.close();
            log.info("Created Transaction table ");
        } catch (SQLException se) {
            se.printStackTrace();
            System.exit(-1);
        }
    }

    public synchronized void writeToDatabase(Transaction t) {
        try {
            if (insertStatement == null) {
                insertStatement = conn.prepareStatement(insertTemplate);
            }
            insertStatement.setString(ID, t.getID());
            insertStatement.setString(ACCT_NUMBER, t.getAccountNumber());
            insertStatement.setString(MERCHANT_ID, t.getMerchantId());
            insertStatement.setDouble(AMOUNT, t.getAmount());
            insertStatement.setString(LOCATION, t.getLocation());
            insertStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public synchronized Transaction readFromDatabase(String id) {
        if (id == null) {
            log.warning("TransactionTable.readFromDatabase(): Passed null id, returning null");
            return null;
        }
        try {
            if (selectStatement == null) {
                selectStatement = conn.prepareStatement(selectTemplate);
            }
            selectStatement.setString(ID, id);
            //log.info("readFromDatabase: " + selectStatement.toString());
            ResultSet rs = selectStatement.executeQuery();
            Transaction t = new Transaction();
            if (rs == null) {
                log.warning("TransactionTable.readFromDatabase(): Null resultSet trying to read Transaction " + id);
                return null;
            }
            while (rs.next()) {
                t.setID(rs.getString(ID));
                t.setAccountNumber(rs.getString(ACCT_NUMBER));
                t.setMerchantId(rs.getString(MERCHANT_ID));
                t.setAmount(rs.getDouble(AMOUNT));
                t.setLocation(rs.getString(LOCATION));
            }
            //System.out.println(t);
            return t;
        } catch (SQLException e) {
            log.info("Error in " + selectStatement.toString() + " --> " + e.getMessage());
            //e.printStackTrace();
            //System.exit(-1);
            return null;
        }
    }

    // MapLoader interface

    @Override
    public Transaction load(String s) {
        //log.info("TransactionTable.load " + s);
        if (conn == null)
            establishConnection();
        return readFromDatabase(s);
    }

    @Override
    public Map<String, Transaction> loadAll(Collection<String> collection) {
        log.info("TransactionTable.loadAll() with " + collection.size() + " keys");
        if (conn == null)
            establishConnection();
        Map<String,Transaction> results = new HashMap<>(collection.size());
        // NOTE: parallelStream here leads to SQLException in read database, so drop back here until we
        // can make that threadsafe. (Trying to use shared PreparedStatement with different parameters)
        collection.stream().forEach((String key) -> {
            Transaction t = load(key);
            results.put(key, t);
        });
        if (results.size() != collection.size()) {
            log.warning(("TransactionTable loadAll: " + collection.size() + " items requested but only " + results.size() + " were found."));
        }
        return results;
    }

    @Override
    public synchronized Iterable<String> loadAllKeys() {
        log.info("TransactionTable.loadAllKeys()");
        if (conn == null)
            establishConnection();
        int size = BankInABoxProperties.TRANSACTION_COUNT;
        List<String> allKeys = new ArrayList<>(size);
        try (Statement statement = conn.createStatement() ) {
            ResultSet rs = statement.executeQuery(selectKeysString);
            while (rs.next()) {
                String transactionID = rs.getString(ID);
                allKeys.add(transactionID);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        log.info("MapLoader.loadAllKeys() on Transaction table returning " + allKeys.size() + " keys");
        return allKeys;
    }
}
