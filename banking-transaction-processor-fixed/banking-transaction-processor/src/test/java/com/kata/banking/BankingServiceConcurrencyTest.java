package com.kata.banking;

import static org.junit.Assert.assertEquals;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** The service promises that concurrent callers never lose an update, create money, or deadlock. */
public class BankingServiceConcurrencyTest {

    private static final int THREADS = 8;

    private final BankingService bank = new BankingService(Clock.systemUTC());

    @Test(timeout = 20_000)
    public void concurrentDepositsToOneAccountAreAllApplied() throws Exception {
        bank.openAccount("shared");

        runInParallel(THREADS, () -> {
            for (int i = 0; i < 1_000; i++) {
                bank.deposit("shared", Money.of("1.00"));
            }
        });

        assertEquals(Money.of("8000.00"), bank.balanceOf("shared"));
        assertEquals(8_000, bank.historyOf("shared").size());
    }

    @Test(timeout = 20_000)
    public void transfersInOppositeDirectionsDoNotDeadlockAndConserveMoney() throws Exception {
        bank.openAccount("a", Money.of("10000.00"));
        bank.openAccount("b", Money.of("10000.00"));

        runInParallel(THREADS, () -> {
            for (int i = 0; i < 2_000; i++) {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    bank.transfer("a", "b", Money.of("1.00"));
                } else {
                    bank.transfer("b", "a", Money.of("1.00"));
                }
            }
        });

        assertEquals(Money.of("20000.00"), bank.balanceOf("a").plus(bank.balanceOf("b")));
    }

    @Test(timeout = 30_000)
    public void randomTransfersAcrossManyAccountsConserveMoneyAndKeepLedgersConsistent() throws Exception {
        int accounts = 10;
        for (int i = 0; i < accounts; i++) {
            bank.openAccount("acc-" + i, Money.of("1000.00"));
        }

        runInParallel(THREADS, () -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2_000; i++) {
                int from = random.nextInt(accounts);
                int to = (from + 1 + random.nextInt(accounts - 1)) % accounts;
                try {
                    bank.transfer("acc-" + from, "acc-" + to, Money.of("50.00"));
                } catch (InsufficientFundsException expectedWhenAnAccountRunsDry) {
                    // legitimate outcome under contention
                }
            }
        });

        Money total = Money.ZERO;
        for (int i = 0; i < accounts; i++) {
            String id = "acc-" + i;
            Money balance = bank.balanceOf(id);
            total = total.plus(balance);
            List<Transaction> history = bank.historyOf(id);
            if (!history.isEmpty()) {
                Money lastBalanceRecorded = history.get(history.size() - 1).balanceAfter();
                assertEquals("ledger and balance disagree for " + id, balance, lastBalanceRecorded);
            }
        }
        assertEquals(Money.of("10000.00"), total);
    }

    private static void runInParallel(int threads, Runnable work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch startTogether = new CountDownLatch(1);
            List<Future<Void>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Void> task = () -> {
                    startTogether.await();
                    work.run();
                    return null;
                };
                results.add(pool.submit(task));
            }
            startTogether.countDown();
            for (Future<Void> result : results) {
                result.get(); // rethrows any failure from a worker
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
