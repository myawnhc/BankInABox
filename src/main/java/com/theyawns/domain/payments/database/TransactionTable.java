package com.theyawns.domain.payments.database;

import com.hazelcast.core.MapLoader;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.domain.payments.Location;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
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
        for (int i = 0; i < count; i++) {
            Transaction t = generate(i);
            writeToDatabase(t);
        }
        return count;
    }

    public void createTransactionTable()  {
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

    public void writeToDatabase(Transaction t) {
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

    public Transaction readFromDatabase(String id) {
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
            return t;
        } catch (SQLException e) {
            log.info("Error in " + selectStatement.toString() + " --> " + e.getMessage());
            //e.printStackTrace();
            //System.exit(-1);
            return null;
        }
    }

    // MapLoader interface
    // TODO: Probably won't use MapLoader for transactions, delete if unneeded

    @Override
    public Transaction load(String s) {
        //log.info("TransactionTable.load " + s);
        if (true)
        throw new IllegalStateException("Should not be loading transaction table!");
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

        return results;
    }

    @Override
    public Iterable<String> loadAllKeys() {
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
