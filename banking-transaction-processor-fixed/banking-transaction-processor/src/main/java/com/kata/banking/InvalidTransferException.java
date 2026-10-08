package com.kata.banking;

/** The transfer itself is malformed, e.g. an account sending money to itself. */
public class InvalidTransferException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InvalidTransferException(String message) {
        super(message);
    }
}
