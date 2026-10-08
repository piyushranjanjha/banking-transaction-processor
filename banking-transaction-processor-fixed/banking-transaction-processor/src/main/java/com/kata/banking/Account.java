package com.kata.banking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A bank account: an identity, a balance and the ledger that explains the balance.
 *
 * <p>The account enforces its own invariants (positive amounts, no overdraft, every change is ledgered).
 * It is deliberately <em>not</em> thread-safe; {@link BankingService} is the concurrency boundary because
 * a transfer spans two accounts and so cannot be made safe by either account alone.
 */
public final class Account {

    private final String id;
    private final List<Transaction> ledger = new ArrayList<>();
    private Money balance;

    private Account(String id, Money openingBalance) {
        this.id = id;
        this.balance = openingBalance;
    }

    public static Account open(String id, Money openingBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        if (openingBalance == null) {
            throw new InvalidAmountException("Opening balance must be provided");
        }
        return new Account(id, openingBalance);
    }

    public String id() {
        return id;
    }

    public Money balance() {
        return balance;
    }

    /** A snapshot of the ledger, oldest entry first. */
    public List<Transaction> ledger() {
        return List.copyOf(ledger);
    }

    public Transaction deposit(Money amount, Instant at) {
        return credit(TransactionType.DEPOSIT, amount, at, null, null);
    }

    public Transaction withdraw(Money amount, Instant at) {
        return debit(TransactionType.WITHDRAWAL, amount, at, null, null);
    }

    public Transaction transferOut(Money amount, Instant at, String toAccountId, UUID transferId) {
        return debit(TransactionType.TRANSFER_OUT, amount, at, toAccountId, transferId);
    }

    public Transaction transferIn(Money amount, Instant at, String fromAccountId, UUID transferId) {
        return credit(TransactionType.TRANSFER_IN, amount, at, fromAccountId, transferId);
    }

    private Transaction credit(TransactionType type, Money amount, Instant at, String counterparty, UUID transferId) {
        requirePositive(amount);
        return record(type, amount, balance.plus(amount), at, counterparty, transferId);
    }

    private Transaction debit(TransactionType type, Money amount, Instant at, String counterparty, UUID transferId) {
        requirePositive(amount);
        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        return record(type, amount, balance.minus(amount), at, counterparty, transferId);
    }

    private Transaction record(TransactionType type, Money amount, Money newBalance, Instant at,
                               String counterparty, UUID transferId) {
        Transaction entry = new Transaction(id, type, amount, newBalance, at, counterparty, transferId);
        balance = newBalance;
        ledger.add(entry);
        return entry;
    }

    private static void requirePositive(Money amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount must be provided");
        }
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }
}
