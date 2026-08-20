package com.finpay.ledger.service.infrastructure.persistence;

import com.finpay.ledger.service.domain.Entry;
import com.finpay.ledger.service.domain.Entry.EntryType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class EntryEntity {

    @Id
    private String entryId;
    private String accountId;
    private String type;            // DEBIT | CREDIT
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private Instant postedAt;

    public EntryEntity() {}

    public static EntryEntity from(Entry e) {
        EntryEntity en = new EntryEntity();
        en.entryId = e.entryId();
        en.accountId = e.accountId();
        en.type = e.type().name();
        en.amount = e.amount();
        en.currency = e.currency();
        en.transactionId = e.transactionId();
        en.postedAt = e.postedAt();
        return en;
    }

    public String getEntryId() { return entryId; }
    public void setEntryId(String v) { this.entryId = v; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String v) { this.accountId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant v) { this.postedAt = v; }
}
