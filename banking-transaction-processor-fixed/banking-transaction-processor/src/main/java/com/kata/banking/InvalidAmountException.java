package com.kata.banking;

/** The amount is missing, malformed, negative, zero where a positive amount is required, or has sub-penny precision. */
public class InvalidAmountException extends BankingException {
    private static final long serialVersionUID = 1L;


    public InvalidAmountException(String message) {
        super(message);
    }
}
