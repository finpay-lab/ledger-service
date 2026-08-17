package com.finpay.ledger.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EntryTest {

    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID POSTING_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant POSTED_AT = Instant.parse("2026-08-12T06:34:22Z");

    private Entry entry(BigDecimal amount) {
        return new Entry(ENTRY_ID, POSTING_ID, ACCOUNT_ID, EntrySide.DEBIT, amount, "EUR", POSTED_AT);
    }

    @Test
    void entry_exposes_all_immutable_fields() {
        Entry entry = entry(new BigDecimal("150.00"));

        assertThat(entry.entryId()).isEqualTo(ENTRY_ID);
        assertThat(entry.postingId()).isEqualTo(POSTING_ID);
        assertThat(entry.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(entry.side()).isEqualTo(EntrySide.DEBIT);
        assertThat(entry.amount()).isEqualByComparingTo("150.00");
        assertThat(entry.currency()).isEqualTo("EUR");
        assertThat(entry.postedAt()).isEqualTo(POSTED_AT);
        assertThat(entry.isDebit()).isTrue();
    }

    @Test
    void credit_entry_is_not_a_debit() {
        Entry entry = new Entry(ENTRY_ID, POSTING_ID, ACCOUNT_ID, EntrySide.CREDIT,
                new BigDecimal("150.00"), "EUR", POSTED_AT);
        assertThat(entry.isDebit()).isFalse();
    }

    @Test
    void zero_or_negative_amount_is_rejected() {
        assertThatThrownBy(() -> entry(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> entry(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_fields_are_rejected() {
        assertThatThrownBy(() -> new Entry(null, POSTING_ID, ACCOUNT_ID, EntrySide.DEBIT,
                new BigDecimal("1.00"), "EUR", POSTED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> entry(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void invalid_currency_is_rejected() {
        assertThatThrownBy(() -> new Entry(ENTRY_ID, POSTING_ID, ACCOUNT_ID, EntrySide.DEBIT,
                new BigDecimal("1.00"), "EURO", POSTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals_is_value_based() {
        Entry a = entry(new BigDecimal("150.00"));
        Entry b = entry(new BigDecimal("150.00"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);

        Entry c = new Entry(UUID.randomUUID(), POSTING_ID, ACCOUNT_ID, EntrySide.DEBIT,
                new BigDecimal("150.00"), "EUR", POSTED_AT);
        assertThat(a).isNotEqualTo(c);
    }
}