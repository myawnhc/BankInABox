package com.theyawns.domain.payments.database;

import com.theyawns.launcher.BankInABoxProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class AbstractTable {

    protected Connection conn;

    public void establishConnection()  {
        try {
            // Register the driver, we don't need to actually assign the class to anything
            Class.forName(BankInABoxProperties.JDBC_DRIVER_CLASS);
            //"org.mariadb.jdbc.Driver");
            String jdbcURL = "jdbc:" + BankInABoxProperties.JDBC_PROTOCOL + "://" +
                    BankInABoxProperties.JDBC_HOST + ":" + BankInABoxProperties.JDBC_PORT + "/" + BankInABoxProperties.JDBC_DB_NAME;
            System.out.println("JDBC URL (for table access) is " + jdbcURL);
//            Class.forName("org.mariadb.jdbc.Driver");
            conn = DriverManager.getConnection(
                    jdbcURL, BankInABoxProperties.JDBC_USER, BankInABoxProperties.JDBC_PASS);
            //log.info("Established connection to BankInABox database");
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
