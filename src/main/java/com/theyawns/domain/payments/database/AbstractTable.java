package com.theyawns.domain.payments.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class AbstractTable {

    protected Connection conn;

    public void establishConnection()  {
        try {
            // Register the driver, we don't need to actually assign the class to anything
            Class.forName("org.mariadb.jdbc.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/BankInABox", "root", "");
            //log.info("Established connection to BankInABox database");
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
