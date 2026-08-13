package com.finpay.ledger.service.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code ledger_entries} table (V1 migration). Entries are
 * immutable; the DB {@code chk_entry_single_side} CHECK plus the deferred
 * posting-balance and account-balance-consistency triggers enforce the ledger
 * invariants regardless of the code path that wrote the rows.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "debit", nullable = false, precision = 38, scale = 2)
    private BigDecimal debit;

    @Column(name = "credit", nullable = false, precision = 38, scale = 2)
    private BigDecimal credit;

    @Column(name = "amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public LedgerEntryEntity() {
        // JPA
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getPostingId() {
        return postingId;
    }

    public void setPostingId(UUID postingId) {
        this.postingId = postingId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(BigDecimal debit) {
        this.debit = debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }
}
