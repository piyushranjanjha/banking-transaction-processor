package com.kata.banking;

import java.math.BigDecimal;

/**
 * A non-negative amount of a single (implicit) currency, always held to exactly two decimal places.
 *
 * <p>Values with more precision than pennies are rejected rather than rounded: silently rounding
 * someone's money is a decision for the caller, not for this type.
 */
public final class Money implements Comparable<Money> {

    private static final int SCALE = 2;

    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal value;

    private Money(BigDecimal value) {
        this.value = value;
    }

    public static Money of(String amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount must be provided");
        }
        try {
            return of(new BigDecimal(amount.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("Amount is not a number: '" + amount + "'");
        }
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount must be provided");
        }
        if (amount.signum() < 0) {
            throw new InvalidAmountException("Amount must not be negative: " + amount.toPlainString());
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidAmountException(
                    "Amount must not have more than " + SCALE + " decimal places: " + amount.toPlainString());
        }
        return new Money(amount.setScale(SCALE));
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value));
    }

    public Money minus(Money other) {
        if (other.isGreaterThan(this)) {
            throw new InvalidAmountException("Cannot subtract " + other + " from " + this);
        }
        return new Money(value.subtract(other.value));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return value.compareTo(other.value) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
