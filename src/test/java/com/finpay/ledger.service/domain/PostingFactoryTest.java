package com.finpay.ledger.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PostingFactoryTest {

    private final PostingFactory factory = new PostingFactory();
    private static final Instant POSTED_AT = Instant.parse("2026-08-12T06:34:22Z");

    private EntrySpec leg(UUID accountId, EntrySide side, String amount) {
        return new EntrySpec(accountId, side, new BigDecimal(amount), "EUR");
    }

    @Test
    void balanced_two_leg_posting_is_created_immutably() {
        UUID debit = UUID.randomUUID();
        UUID credit = UUID.randomUUID();

        Posting posting = factory.recordPosting("transfer-42", List.of(
                leg(debit, EntrySide.DEBIT, "150.00"),
                leg(credit, EntrySide.CREDIT, "150.00")), POSTED_AT);

        assertThat(posting.businessRef()).isEqualTo("transfer-42");
        assertThat(posting.postedAt()).isEqualTo(POSTED_AT);
        assertThat(posting.postingId()).isNotNull();
        assertThat(posting.entries()).hasSize(2);
        assertThat(posting.totalDebits()).isEqualByComparingTo("150.00");
        assertThat(posting.totalCredits()).isEqualByComparingTo("150.00");

        Entry debitEntry = posting.entries().get(0);
        assertThat(debitEntry.accountId()).isEqualTo(debit);
        assertThat(debitEntry.side()).isEqualTo(EntrySide.DEBIT);
        assertThat(debitEntry.postingId()).isEqualTo(posting.postingId());
        assertThat(debitEntry.entryId()).isNotNull();

        assertThatThrownBy(() -> posting.entries().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unbalanced_posting_is_rejected() {
        assertThatThrownBy(() -> factory.recordPosting("unbalanced", List.of(
                leg(UUID.randomUUID(), EntrySide.DEBIT, "100.00"),
                leg(UUID.randomUUID(), EntrySide.CREDIT, "90.00")), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("Unbalanced");
    }

    @Test
    void single_leg_posting_is_rejected() {
        assertThatThrownBy(() -> factory.recordPosting("single", List.of(
                leg(UUID.randomUUID(), EntrySide.DEBIT, "100.00")), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("two legs");
    }

    @Test
    void multi_leg_balanced_posting_is_created() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        Posting posting = factory.recordPosting("multi", List.of(
                leg(a, EntrySide.DEBIT, "100.00"),
                leg(b, EntrySide.DEBIT, "50.00"),
                leg(c, EntrySide.CREDIT, "150.00")), POSTED_AT);

        assertThat(posting.totalDebits()).isEqualByComparingTo("150.00");
        assertThat(posting.totalCredits()).isEqualByComparingTo("150.00");
        assertThat(posting.entries()).hasSize(3);
    }

    @Test
    void currency_mismatch_is_rejected() {
        EntrySpec eur = leg(UUID.randomUUID(), EntrySide.DEBIT, "100.00");
        EntrySpec usd = new EntrySpec(UUID.randomUUID(), EntrySide.CREDIT, new BigDecimal("100.00"), "USD");

        assertThatThrownBy(() -> factory.recordPosting("mismatch", List.of(eur, usd), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    void zero_or_negative_amount_is_rejected() {
        EntrySpec zero = new EntrySpec(UUID.randomUUID(), EntrySide.DEBIT, BigDecimal.ZERO, "EUR");
        EntrySpec credit = leg(UUID.randomUUID(), EntrySide.CREDIT, "0.00");

        assertThatThrownBy(() -> factory.recordPosting("zero", List.of(zero, credit), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void blank_business_ref_is_rejected() {
        assertThatThrownBy(() -> factory.recordPosting("  ", List.of(
                leg(UUID.randomUUID(), EntrySide.DEBIT, "10.00"),
                leg(UUID.randomUUID(), EntrySide.CREDIT, "10.00")), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("businessRef");
    }

    @Test
    void each_posting_gets_unique_ids() {
        List<EntrySpec> legs = List.of(
                leg(UUID.randomUUID(), EntrySide.DEBIT, "10.00"),
                leg(UUID.randomUUID(), EntrySide.CREDIT, "10.00"));

        Posting first = factory.recordPosting("one", legs, POSTED_AT);
        Posting second = factory.recordPosting("two", legs, POSTED_AT);

        assertThat(first.postingId()).isNotEqualTo(second.postingId());
        assertThat(first.entries().get(0).entryId()).isNotEqualTo(second.entries().get(0).entryId());
    }
}