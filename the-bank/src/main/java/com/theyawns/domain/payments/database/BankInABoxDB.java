package com.theyawns.domain.payments.database;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.launcher.BankInABoxProperties;

import java.sql.*;

public class BankInABoxDB {
    private final static ILogger log = Logger.getLogger(BankInABoxDB.class);

    private Connection conn;

    private static final String createDatabaseString = "create database BankInABox";
    private static final String dropDatabaseString   = "drop database if exists BankInABox";

    protected synchronized void establishConnection()  {
        try {
            // Register the driver, we don't need to actually assign the class to anything
            Class.forName(BankInABoxProperties.JDBC_DRIVER_CLASS);
                    //"org.mariadb.jdbc.Driver");
            String jdbcURL = "jdbc:" + BankInABoxProperties.JDBC_PROTOCOL + "://" +
                    BankInABoxProperties.JDBC_HOST + ":" + BankInABoxProperties.JDBC_PORT + "/";
            System.out.println("JDBC URL is " + jdbcURL);
            conn = DriverManager.getConnection(
                    jdbcURL, BankInABoxProperties.JDBC_USER, BankInABoxProperties.JDBC_PASS);
            log.info("Established connection to MySQL/MariaDB server");
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    protected synchronized void createDatabase() {
        if (conn == null) {
            throw new IllegalStateException("Must establish connection before creating the database!");
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(dropDatabaseString);
            log.info("Dropped (if exists) database BankInABox");
            stmt.executeUpdate(createDatabaseString);
            log.info("Created Database BankInABox");

        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // For testing purposes
    public static void main(String[] args) {
        BankInABoxDB main = new BankInABoxDB();
        main.establishConnection();
        main.createDatabase();
    }
}



