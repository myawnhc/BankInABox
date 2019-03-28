package com.theyawns.domain.payments;

import java.io.Serializable;

// TODO: Use IdentifiedDataSerializable
public class Account implements Serializable {

    public enum AccountStatus { CURRENT, OVERDUE, CLOSED } // TODO

    private String accountNumber;
    private Double creditLimit;
    private Double balance;
    private AccountStatus status;

    public Account(String acctNo) {
        this.accountNumber = acctNo;
        status = AccountStatus.CURRENT;
        balance = 0.0;
        creditLimit = 1000.0;
    }

    public String getAccountNumber() { return accountNumber; }

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
}
