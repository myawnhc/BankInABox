package com.theyawns.launcher;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.util.EnvironmentSetup;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BankInABoxProperties {

    private final static ILogger log = Logger.getLogger(BankInABoxProperties.class);

    public static boolean COLLECT_TPS_STATS     = false;
    public static boolean COLLECT_LATENCY_STATS = false;

    public static int TRANSACTION_THREADS = 5;
    public static int TRANSACTION_COUNT   = 500000;
    public static int MERCHANT_COUNT      = 151;
    public static int ACCOUNT_COUNT       = 1001;

    public static String JDBC_DRIVER_CLASS;
    public static String JDBC_DB_NAME;
    public static String JDBC_PROTOCOL;
    public static String JDBC_HOST;
    public static String JDBC_PORT;
    public static String JDBC_USER;
    public static String JDBC_PASS;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        String propFileName = "BankInABox.properties";
        InputStream stream = BankInABoxProperties.class.getClassLoader().getResourceAsStream(
                propFileName);
        if (null == stream) {
            try {
                throw new FileNotFoundException("Property file " + propFileName
                        + " not found in the classpath.  Defaults will be used");
            } catch (FileNotFoundException e) {
                log.severe(e);
            }
        }
        try {
            Properties properties = new Properties();
            properties.load(stream);
            setProperties(properties);
        } catch (IOException e) {
            log.severe(e);
        }
    }

    private static void setProperties(Properties properties) {
        String temp = properties.getProperty("CollectTPSStats");
        if (temp == null) {
            log.info("No value in properties file for CollectTPSStats.");

        }
        COLLECT_TPS_STATS = Boolean.parseBoolean(temp);

        temp = properties.getProperty("CollectLatencyStats");
        if (temp == null) {
            log.info("No value in properties file for CollectLatencyStats.");
        }

        COLLECT_LATENCY_STATS = Boolean.parseBoolean(temp);

        temp = properties.getProperty("TxnGen_Transaction_Thread_Count");
        if (temp == null) {
            log.info("Missing TxnGen_Transation_Thread_Count for TransactionGenerator.");
        }
        TRANSACTION_THREADS = Integer.parseInt(temp);

        temp = properties.getProperty("TxnGen_Transaction_Count");
        if (temp == null) {
            log.info("Missing TxnGen_Transation_Count for TransactionGenerator.");
        }
        TRANSACTION_COUNT = Integer.parseInt(temp);

        temp = properties.getProperty("TxnGen_Merchant_Count");
        if (temp == null) {
            log.info("Missing TxnGen_Merchant_Count for TransactionGenerator.");
        }
        MERCHANT_COUNT = Integer.parseInt(temp);

        temp = properties.getProperty("TxnGen_Account_Count");
        if (temp == null) {
            log.info("Missing TxnGen_Account_Count for TransactionGenerator.");
        }
        ACCOUNT_COUNT = Integer.parseInt(temp);

        JDBC_DRIVER_CLASS = properties.getProperty("JDBC_DRIVER_CLASS");
        JDBC_DB_NAME = properties.getProperty("JDBC_DB_NAME");
        JDBC_PROTOCOL = properties.getProperty("JDBC_PROTOCOL");
        
        if (System.getProperty(EnvironmentSetup.KUBERNETES_ENABLED).equalsIgnoreCase("true")) {
            JDBC_HOST = EnvironmentSetup.MARIA_SERVICE;
        	log.info("Change from JDBC host: '" + properties.getProperty("JDBC_HOST") + 
        			"' to '" + JDBC_HOST + "'. Plus set port, username, password.");
            JDBC_PORT = "3306";
            JDBC_USER = "root";
            JDBC_PASS = "root";            
        } else {
            JDBC_HOST = properties.getProperty("JDBC_HOST");
            JDBC_PORT = properties.getProperty("JDBC_PORT");
            JDBC_USER = properties.getProperty("JDBC_USER");
            JDBC_PASS = properties.getProperty("JDBC_PASS");
        }

    }
}
