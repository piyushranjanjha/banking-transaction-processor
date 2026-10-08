package com.kata.banking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class BankingServiceTest {

    private static final Instant NOON = Instant.parse("2026-03-01T12:00:00Z");

    private MutableClock clock;
    private BankingService bank;

    @Before
    public void setUp() {
        clock = new MutableClock(NOON);
        bank = new BankingService(clock);
        bank.openAccount("alice", Money.of("100.00"));
        bank.openAccount("bob", Money.of("50.00"));
    }

    // ---- accounts -------------------------------------------------------------------------------------------

    @Test
    public void reportsTheBalanceOfAnAccountItHasOpened() {
        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
        assertEquals(Money.of("50.00"), bank.balanceOf("bob"));
    }

    @Test
    public void aMissingAmountIsAnInvalidAmountAndChangesNothing() {
        assertThrows(InvalidAmountException.class, () -> bank.deposit("alice", null));
        assertThrows(InvalidAmountException.class, () -> bank.withdraw("alice", null));
        assertThrows(InvalidAmountException.class, () -> bank.transfer("alice", "bob", null));
        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
        assertEquals(Money.of("50.00"), bank.balanceOf("bob"));
        assertTrue(bank.historyOf("alice").isEmpty());
        assertTrue(bank.historyOf("bob").isEmpty());
    }

    @Test
    public void aMissingOpeningBalanceIsAnInvalidAmountAndOpensNothing() {
        assertThrows(InvalidAmountException.class, () -> bank.openAccount("carol", null));
        assertThrows(AccountNotFoundException.class, () -> bank.balanceOf("carol"));
    }

    @Test
    public void accountsCanBeOpenedEmpty() {
        bank.openAccount("carol");

        assertEquals(Money.ZERO, bank.balanceOf("carol"));
    }

    @Test
    public void refusesToOpenTwoAccountsWithTheSameId() {
        assertThrows(DuplicateAccountException.class, () -> bank.openAccount("alice", Money.ZERO));

        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
    }

    @Test
    public void reportsUnknownAccountsForEveryQuery() {
        assertThrows(AccountNotFoundException.class, () -> bank.balanceOf("nobody"));
        assertThrows(AccountNotFoundException.class, () -> bank.historyOf("nobody"));
        assertThrows(AccountNotFoundException.class, () -> bank.balanceOf(null));
    }

    // ---- deposit and withdraw -------------------------------------------------------------------------------

    @Test
    public void depositAddsToTheBalanceAndIsStampedWithTheCurrentTime() {
        Transaction tx = bank.deposit("alice", Money.of("25.00"));

        assertEquals(Money.of("125.00"), bank.balanceOf("alice"));
        assertEquals(NOON, tx.timestamp());
        assertEquals(List.of(tx), bank.historyOf("alice"));
    }

    @Test
    public void withdrawalSubtractsFromTheBalance() {
        bank.withdraw("alice", Money.of("30.00"));

        assertEquals(Money.of("70.00"), bank.balanceOf("alice"));
    }

    @Test
    public void withdrawalBeyondTheBalanceIsRefusedAndNothingIsRecorded() {
        assertThrows(InsufficientFundsException.class, () -> bank.withdraw("alice", Money.of("100.01")));

        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
        assertTrue(bank.historyOf("alice").isEmpty());
    }

    @Test
    public void zeroAmountsAreRefused() {
        assertThrows(InvalidAmountException.class, () -> bank.deposit("alice", Money.ZERO));
        assertThrows(InvalidAmountException.class, () -> bank.withdraw("alice", Money.ZERO));
        assertThrows(InvalidAmountException.class, () -> bank.transfer("alice", "bob", Money.ZERO));
    }

    @Test
    public void operationsOnUnknownAccountsAreRefused() {
        assertThrows(AccountNotFoundException.class, () -> bank.deposit("nobody", Money.of("1.00")));
        assertThrows(AccountNotFoundException.class, () -> bank.withdraw("nobody", Money.of("1.00")));
    }

    // ---- transfer -------------------------------------------------------------------------------------------

    @Test
    public void transferMovesMoneyAndRecordsBothLegsUnderOneTransferId() {
        bank.transfer("alice", "bob", Money.of("40.00"));

        assertEquals(Money.of("60.00"), bank.balanceOf("alice"));
        assertEquals(Money.of("90.00"), bank.balanceOf("bob"));

        Transaction out = bank.historyOf("alice").get(0);
        Transaction in = bank.historyOf("bob").get(0);
        assertEquals(TransactionType.TRANSFER_OUT, out.type());
        assertEquals("bob", out.counterpartyAccountId().orElseThrow());
        assertEquals(TransactionType.TRANSFER_IN, in.type());
        assertEquals("alice", in.counterpartyAccountId().orElseThrow());
        assertEquals(out.transferId(), in.transferId());
        assertEquals(out.timestamp(), in.timestamp());
        assertNotEquals(out.id(), in.id());
    }

    @Test
    public void transferringTheWholeBalanceIsAllowed() {
        bank.transfer("alice", "bob", Money.of("100.00"));

        assertEquals(Money.ZERO, bank.balanceOf("alice"));
        assertEquals(Money.of("150.00"), bank.balanceOf("bob"));
    }

    @Test
    public void overdraftingTransferChangesNeitherAccount() {
        assertThrows(InsufficientFundsException.class, () -> bank.transfer("alice", "bob", Money.of("100.01")));

        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
        assertEquals(Money.of("50.00"), bank.balanceOf("bob"));
        assertTrue(bank.historyOf("alice").isEmpty());
        assertTrue(bank.historyOf("bob").isEmpty());
    }

    @Test
    public void transferToAnUnknownAccountChangesNothing() {
        assertThrows(AccountNotFoundException.class, () -> bank.transfer("alice", "nobody", Money.of("1.00")));

        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
        assertTrue(bank.historyOf("alice").isEmpty());
    }

    @Test
    public void transferFromAnUnknownAccountChangesNothing() {
        assertThrows(AccountNotFoundException.class, () -> bank.transfer("nobody", "bob", Money.of("1.00")));

        assertEquals(Money.of("50.00"), bank.balanceOf("bob"));
        assertTrue(bank.historyOf("bob").isEmpty());
    }

    @Test
    public void anAccountCannotTransferToItself() {
        assertThrows(InvalidTransferException.class, () -> bank.transfer("alice", "alice", Money.of("1.00")));

        assertEquals(Money.of("100.00"), bank.balanceOf("alice"));
        assertTrue(bank.historyOf("alice").isEmpty());
    }

    // ---- history --------------------------------------------------------------------------------------------

    @Test
    public void historyListsEveryOperationOldestFirstWithItsOwnTimestamp() {
        bank.deposit("alice", Money.of("10.00"));
        clock.advanceSeconds(60);
        bank.withdraw("alice", Money.of("5.00"));
        clock.advanceSeconds(60);
        bank.transfer("alice", "bob", Money.of("20.00"));

        List<Transaction> history = bank.historyOf("alice");

        assertEquals(List.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT),
                history.stream().map(Transaction::type).toList());
        assertEquals(List.of(NOON, NOON.plusSeconds(60), NOON.plusSeconds(120)),
                history.stream().map(Transaction::timestamp).toList());
        assertEquals(Money.of("85.00"), history.get(2).balanceAfter());
    }

    @Test
    public void historyOfOneAccountDoesNotIncludeOtherAccountsActivity() {
        bank.deposit("bob", Money.of("1.00"));

        assertTrue(bank.historyOf("alice").isEmpty());
    }

    // ---- test support ---------------------------------------------------------------------------------------

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
