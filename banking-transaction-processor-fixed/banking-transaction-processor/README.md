# Banking Transaction Processor

An in-memory service that processes banking transactions for multiple accounts: deposits, withdrawals and
transfers, a per-account ledger with timestamps, and queries for balances and transaction history.

Java 21, Maven, JUnit 4. No frameworks: the domain is the point, so nothing sits between the reader and it.

```
mvn test
```

## Usage

```java
BankingService bank = new BankingService(Clock.systemUTC());
bank.openAccount("alice", Money.of("100.00"));
bank.openAccount("bob");

bank.deposit("alice", Money.of("25.50"));
bank.withdraw("alice", Money.of("10.00"));
bank.transfer("alice", "bob", Money.of("40.00"));

bank.balanceOf("alice");    // 75.50
bank.historyOf("bob");      // [TRANSFER_IN 40.00 from alice, balance 40.00]
```

## Understanding the problem

The brief is short, so the first job was to decide what "correct" means for the parts it leaves open.

| Question | Decision | Why |
|---|---|---|
| What is an "invalid amount"? | Negative, zero, not a number, missing, or finer than a penny | Each one is a different way for bad input to become wrong money. |
| Round `10.005`, or reject it? | Reject | Rounding someone's money is a business decision, not something a value type should do silently. |
| What may an overdraft mean for a transfer? | The source must hold the full amount; a failed transfer changes nothing | A transfer is all-or-nothing. A half-applied transfer is the worst bug this system could have. |
| Transfer to yourself? | Rejected (`InvalidTransferException`) | It is a no-op that would still write two ledger entries and could only be a mistake. |
| Can accounts have a balance of exactly zero after a withdrawal? | Yes | Overdraft means going *below* zero. |
| What is in the ledger when an operation fails? | Nothing | The ledger records what happened, not what was attempted. |
| How is one transfer represented? | Two entries, one per account, sharing a `transferId`, same timestamp | Each account's history stays self-contained and the two legs can be correlated. |
| Where does the time come from? | An injected `Clock` | Timestamps become testable without sleeping or flaky assertions. |

## Design

```
BankingService  -- finds accounts, stamps time, owns concurrency
    Account     -- balance + ledger; enforces positive amounts and no overdraft
        Transaction  -- immutable ledger entry (type, amount, balanceAfter, timestamp, counterparty, transferId)
        Money        -- non-negative, exactly two decimals, BigDecimal inside
```

- **`Money`** makes the two worst money bugs unrepresentable: floating point (it wraps `BigDecimal`) and
  negative amounts. `Money.of("10.5")` equals `Money.of("10.50")`, which a raw `BigDecimal` does not give you.
- **`Account`** protects its own invariants, so nothing can bypass them by calling it directly. Each ledger
  entry stores `balanceAfter`, so a ledger can be audited without replaying it.
- **`BankingService`** is a facade. It is the only place that knows about concurrency, because a transfer
  spans two accounts and so no single account can make it safe.
- **Exceptions** are unchecked and share a root (`BankingException`), so callers can catch one type or a
  specific one. Each failure has its own type because each needs a different response from a caller.
- **Ledger snapshots**: `ledger()` returns an immutable copy, so callers cannot rewrite history and a list
  they already hold never changes under them.

## Concurrency, and how it evolved

This is the part I changed my mind about, and the history shows it:

1. `b83d273` started with `synchronized` on every service method: one lock for the whole bank. It is
   trivially correct, and I wanted a correct baseline before optimising anything.
2. `a5f6524` added concurrency tests (lost updates, money conservation, opposing transfers, ledger/balance
   agreement). They passed against the simple version, so they characterise the behaviour I must keep.
3. `515b5ee` replaced the global lock with per-account locks. Unrelated accounts no longer queue behind each
   other. Transfers take both locks in account-id order, which makes the classic A-to-B / B-to-A deadlock
   impossible. I checked the tests really guard this: with the ordering removed, both transfer tests hang
   and time out.

Trade-off: per-account locking is more code than one big lock. In a ledger service that is the hot path, so I
judged it worth it; for a tiny system I would have stopped at step 1.

## Tests

Tests are written first and named as behaviour (`overdraftingTransferChangesNeitherAccount`,
`refusesAnOverdraftAndLeavesBalanceAndLedgerUntouched`), so reading the test names gives you the contract.
They are layered: `MoneyTest` (value rules), `AccountTest` (invariants and ledger), `BankingServiceTest`
(use cases through the public API), `BankingServiceConcurrencyTest` (thread-safety promises).

## Limitations and decisions to discuss

- **Verification note.** The sandbox I built this in could not reach Maven Central, so I could not run
  `mvn test` and could not use JUnit 5 / AssertJ. I compiled with `javac --release 21 -Xlint:all` (zero
  warnings) and ran all 43 tests with `JUnitCore` against JUnit 4.13.2, which is what the `pom.xml` declares.
  I expect `mvn test` to work unchanged but have not seen it run. With a normal network I would use JUnit 5.
- **No HTTP layer.** "APIs" is delivered as a Java service interface. A Spring Boot controller is a thin
  adapter over `BankingService` and would not change the domain; I kept it out so the timebox went into
  behaviour and tests. Mapping the exceptions to status codes is straightforward (404 unknown account, 409
  duplicate, 422 invalid amount / insufficient funds).
- **In-memory only.** State is lost on restart. Persistence would sit behind an account repository; the
  per-account lock would then become a database transaction or optimistic version check.
- **Single currency.** `Money` has no currency. Adding one is a change to `Money` alone, which is why the
  concept is its own type.
- **No idempotency.** Retrying a request applies it twice. A real payment API needs client-supplied
  idempotency keys.
- **History is unbounded and unpaged.** `historyOf` returns the whole ledger. Next step: filtering by date
  range and paging.
- **Locks use the `Account` instance as the monitor**, which is simple but means any code holding a
  reference could lock it. A private lock object would be safer if `Account` ever escapes the package.
- **Account ids are case-sensitive opaque strings**, with only a blank check. A real system would define
  a format.

## What I would do with more time

1. Property-based tests for the central promise: for any sequence of operations, the total money across
   accounts is conserved and each ledger's last `balanceAfter` equals the balance.
2. The HTTP adapter and persistence behind an interface.
3. Date-range filtering and paging for history, and idempotency keys.
