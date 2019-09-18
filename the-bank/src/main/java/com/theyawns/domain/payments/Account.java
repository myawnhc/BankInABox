package com.theyawns.domain.payments;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.Constants;

import java.io.IOException;
import java.io.Serializable;

/* Must continue to support default Java Serializable until EntryProcessors implement IdentifiedDataSerializable */

/** Simple account type for use with Bank in a Box demo app */
public class Account implements IdentifiedDataSerializable, Serializable {

    public enum AccountStatus { CURRENT, OVERDUE, CLOSED } // TODO: This is not currently used

    protected String accountNumber = "*invalid*";
    private Double creditLimit = 0.0;
    private Double balance = 0.0;
    private AccountStatus status = AccountStatus.CURRENT;
    //private Location lastReportedLocation;
    private String lastReportedLocation = "unknown";


    public Account(String acctNo) {
        this.accountNumber = acctNo;
        status = AccountStatus.CURRENT;
        balance = 0.0;
        creditLimit = 1000.0;
    }

    public Account(Account copyfrom) {
        this.accountNumber = copyfrom.accountNumber;
        this.creditLimit = copyfrom.creditLimit;
        this.balance = copyfrom.balance;
        this.status = copyfrom.status;
    }

    // no arg constructor needed for IDS Serialization
    public Account() {}

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String acctNo) { this.accountNumber = acctNo; } // used by JDBC

    public void setBalance(Double balance) { this.balance = balance; }
    public Double getBalance() {
        return balance;
    }
    public void adjustBalance(Double adjustment) { this.balance += balance; }

    public void setCreditLimit(Double limit) { this.creditLimit = limit; }
    public Double getCreditLimit() {
        return creditLimit;
    }

    public void setAccountStatus(AccountStatus status) { this.status = status; }
    public AccountStatus getAccountStatus() { return status; }

    public void setLastReportedLocation(String location) { this.lastReportedLocation = location; }
    public String getLastReportedLocation() { return lastReportedLocation; }

    public String toString() {
        return "Acct " + accountNumber + " " + creditLimit + " " + balance + " " + status;
    }


    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getId() {
        return Constants.IDS_ACCOUNT_ID;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeUTF(accountNumber);
        objectDataOutput.writeDouble(creditLimit);
        objectDataOutput.writeDouble(balance);
        objectDataOutput.writeInt(status.ordinal());
        objectDataOutput.writeUTF(lastReportedLocation);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        accountNumber = objectDataInput.readUTF();
        creditLimit = objectDataInput.readDouble();
        balance = objectDataInput.readDouble();
        status = AccountStatus.values()[objectDataInput.readInt()];
        lastReportedLocation = objectDataInput.readUTF();
    }
}
