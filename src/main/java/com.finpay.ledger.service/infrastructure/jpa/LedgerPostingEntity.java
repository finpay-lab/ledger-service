package com.finpay.ledger.service.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code ledger_postings} table (V1 migration). Immutable
 * once committed; the idempotency key enforces Rule 6 at the DB as well.
 */
@Entity
@Table(name = "ledger_postings")
public class LedgerPostingEntity {

    @Id
    @Column(name = "posting_id")
    private UUID postingId;

    @Column(name = "reference", nullable = false, length = 128)
    private String reference;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public LedgerPostingEntity() {
        // JPA
    }

    public UUID getPostingId() {
        return postingId;
    }

    public void setPostingId(UUID postingId) {
        this.postingId = postingId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }
}
