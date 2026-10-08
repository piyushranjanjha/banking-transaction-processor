package com.kata.banking;

/** The account does not hold enough money to cover the requested debit. */
public class InsufficientFundsException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String accountId, Money balance, Money requested) {
        super("Account " + accountId + " has " + balance + " available but " + requested + " was requested");
    }
}
