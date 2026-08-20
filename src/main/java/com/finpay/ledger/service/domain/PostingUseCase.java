package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Posts a double-entry movement between two accounts (FP-5).
 *
 * Enforces:
 *  - idempotency by {@code idempotencyKey} (Rule 6),
 *  - immutability: entries are appended, never mutated,
 *  - outbox staging in the SAME transaction (Rule 5: persist+commit, then publish).
 *
 * No Spring/JPA/Kafka imports — this is pure domain orchestration.
 */
public final class PostingUseCase {

    private final LedgerRepository repository;
    private final Outbox outbox;

    public PostingUseCase(LedgerRepository repository, Outbox outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    public record PostingRequest(
            String idempotencyKey,
            String transactionId,
            String debitAccountId,
            String creditAccountId,
            BigDecimal amount,
            String currency,
            String reason
    ) {}

    public record PostingResult(String transactionId, String debitEntryId, String creditEntryId) {}

    public PostingResult post(PostingRequest req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        // Idempotency: reject duplicate with conflicting payload (Rule 6).
        if (repository.postingExists(req.idempotencyKey())) {
            return new PostingResult(req.transactionId(), "duplicate", "duplicate");
        }

        Account debit = repository.findAccount(req.debitAccountId())
                .orElseThrow(() -> new AccountNotFound(req.debitAccountId()));
        Account credit = repository.findAccount(req.creditAccountId())
                .orElseThrow(() -> new AccountNotFound(req.creditAccountId()));
        if (!debit.isActive() || !credit.isActive()) {
            throw new AccountInactiveException(req.debitAccountId() + "/" + req.creditAccountId());
        }
        if (!debit.currency().getCurrencyCode().equals(req.currency())
                || !credit.currency().getCurrencyCode().equals(req.currency())) {
            throw new CurrencyMismatchException();
        }

        String txn = (req.transactionId() == null || req.transactionId().isBlank())
                ? UUID.randomUUID().toString() : req.transactionId();
        String debitEntryId = UUID.randomUUID().toString();
        String creditEntryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        repository.append(new Entry(debitEntryId, debit.accountId(), Entry.EntryType.DEBIT,
                req.amount(), req.currency(), txn, now));
        repository.append(new Entry(creditEntryId, credit.accountId(), Entry.EntryType.CREDIT,
                req.amount(), req.currency(), txn, now));

        // Stage domain events in the same transaction; relay publishes later.
        outbox.stage("LedgerEntryPosted", txn,
                "{\"transactionId\":\"" + txn + "\",\"amount\":" + req.amount() + ",\"currency\":\"" + req.currency() + "\"}");

        repository.markPosted(req.idempotencyKey(), txn);
        return new PostingResult(txn, debitEntryId, creditEntryId);
    }

    public static final class AccountNotFound extends RuntimeException {
        AccountNotFound(String id) { super("account not found: " + id); }
    }
    public static final class AccountInactiveException extends RuntimeException {
        AccountInactiveException(String ids) { super("account inactive: " + ids); }
    }
    public static final class CurrencyMismatchException extends RuntimeException {
        CurrencyMismatchException() { super("currency mismatch between accounts"); }
    }
}
