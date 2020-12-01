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
