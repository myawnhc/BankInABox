package com.theyawns.domain.payments.database;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.domain.payments.TransactionKey;
import com.theyawns.launcher.BankInABoxProperties;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// No longer a legal MapLoader implementation, as our SQL key type
// (String) doesn't match our IMap key type (TransactionKey), forcing us to
// change method signatures in ways incompatible with MapLoader
public class TransactionTable extends AbstractTable
                          // implements MapLoader<String, Transaction>
{

    private final static ILogger log = Logger.getLogger(TransactionTable.class);

    private static final DecimalFormat merchantFormat = new DecimalFormat("00000000");       // 8 digit
    private static final DecimalFormat accountFormat  = new DecimalFormat( "0000000000");    // 10 digit
    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    private static int numberOfEntries;

    // Index positions
    private static final int ID = 1;
    private static final int ACCT_NUMBER = 2;
    private static final int MERCHANT_ID = 3;
    private static final int AMOUNT = 4;
    private static final int LOCATION = 5;

    private static final String createTableString =
            "create table transaction ( " +
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
            "select id, acct_number, merchant_id, amount, location from transaction where id = ?";

    private static final String selectKeysString = "select id from transaction";

    private static final String tableSizeQuery = "select count(*) from transaction";

    private PreparedStatement createStatement;
    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;
    private PreparedStatement tableSizeQueryStatement;

    public int generateAndStoreMultiple(int count) {
        Random acctRandom = new Random(123);
        Random merchantRandom = new Random(456);
        MerchantTable mTable = new MerchantTable();
        for (int i = 0; i < count; i++) {
            int acctNum = acctRandom.nextInt(BankInABoxProperties.ACCOUNT_COUNT);
            String acctId = accountFormat.format(acctNum);
            String txnID = txnFormat.format(i);
            //TransactionKey key = new TransactionKey(txnID, acctId);
            Transaction t = new Transaction(txnID, acctId);
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
            insertStatement.setString(ID, t.getItemID());
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

    private static int passesThroughTransactionFile = 0;
    private static int offset = 0;

    public synchronized Transaction readFromDatabase(String id) {

        // This should only happen once.
        if (numberOfEntries == 0) {
            //log.info("TransactionTable.readFromDatabase: Getting Txn table size for offset calculations");
            numberOfEntries = getTableSize();
        }
        String originalId = id;

        try {
            if (id == null) {
                log.warning("TransactionTable.readFromDatabase(): Passed null id, returning null");
                return null;
            }

            int txnNum = Integer.parseInt(id);
            //log.info("id, txnnum, numentries " + id + " " + txnNum + " " + numberOfEntries);

            // First transaction id is one; so offset of 0 indicates we've hit last transaction and must roll over
            int txnOffset = txnNum % numberOfEntries;
            if (txnNum > 0 && txnOffset == 0) {
                passesThroughTransactionFile++;
                offset += numberOfEntries;
                log.info("Finished pass " + passesThroughTransactionFile + " through transaction file, offset now " + offset);
            }

            if (passesThroughTransactionFile > 0) {
                txnNum -= offset;
                id = txnFormat.format(txnNum);
            }

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
            if (rs.first()) {
                // We set the requested ID, rather than the ID from the resultset, due to the fact that we'll
                // rewind and reuse the dataset multiple times if the demo is long-running.
                t.setItemID(originalId);
                t.setAccountNumber(rs.getString(ACCT_NUMBER));
                t.setTransactionKey(new TransactionKey(originalId, t.getAccountNumber()));
                t.setMerchantId(rs.getString(MERCHANT_ID));
                t.setAmount(rs.getDouble(AMOUNT));
                t.setLocation(rs.getString(LOCATION));
            } else {
                log.warning("TransactionTable.readFromDatabase: no entry for key " + id);
            }

            return t;
        } catch (SQLException e) {
            log.severe("Error in " + selectStatement.toString() + " --> " + e.getMessage());
            //e.printStackTrace();
            //System.exit(-1);
            return null;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public synchronized int getTableSize() {
        try {
            tableSizeQueryStatement = conn.prepareStatement(tableSizeQuery);
            ResultSet rs = tableSizeQueryStatement.executeQuery();

            if (rs.next()) {
                int size = rs.getInt(1);
                tableSizeQueryStatement.close();
                return size;
            }

        } catch (SQLException se) {
            se.printStackTrace();
            System.exit(-1);
        }
        return 0;
    }

    // MapLoader interface

    //@Override
    public synchronized Transaction load(String key) {
        //log.info("TransactionTable.load " + key);
        if (conn == null)
            establishConnection();
        //String txnID = key.transactionID;
        return readFromDatabase(key);
    }

    //@Override
    public synchronized Map<TransactionKey, Transaction> loadAll(Collection<String> collection) {
        //log.info("TransactionTable.loadAll() with " + collection.size() + " keys");
        if (conn == null)
            establishConnection();
        Map<TransactionKey,Transaction> results = new HashMap<>(collection.size());
        // NOTE: parallelStream here leads to SQLException in read database, so drop back here until we
        // can make that threadsafe. (Trying to use shared PreparedStatement with different parameters)
        collection.stream().forEach((String txnid) -> {
            Transaction t = load(txnid);
            String acct = t.getAccountNumber();
            results.put(new TransactionKey(txnid, acct), t);
        });
        if (results.size() != collection.size()) {
            log.warning(("TransactionTable loadAll: " + collection.size() + " items requested but only " + results.size() + " were found."));
        }
        return results;
    }

    //@Override
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
