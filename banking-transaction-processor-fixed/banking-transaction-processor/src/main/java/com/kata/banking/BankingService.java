package com.kata.banking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for processing transactions across many accounts.
 *
 * <h2>Concurrency</h2>
 * Each {@link Account} is guarded by its own monitor, so operations on unrelated accounts never wait for
 * each other. A transfer needs both monitors; they are always taken in account-id order, so two opposing
 * transfers (A to B and B to A) cannot each hold one lock while waiting for the other.
 */
public class BankingService {

    private final Clock clock;
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public BankingService(Clock clock) {
        this.clock = clock;
    }

    public void openAccount(String accountId) {
        openAccount(accountId, Money.ZERO);
    }

    public void openAccount(String accountId, Money openingBalance) {
        Account account = Account.open(accountId, openingBalance); // validates the id before it touches the map
        if (accounts.putIfAbsent(accountId, account) != null) {
            throw new DuplicateAccountException(accountId);
        }
    }

    public Transaction deposit(String accountId, Money amount) {
        Account account = find(accountId);
        synchronized (account) {
            return account.deposit(amount, clock.instant());
        }
    }

    public Transaction withdraw(String accountId, Money amount) {
        Account account = find(accountId);
        synchronized (account) {
            return account.withdraw(amount, clock.instant());
        }
    }

    public void transfer(String fromAccountId, String toAccountId, Money amount) {
        Account from = find(fromAccountId);
        Account to = find(toAccountId);
        if (from == to) {
            throw new InvalidTransferException("Cannot transfer from account '" + fromAccountId + "' to itself");
        }
        Account first = fromAccountId.compareTo(toAccountId) < 0 ? from : to;
        Account second = first == from ? to : from;

        synchronized (first) {
            synchronized (second) {
                UUID transferId = UUID.randomUUID();
                Instant at = clock.instant();
                // The debit is the only step that can fail (amount, overdraft), so it goes first:
                // if it throws, nothing has changed. The credit cannot fail once the debit has succeeded.
                from.transferOut(amount, at, toAccountId, transferId);
                to.transferIn(amount, at, fromAccountId, transferId);
            }
        }
    }

    public Money balanceOf(String accountId) {
        Account account = find(accountId);
        synchronized (account) {
            return account.balance();
        }
    }

    public List<Transaction> historyOf(String accountId) {
        Account account = find(accountId);
        synchronized (account) {
            return account.ledger();
        }
    }

    private Account find(String accountId) {
        Account account = accountId == null ? null : accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }
}
