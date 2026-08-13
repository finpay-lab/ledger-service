package com.finpay.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finpay.ledger.service.application.GetBalanceUseCase;
import com.finpay.ledger.service.application.PostPostingCommand;
import com.finpay.ledger.service.application.PostPostingResult;
import com.finpay.ledger.service.application.PostPostingUseCase;
import com.finpay.ledger.service.domain.AccountBalance;
import com.finpay.ledger.service.domain.AccountBalanceNotFoundException;
import com.finpay.ledger.service.domain.AccountBalanceRepository;
import com.finpay.ledger.service.domain.EntryLeg;
import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.infrastructure.jpa.LedgerAccountBalanceEntity;
import com.finpay.ledger.service.infrastructure.jpa.LedgerAccountBalanceJpaRepository;
import com.finpay.ledger.service.infrastructure.jpa.LedgerEntryEntity;
import com.finpay.ledger.service.infrastructure.jpa.LedgerEntryJpaRepository;
import com.finpay.ledger.service.infrastructure.jpa.LedgerPostingEntity;
import com.finpay.ledger.service.infrastructure.jpa.LedgerPostingJpaRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Proves the ledger invariants against a real PostgreSQL:
 *  1. balance calc — a posting moves the two accounts' balances in opposite
 *     directions and bumps the optimistic-lock versions;
 *  2. DB invariant SUM(debit)==SUM(credit) — an imbalanced posting is rejected
 *     by the deferred constraint triggers at COMMIT even when the application
 *     layer is bypassed (direct entity writes), and nothing is persisted;
 *  3. optimistic locking — a stale write (read before another posting
 *     committed) fails with {@link ObjectOptimisticLockingFailureException}
 *     and concurrent postings to the same account never lose updates.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LedgerInvariantIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger")
            .withUsername("ledger")
            .withPassword("ledger");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    PostPostingUseCase postPostingUseCase;

    @Autowired
    GetBalanceUseCase getBalanceUseCase;

    @Autowired
    AccountBalanceRepository accountBalanceRepository;

    @Autowired
    LedgerAccountBalanceJpaRepository balanceJpaRepository;

    @Autowired
    LedgerPostingJpaRepository postingJpaRepository;

    @Autowired
    LedgerEntryJpaRepository entryJpaRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    private static PostPostingCommand transfer(UUID from, UUID to, String amount, String idempotencyKey) {
        return new PostPostingCommand("transfer", "EUR", idempotencyKey, List.of(
                new EntryLeg(from, EntrySide.DEBIT, new BigDecimal(amount)),
                new EntryLeg(to, EntrySide.CREDIT, new BigDecimal(amount))));
    }

    @Test
    void balanced_posting_updates_both_account_balances_and_bumps_versions() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        PostPostingResult result = postPostingUseCase.post(transfer(from, to, "150.00", "inv-balance-1"));

        assertThat(result.created()).isTrue();
        assertThat(result.posting().entries()).hasSize(2);

        AccountBalance debitAccount = getBalanceUseCase.get(from);
        AccountBalance creditAccount = getBalanceUseCase.get(to);
        assertThat(debitAccount.balance()).isEqualByComparingTo("-150.00");
        assertThat(creditAccount.balance()).isEqualByComparingTo("150.00");
        assertThat(debitAccount.version()).isEqualTo(1);
        assertThat(creditAccount.version()).isEqualTo(1);
    }

    @Test
    void idempotent_replay_returns_the_original_posting_without_side_effects() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        PostPostingResult first = postPostingUseCase.post(transfer(from, to, "100.00", "inv-same-key"));
        PostPostingResult second = postPostingUseCase.post(transfer(from, to, "100.00", "inv-same-key"));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.posting().postingId()).isEqualTo(first.posting().postingId());
        assertThat(second.posting().entries()).hasSize(2);

        // Exactly one posting's worth of effect on the balance.
        assertThat(getBalanceUseCase.get(to).balance()).isEqualByComparingTo("100.00");
        assertThat(getBalanceUseCase.get(to).version()).isEqualTo(1);
    }

    @Test
    void balance_is_not_found_before_the_first_posting() {
        assertThatThrownBy(() -> getBalanceUseCase.get(UUID.randomUUID()))
                .isInstanceOf(AccountBalanceNotFoundException.class);
    }

    /**
     * Bypasses the application layer and writes an imbalanced posting directly
     * through the JPA repositories. The deferred DB trigger
     * {@code assert_posting_balanced} must abort the COMMIT (SUM(debit)=100.00
     * != SUM(credit)=50.00) and roll back everything, even though the
     * per-account balance rows were kept internally consistent on purpose.
     */
    @Test
    void db_invariant_rejects_an_imbalanced_posting_at_commit() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        postPostingUseCase.post(transfer(from, to, "100.00", "inv-setup")); // from=-100, to=100

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] failedPostingId = new UUID[1];
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            // Keep per-account consistency so only the posting-balance invariant
            // SUM(debit)=SUM(credit) can reject the transaction.
            LedgerAccountBalanceEntity fromBalance = balanceJpaRepository.findById(from).orElseThrow();
            fromBalance.setBalance(new BigDecimal("-200.00"));
            LedgerAccountBalanceEntity toBalance = balanceJpaRepository.findById(to).orElseThrow();
            toBalance.setBalance(new BigDecimal("150.00"));

            UUID postingId = UUID.randomUUID();
            failedPostingId[0] = postingId;
            LedgerPostingEntity posting = new LedgerPostingEntity();
            posting.setPostingId(postingId);
            posting.setReference("imbalanced");
            posting.setCurrency("EUR");
            posting.setIdempotencyKey("inv-bad-key");
            posting.setPostedAt(Instant.now());
            postingJpaRepository.save(posting);

            entryJpaRepository.save(entry(postingId, from, "100.00", BigDecimal.ZERO));
            entryJpaRepository.save(entry(postingId, to, BigDecimal.ZERO, "50.00"));
        })).isInstanceOf(RuntimeException.class);

        // The whole transaction rolled back: no posting row, balances unchanged.
        assertThat(postingJpaRepository.findByIdempotencyKey("inv-bad-key")).isEmpty();
        assertThat(entryJpaRepository.findByPostingId(failedPostingId[0])).isEmpty();
        assertThat(balanceJpaRepository.findById(from).orElseThrow().getBalance()).isEqualByComparingTo("-100.00");
        assertThat(balanceJpaRepository.findById(to).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(balanceJpaRepository.findById(from).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void optimistic_lock_rejects_a_stale_balance_write() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        postPostingUseCase.post(transfer(from, to, "100.00", "inv-ol-setup")); // from.version = 1

        // Simulate a concurrent actor that read the balance BEFORE the posting
        // above committed: it holds a stale version 0.
        AccountBalance stale = AccountBalance.hydrate(from, "EUR", BigDecimal.ZERO, 0L, Instant.now());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> accountBalanceRepository.save(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The stale write was rejected; the committed balance is untouched.
        AccountBalance balance = getBalanceUseCase.get(from);
        assertThat(balance.balance()).isEqualByComparingTo("-100.00");
        assertThat(balance.version()).isEqualTo(1);
    }

    @Test
    void concurrent_postings_to_the_same_account_never_lose_updates() throws Exception {
        UUID source1 = UUID.randomUUID();
        UUID source2 = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<PostPostingResult>> tasks = List.of(
                () -> {
                    start.await();
                    return postPostingUseCase.post(transfer(source1, target, "100.00", "inv-conc-1"));
                },
                () -> {
                    start.await();
                    return postPostingUseCase.post(transfer(source2, target, "100.00", "inv-conc-2"));
                });

        List<Future<PostPostingResult>> futures = tasks.stream().map(pool::submit).toList();
        start.countDown();

        int committed = 0;
        for (Future<PostPostingResult> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
                committed++;
            } catch (java.util.concurrent.ExecutionException e) {
                // Exactly one of two racing postings may lose; it must be the
                // optimistic-lock failure, never a silent lost update.
                assertThat(e.getCause()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
            }
        }
        pool.shutdownNow();

        assertThat(committed).isGreaterThanOrEqualTo(1);
        AccountBalance targetBalance = getBalanceUseCase.get(target);
        assertThat(targetBalance.balance())
                .isEqualByComparingTo(new BigDecimal(committed).multiply(new BigDecimal("100.00")));
        assertThat(targetBalance.version()).isEqualTo(committed);
    }

    private static LedgerEntryEntity entry(UUID postingId, UUID accountId, String debit, BigDecimal credit) {
        return entry(postingId, accountId, new BigDecimal(debit), credit);
    }

    private static LedgerEntryEntity entry(UUID postingId, UUID accountId, BigDecimal debit, String credit) {
        return entry(postingId, accountId, debit, new BigDecimal(credit));
    }

    private static LedgerEntryEntity entry(UUID postingId, UUID accountId, BigDecimal debit, BigDecimal credit) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.setEntryId(UUID.randomUUID());
        entity.setPostingId(postingId);
        entity.setAccountId(accountId);
        entity.setDebit(debit);
        entity.setCredit(credit);
        entity.setAmount(debit.add(credit));
        entity.setCurrency("EUR");
        entity.setPostedAt(Instant.now());
        return entity;
    }
}
