package com.finpay.ledger.service.infrastructure.jpa;

import com.finpay.ledger.service.domain.PostingStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for the {@code ledger_postings} table (V1__create_ledger.sql). */
@Entity
@Table(name = "ledger_postings")
public class PostingEntity {

    @Id
    @Column(name = "posting_id")
    private UUID postingId;

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PostingStatus status;

    @Column(name = "reversal_of_posting_id")
    private UUID reversalOfPostingId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "debit_total", nullable = false)
    private BigDecimal debitTotal;

    @Column(name = "credit_total", nullable = false)
    private BigDecimal creditTotal;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Optimistic lock; incremented by Hibernate on every flush. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PostingEntity() {
        // JPA
    }

    public UUID getPostingId() {
        return postingId;
    }

    public void setPostingId(UUID postingId) {
        this.postingId = postingId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PostingStatus getStatus() {
        return status;
    }

    public void setStatus(PostingStatus status) {
        this.status = status;
    }

    public UUID getReversalOfPostingId() {
        return reversalOfPostingId;
    }

    public void setReversalOfPostingId(UUID reversalOfPostingId) {
        this.reversalOfPostingId = reversalOfPostingId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getDebitTotal() {
        return debitTotal;
    }

    public void setDebitTotal(BigDecimal debitTotal) {
        this.debitTotal = debitTotal;
    }

    public BigDecimal getCreditTotal() {
        return creditTotal;
    }

    public void setCreditTotal(BigDecimal creditTotal) {
        this.creditTotal = creditTotal;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}