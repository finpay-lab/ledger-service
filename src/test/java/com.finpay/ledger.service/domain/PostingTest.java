package com.finpay.ledger.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

class PostingTest {

    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();

    private static PostingLeg leg(UUID accountId, EntrySide side, String amount) {
        return new PostingLeg(accountId, side, new BigDecimal(amount));
    }

    private static List<PostingLeg> balancedLegs() {
        return List.of(leg(ACCOUNT_A, EntrySide.DEBIT, "150.00"), leg(ACCOUNT_B, EntrySide.CREDIT, "150.00"));
    }

    @Test
    void post_creates_balanced_posting_and_emits_one_event_per_entry() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "key-1");

        assertThat(posting.postingId()).isNotNull();
        assertThat(posting.currency()).isEqualTo("EUR");
        assertThat(posting.status()).isEqualTo(PostingStatus.POSTED);
        assertThat(posting.reversalOfPostingId()).isNull();
        assertThat(posting.entries()).hasSize(2);
        assertThat(posting.entries().stream().map(LedgerEntry::entryId)).doesNotContainNull();

        List<DomainEvent> events = posting.pullDomainEvents();
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.eventType().equals("LedgerEntryPosted"));
        assertThat(events).allMatch(e -> ((LedgerEntryPosted) e).postingId().equals(posting.postingId()));
    }

    @Test
    void post_rejects_imbalanced_posting() {
        List<PostingLeg> legs = List.of(
                leg(ACCOUNT_A, EntrySide.DEBIT, "150.00"),
                leg(ACCOUNT_B, EntrySide.CREDIT, "149.99"));

        assertThatThrownBy(() -> Posting.post(legs, "EUR", "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must balance");
    }

    @Test
    void post_rejects_single_leg_and_missing_side() {
        assertThatThrownBy(() -> Posting.post(
                List.of(leg(ACCOUNT_A, EntrySide.DEBIT, "10.00")), "EUR", "k"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Posting.post(
                List.of(leg(ACCOUNT_A, null, "10.00"), leg(ACCOUNT_B, EntrySide.CREDIT, "10.00")), "EUR", "k"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void post_rejects_missing_debit_or_credit_and_non_positive_amount() {
        assertThatThrownBy(() -> Posting.post(
                List.of(leg(ACCOUNT_A, EntrySide.DEBIT, "10.00"), leg(ACCOUNT_B, EntrySide.DEBIT, "10.00")),
                "EUR", "k"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Posting.post(
                List.of(leg(ACCOUNT_A, EntrySide.DEBIT, "0.00"), leg(ACCOUNT_B, EntrySide.CREDIT, "0.00")),
                "EUR", "k"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void post_normalizes_currency_and_rejects_unknown_codes() {
        assertThat(Posting.post(balancedLegs(), "eur", "k").currency()).isEqualTo("EUR");
        assertThatThrownBy(() -> Posting.post(balancedLegs(), "XYZ", "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Posting.post(balancedLegs(), "EURO", "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Posting.post(balancedLegs(), null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Posting.post(balancedLegs(), "EUR", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entries_are_immutable() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "k");

        assertThatThrownBy(() -> posting.entries().add(leg(ACCOUNT_A, EntrySide.DEBIT, "1.00")))
                .isInstanceOf(UnsupportedOperationException.class);
        // Listing one entry and trying to mutate it is impossible (record, no setters).
        assertThat(posting.entries().get(0).entryId()).isNotNull();
    }

    @Test
    void reversal_creates_offsetting_posting_and_marks_original_reversed() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "key-1");
        posting.pullDomainEvents();

        Posting reversal = Posting.createReversal(posting, "key-1:R", "booked twice");

        assertThat(reversal.reversalOfPostingId()).isEqualTo(posting.postingId());
        assertThat(reversal.status()).isEqualTo(PostingStatus.POSTED);
        assertThat(reversal.currency()).isEqualTo("EUR");
        // Inverted sides: the debit leg of the original becomes the credit leg of the reversal.
        LedgerEntry originalDebit = posting.entries().stream()
                .filter(e -> e.side() == EntrySide.DEBIT).findFirst().orElseThrow();
        LedgerEntry reversalCredit = reversal.entries().stream()
                .filter(e -> e.side() == EntrySide.CREDIT).findFirst().orElseThrow();
        assertThat(reversalCredit.amount()).isEqualByComparingTo(originalDebit.amount());
        assertThat(reversalCredit.accountId()).isEqualTo(originalDebit.accountId());
        // Reversal emits LedgerEntryPosted for each offsetting entry.
        assertThat(reversal.pullDomainEvents()).hasSize(2);
    }

    @Test
    void mark_reversed_emits_ledger_reversed() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "key-1");
        posting.pullDomainEvents();
        Posting reversal = Posting.createReversal(posting, "key-1:R", "booked twice");

        posting.markReversed(reversal.postingId(), "booked twice");

        assertThat(posting.status()).isEqualTo(PostingStatus.REVERSED);
        List<DomainEvent> events = posting.pullDomainEvents();
        assertThat(events).hasSize(1);
        LedgerReversed reversed = (LedgerReversed) events.get(0);
        assertThat(reversed.originalPostingId()).isEqualTo(posting.postingId());
        assertThat(reversed.reversalPostingId()).isEqualTo(reversal.postingId());
        assertThat(reversed.reason()).isEqualTo("booked twice");
        // Original entries untouched.
        assertThat(posting.entries()).hasSize(2);
    }

    @Test
    void already_reversed_posting_cannot_be_reversed_again() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "key-1");
        posting.pullDomainEvents();
        Posting reversal = Posting.createReversal(posting, "key-1:R", "first");
        posting.markReversed(reversal.postingId(), "first");
        posting.pullDomainEvents();
        reversal.pullDomainEvents();

        assertThatThrownBy(() -> Posting.createReversal(posting, "key-1:R2", "second"))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> posting.markReversed(UUID.randomUUID(), "second"))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void reversal_posting_cannot_itself_be_reversed() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "key-1");
        posting.pullDomainEvents();
        Posting reversal = Posting.createReversal(posting, "key-1:R", "booked twice");
        posting.markReversed(reversal.postingId(), "booked twice");
        reversal.pullDomainEvents();

        assertThatThrownBy(() -> Posting.createReversal(reversal, "key-1:R:R", "nope"))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void reversal_requires_reason_and_idempotency_key() {
        Posting posting = Posting.post(balancedLegs(), "EUR", "key-1");
        posting.pullDomainEvents();

        assertThatThrownBy(() -> Posting.createReversal(posting, "key-1:R", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Posting.createReversal(posting, null, "booked twice"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}