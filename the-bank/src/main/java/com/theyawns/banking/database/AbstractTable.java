package com.theyawns.banking.database;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.controller.launcher.BankInABoxProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Common base class for Tables, mainly to share connection logic */
public class AbstractTable {

    private final static ILogger log = Logger.getLogger(AbstractTable.class);

    protected Connection conn;

    public synchronized void establishConnection()  {
        //log.info("AbstractTable.establishConnection()");
        try {
            // Register the driver, we don't need to actually assign the class to anything
            Class.forName(BankInABoxProperties.JDBC_DRIVER_CLASS);
            String jdbcURL = "jdbc:" + BankInABoxProperties.JDBC_PROTOCOL + "://" +
                    BankInABoxProperties.JDBC_HOST + ":" + BankInABoxProperties.JDBC_PORT + "/" + BankInABoxProperties.JDBC_DB_NAME;
            //log.info("Attempting connection to " + jdbcURL + " for user " + BankInABoxProperties.JDBC_USER);
            conn = DriverManager.getConnection(
                    jdbcURL, BankInABoxProperties.JDBC_USER, BankInABoxProperties.JDBC_PASS);
            log.info("Established connection to BankInABox database");
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
