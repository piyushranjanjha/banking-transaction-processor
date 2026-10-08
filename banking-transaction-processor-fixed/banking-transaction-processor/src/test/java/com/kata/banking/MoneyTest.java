package com.kata.banking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import org.junit.Test;

public class MoneyTest {

    @Test
    public void amountsWithTheSameValueAreEqualRegardlessOfTrailingZeros() {
        assertEquals(Money.of("10.5"), Money.of("10.50"));
        assertEquals(Money.of("10"), Money.of(new BigDecimal("10.00")));
    }

    @Test
    public void isAlwaysHeldWithTwoDecimalPlaces() {
        assertEquals("10.50", Money.of("10.5").toString());
        assertEquals("0.00", Money.ZERO.toString());
    }

    @Test
    public void rejectsMoreThanTwoDecimalPlacesInsteadOfSilentlyRounding() {
        assertThrows(InvalidAmountException.class, () -> Money.of("10.001"));
    }

    @Test
    public void rejectsNegativeAmounts() {
        assertThrows(InvalidAmountException.class, () -> Money.of("-0.01"));
    }

    @Test
    public void rejectsMissingAmounts() {
        assertThrows(InvalidAmountException.class, () -> Money.of((BigDecimal) null));
        assertThrows(InvalidAmountException.class, () -> Money.of((String) null));
    }

    @Test
    public void rejectsTextThatIsNotANumber() {
        assertThrows(InvalidAmountException.class, () -> Money.of("ten pounds"));
    }

    @Test
    public void addsAndSubtracts() {
        assertEquals(Money.of("12.34"), Money.of("10.00").plus(Money.of("2.34")));
        assertEquals(Money.of("7.66"), Money.of("10.00").minus(Money.of("2.34")));
    }

    @Test
    public void cannotSubtractMoreThanItHolds() {
        assertThrows(InvalidAmountException.class, () -> Money.of("1.00").minus(Money.of("1.01")));
    }

    @Test
    public void knowsWhetherItIsZeroOrExceedsAnotherAmount() {
        assertTrue(Money.ZERO.isZero());
        assertFalse(Money.of("0.01").isZero());
        assertTrue(Money.of("5.00").isGreaterThan(Money.of("4.99")));
        assertFalse(Money.of("5.00").isGreaterThan(Money.of("5.00")));
    }
}
