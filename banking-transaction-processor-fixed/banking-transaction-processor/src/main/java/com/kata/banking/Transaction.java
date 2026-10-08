package com.kata.banking;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One immutable entry in an account's ledger.
 *
 * <p>A transfer produces two entries, one per account, that share a {@code transferId}.
 * {@code balanceAfter} makes each entry self-describing: the ledger can be audited without replaying it.
 */
public final class Transaction {

    private final UUID id;
    private final String accountId;
    private final TransactionType type;
    private final Money amount;
    private final Money balanceAfter;
    private final Instant timestamp;
    private final String counterpartyAccountId;
    private final UUID transferId;

    Transaction(String accountId, TransactionType type, Money amount, Money balanceAfter, Instant timestamp,
                String counterpartyAccountId, UUID transferId) {
        this.id = UUID.randomUUID();
        this.accountId = Objects.requireNonNull(accountId);
        this.type = Objects.requireNonNull(type);
        this.amount = Objects.requireNonNull(amount);
        this.balanceAfter = Objects.requireNonNull(balanceAfter);
        this.timestamp = Objects.requireNonNull(timestamp);
        this.counterpartyAccountId = counterpartyAccountId;
        this.transferId = transferId;
    }

    public UUID id() {
        return id;
    }

    public String accountId() {
        return accountId;
    }

    public TransactionType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public Money balanceAfter() {
        return balanceAfter;
    }

    public Instant timestamp() {
        return timestamp;
    }

    /** The other account involved; present only for transfers. */
    public Optional<String> counterpartyAccountId() {
        return Optional.ofNullable(counterpartyAccountId);
    }

    /** Links the two legs of a transfer; present only for transfers. */
    public Optional<UUID> transferId() {
        return Optional.ofNullable(transferId);
    }

    @Override
    public String toString() {
        return timestamp + " " + type + " " + amount + " on " + accountId + " (balance " + balanceAfter + ")";
    }
}
