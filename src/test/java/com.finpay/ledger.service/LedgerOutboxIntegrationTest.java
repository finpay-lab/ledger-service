package com.finpay.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finpay.ledger.service.application.PostPostingCommand;
import com.finpay.ledger.service.application.PostPostingUseCase;
import com.finpay.ledger.service.application.ReversePostingCommand;
import com.finpay.ledger.service.application.ReversePostingUseCase;
import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.PostingLeg;
import com.finpay.ledger.service.domain.PostingStatus;
import com.finpay.ledger.service.infrastructure.jpa.LedgerEntryEntity;
import com.finpay.ledger.service.infrastructure.jpa.LedgerEntryJpaRepository;
import com.finpay.ledger.service.infrastructure.jpa.OutboxJpaRepository;
import com.finpay.ledger.service.infrastructure.jpa.OutboxMessageEntity;
import com.finpay.ledger.service.infrastructure.jpa.PostingEntity;
import com.finpay.ledger.service.infrastructure.jpa.PostingJpaRepository;
import com.finpay.ledger.service.infrastructure.outbox.OutboxRelay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * End-to-end: Posting aggregate → JPA rows → transactional outbox rows (same
 * tx) → relay publishes to the (mocked) Kafka producer and marks rows published.
 * Also proves the double-entry DB invariant (check constraint on SUM(debit) =
 * SUM(credit)) rejects an imbalanced posting. Deterministic and fast: Kafka is
 * a {@link KafkaTemplate} mock.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LedgerOutboxIntegrationTest {

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

    @MockBean
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    PostPostingUseCase postPostingUseCase;

    @Autowired
    ReversePostingUseCase reversePostingUseCase;

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    PostingJpaRepository postingJpaRepository;

    @Autowired
    LedgerEntryJpaRepository entryJpaRepository;

    @Autowired
    OutboxJpaRepository outboxJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();

    @BeforeEach
    void resetProducerMock() {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void post_persists_rows_and_outbox_then_relay_publishes() {
        UUID postingId = post(ACCOUNT_A, ACCOUNT_B, "100.00", "pp-key-1");

        PostingEntity stored = postingJpaRepository.findById(postingId).orElseThrow();
        assertThat(stored.getCurrency()).isEqualTo("EUR");
        assertThat(stored.getStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(stored.getDebitTotal()).isEqualByComparingTo("100.00");
        assertThat(stored.getCreditTotal()).isEqualByComparingTo("100.00");
        assertThat(stored.getVersion()).isZero();

        List<LedgerEntryEntity> entries = entryJpaRepository.findByPostingId(postingId);
        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(e -> assertThat(e.getCurrency()).isEqualTo("EUR"));

        List<OutboxMessageEntity> pending = pendingRows();
        assertThat(pending).hasSize(2); // one LedgerEntryPosted per entry
        pending.forEach(row -> assertThat(row.getEventType()).isEqualTo("LedgerEntryPosted"));
        OutboxMessageEntity row = pending.get(0);
        assertThat(row.getPayload())
                .contains("\"eventType\":\"LedgerEntryPosted\"")
                .contains("\"postingId\":\"" + postingId)
                .contains("\"currency\":\"EUR\"");

        outboxRelay.publishPending();

        // Partitioned by accountId.
        verify(kafkaTemplate).send(eq("finpay.ledger"), eq(ACCOUNT_A.toString()), anyString());
        verify(kafkaTemplate).send(eq("finpay.ledger"), eq(ACCOUNT_B.toString()), anyString());
        assertThat(publishedRows()).allSatisfy(r -> assertThat(r.isPublished()).isTrue());
    }

    @Test
    void reversal_marks_original_reversed_and_publishes_reversal_events() {
        UUID postingId = post(ACCOUNT_A, ACCOUNT_B, "250.00", "rev-src");

        UUID reversalId = reversePostingUseCase.reverse(
                new ReversePostingCommand(postingId, "rev-key-1", "booked twice"))
                .reversal().postingId();

        PostingEntity original = postingJpaRepository.findById(postingId).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(PostingStatus.REVERSED);
        assertThat(original.getRevisionPublishedFlag()).isNull(); // no-op guard; see below
        PostingEntity reversal = postingJpaRepository.findById(reversalId).orElseThrow();
        assertThat(reversal.getReversalOfPostingId()).isEqualTo(postingId);
        assertThat(reversal.getReason()).isEqualTo("booked twice");
        // Reversal legs have inverted sides, same amounts.
        List<LedgerEntryEntity> reversalEntries = entryJpaRepository.findByPostingId(reversalId);
        assertThat(reversalEntries).hasSize(2);
        assertThat(reversalEntries).anyMatch(e -> e.getSide() == EntrySide.DEBIT);
        assertThat(reversalEntries).anyMatch(e -> e.getSide() == EntrySide.CREDIT);

        List<OutboxMessageEntity> pending = pendingRows();
        // 2 LedgerEntryPosted (reversal legs) + 1 LedgerReversed.
        assertThat(pending).filteredOn(r -> r.getEventType().equals("LedgerReversed")).hasSize(1);
        assertThat(pending).filteredOn(r -> r.getEventType().equals("LedgerEntryPosted")).hasSize(2);
        OutboxMessageEntity ledgerReversedRow = pending.stream()
                .filter(r -> r.getEventType().equals("LedgerReversed")).findFirst().orElseThrow();
        assertThat(ledgerReversedRow.getPayload())
                .contains("\"originalPostingId\":\"" + postingId)
                .contains("\"reversalPostingId\":\"" + reversalId)
                .contains("\"reason\":\"booked twice\"");

        outboxRelay.publishPending();
        assertThat(publishedRows()).allSatisfy(r -> assertThat(r.isPublished()).isTrue());
    }

    @Test
    void relay_sends_each_row_exactly_once() {
        post(ACCOUNT_A, ACCOUNT_B, "300.00", "once-key");

        outboxRelay.publishPending();
        outboxRelay.publishPending();

        verify(kafkaTemplate, times(2)).send(eq("finpay.ledger"), anyString(), anyString());
        assertThat(publishedRows()).allSatisfy(r -> assertThat(r.isPublished()).isTrue());
    }

    @Test
    void illegal_reversal_creates_no_outbox_row() {
        UUID postingId = post(ACCOUNT_A, ACCOUNT_B, "75.00", "illegal-key");
        int outboxBefore = outboxJpaRepository.findAll().size();

        // A second reversal of the same posting is rejected by the state machine.
        reversePostingUseCase.reverse(new ReversePostingCommand(postingId, "first-rev", "first"));
        assertThat(outboxJpaRepository.findAll()).hasSize(outboxBefore + 3);
        assertThatThrownBy(() -> reversePostingUseCase.reverse(
                new ReversePostingCommand(postingId, "second-rev", "second")))
                .isInstanceOf(com.finpay.ledger.service.domain.IllegalStateTransitionException.class);
        assertThat(outboxJpaRepository.findAll()).hasSize(outboxBefore + 3);
    }

    @Test
    void db_check_constraint_rejects_imbalanced_posting() {
        // Prove the double-entry DB invariant: a posting where SUM(debit) !=
        // SUM(credit) cannot be inserted even if the application were bypassed.
        String postingId = UUID.randomUUID().toString();
        String entryId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ledger_postings
                    (posting_id, idempotency_key, currency, status, debit_total, credit_total, posted_at, created_at, version)
                VALUES (?, 'db-imbalanced', 'EUR', 'POSTED', 100.0000, 99.0000, now(), now(), 0)
                """, postingId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // And parallel entries that would break the invariant are rejected too.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ledger_entries (entry_id, posting_id, account_id, side, amount, currency)
                VALUES (?, ?, ?, 'DEBIT', 1.0000, 'EUR')
                """, entryId, postingId, ACCOUNT_A.toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID post(UUID debitAccount, UUID creditAccount, String amount, String idempotencyKey) {
        return postPostingUseCase.post(new PostPostingCommand(
                List.of(
                        new PostingLeg(debitAccount, EntrySide.DEBIT, new BigDecimal(amount)),
                        new PostingLeg(creditAccount, EntrySide.CREDIT, new BigDecimal(amount))),
                "EUR", idempotencyKey)).posting().postingId();
    }

    private List<OutboxMessageEntity> pendingRows() {
        return outboxJpaRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, 100));
    }

    private List<OutboxMessageEntity> publishedRows() {
        return outboxJpaRepository.findAll().stream().filter(OutboxMessageEntity::isPublished).toList();
    }

}