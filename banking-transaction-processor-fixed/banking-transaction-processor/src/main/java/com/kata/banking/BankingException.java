package com.kata.banking;

/** Root of every business-rule violation raised by the banking service. */
public abstract class BankingException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    protected BankingException(String message) {
        super(message);
    }
}
