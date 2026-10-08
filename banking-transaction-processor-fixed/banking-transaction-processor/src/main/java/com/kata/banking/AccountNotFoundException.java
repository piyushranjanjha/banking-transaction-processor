package com.kata.banking;

public class AccountNotFoundException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String accountId) {
        super("No account with id '" + accountId + "'");
    }
}
