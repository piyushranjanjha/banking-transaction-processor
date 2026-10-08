package com.kata.banking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class AccountTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T11:00:00Z");

    private final Account account = Account.open("ACC-1", Money.of("100.00"));

    @Test
    public void startsWithItsIdAndOpeningBalanceAndAnEmptyLedger() {
        assertEquals("ACC-1", account.id());
        assertEquals(Money.of("100.00"), account.balance());
        assertTrue(account.ledger().isEmpty());
    }

    @Test
    public void rejectsABlankId() {
        assertThrows(IllegalArgumentException.class, () -> Account.open(" ", Money.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Account.open(null, Money.ZERO));
    }

    @Test
    public void depositIncreasesBalanceAndIsRecordedInTheLedger() {
        account.deposit(Money.of("25.50"), T1);

        assertEquals(Money.of("125.50"), account.balance());
        Transaction entry = onlyEntryOf(account);
        assertEquals(TransactionType.DEPOSIT, entry.type());
        assertEquals(Money.of("25.50"), entry.amount());
        assertEquals(Money.of("125.50"), entry.balanceAfter());
        assertEquals(T1, entry.timestamp());
        assertEquals("ACC-1", entry.accountId());
        assertTrue(entry.counterpartyAccountId().isEmpty());
    }

    @Test
    public void withdrawalDecreasesBalanceAndIsRecordedInTheLedger() {
        account.withdraw(Money.of("40.00"), T1);

        assertEquals(Money.of("60.00"), account.balance());
        Transaction entry = onlyEntryOf(account);
        assertEquals(TransactionType.WITHDRAWAL, entry.type());
        assertEquals(Money.of("40.00"), entry.amount());
        assertEquals(Money.of("60.00"), entry.balanceAfter());
    }

    @Test
    public void mayWithdrawTheEntireBalance() {
        account.withdraw(Money.of("100.00"), T1);

        assertEquals(Money.ZERO, account.balance());
    }

    @Test
    public void refusesAnOverdraftAndLeavesBalanceAndLedgerUntouched() {
        InsufficientFundsException e = assertThrows(InsufficientFundsException.class,
                () -> account.withdraw(Money.of("100.01"), T1));

        assertTrue(e.getMessage().contains("ACC-1"));
        assertEquals(Money.of("100.00"), account.balance());
        assertTrue(account.ledger().isEmpty());
    }

    @Test
    public void refusesZeroAmountsForEveryOperation() {
        UUID transferId = UUID.randomUUID();
        assertThrows(InvalidAmountException.class, () -> account.deposit(Money.ZERO, T1));
        assertThrows(InvalidAmountException.class, () -> account.withdraw(Money.ZERO, T1));
        assertThrows(InvalidAmountException.class, () -> account.transferOut(Money.ZERO, T1, "ACC-2", transferId));
        assertThrows(InvalidAmountException.class, () -> account.transferIn(Money.ZERO, T1, "ACC-2", transferId));
        assertTrue(account.ledger().isEmpty());
    }

    @Test
    public void transferLegsRecordTheCounterpartyAndShareATransferId() {
        UUID transferId = UUID.randomUUID();

        account.transferOut(Money.of("30.00"), T1, "ACC-2", transferId);
        account.transferIn(Money.of("5.00"), T2, "ACC-3", transferId);

        List<Transaction> ledger = account.ledger();
        assertEquals(TransactionType.TRANSFER_OUT, ledger.get(0).type());
        assertEquals("ACC-2", ledger.get(0).counterpartyAccountId().orElseThrow());
        assertEquals(transferId, ledger.get(0).transferId().orElseThrow());
        assertEquals(Money.of("70.00"), ledger.get(0).balanceAfter());
        assertEquals(TransactionType.TRANSFER_IN, ledger.get(1).type());
        assertEquals("ACC-3", ledger.get(1).counterpartyAccountId().orElseThrow());
        assertEquals(Money.of("75.00"), ledger.get(1).balanceAfter());
    }

    @Test
    public void transferOutIsSubjectToTheOverdraftRule() {
        assertThrows(InsufficientFundsException.class,
                () -> account.transferOut(Money.of("100.01"), T1, "ACC-2", UUID.randomUUID()));
        assertEquals(Money.of("100.00"), account.balance());
    }

    @Test
    public void ledgerIsKeptInTheOrderOperationsHappened() {
        account.deposit(Money.of("1.00"), T1);
        account.withdraw(Money.of("2.00"), T2);

        List<Transaction> ledger = account.ledger();
        assertEquals(TransactionType.DEPOSIT, ledger.get(0).type());
        assertEquals(TransactionType.WITHDRAWAL, ledger.get(1).type());
    }

    @Test
    public void ledgerReturnedToCallersCannotBeUsedToTamperWithHistory() {
        account.deposit(Money.of("1.00"), T1);

        assertThrows(UnsupportedOperationException.class, () -> account.ledger().clear());
    }

    @Test
    public void ledgerReturnedEarlierIsASnapshotAndDoesNotChangeLater() {
        account.deposit(Money.of("1.00"), T1);
        List<Transaction> snapshot = account.ledger();

        account.deposit(Money.of("1.00"), T2);

        assertEquals(1, snapshot.size());
        assertEquals(2, account.ledger().size());
    }

    private static Transaction onlyEntryOf(Account account) {
        assertEquals(1, account.ledger().size());
        return account.ledger().get(0);
    }
}
