package com.finpay.ledger.service.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Idempotency keys for postings (Rule 6). */
@Entity
@Table(name = "posting_idempotency")
public class IdempotencyEntity {
    @Id
    private String idempotencyKey;
    private String transactionId;

    public IdempotencyEntity() {}
    public IdempotencyEntity(String key, String txn) { this.idempotencyKey = key; this.transactionId = txn; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
}
