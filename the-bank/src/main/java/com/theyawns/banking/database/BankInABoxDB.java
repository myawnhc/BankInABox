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

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.controller.launcher.BankInABoxProperties;

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
            System.out.println("User / Pass " + BankInABoxProperties.JDBC_USER + " / " + BankInABoxProperties.JDBC_PASS);
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



