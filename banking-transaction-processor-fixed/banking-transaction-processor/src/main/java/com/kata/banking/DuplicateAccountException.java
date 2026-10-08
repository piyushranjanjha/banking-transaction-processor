package com.kata.banking;

public class DuplicateAccountException extends BankingException {
    private static final long serialVersionUID = 1L;

    public DuplicateAccountException(String accountId) {
        super("Account '" + accountId + "' already exists");
    }
}
