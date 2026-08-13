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

    private static EntryLeg leg(UUID accountId, EntrySide side, String amount) {
        return new EntryLeg(accountId, side, new BigDecimal(amount));
    }

    @Test
    void balanced_posting_is_created_with_single_sided_entries() {
        Posting posting = Posting.create("transfer-1", "EUR", "key-1", List.of(
                leg(ACCOUNT_A, EntrySide.DEBIT, "100.00"),
                leg(ACCOUNT_B, EntrySide.CREDIT, "100.00")));

        assertThat(posting.postingId()).isNotNull();
        assertThat(posting.reference()).isEqualTo("transfer-1");
        assertThat(posting.currency()).isEqualTo("EUR");
        assertThat(posting.idempotencyKey()).isEqualTo("key-1");
        assertThat(posting.entries()).hasSize(2);

        LedgerEntry debit = posting.entries().get(0);
        LedgerEntry credit = posting.entries().get(1);
        assertThat(debit.accountId()).isEqualTo(ACCOUNT_A);
        assertThat(debit.debit()).isEqualByComparingTo("100.00");
        assertThat(debit.credit()).isEqualByComparingTo("0");
        assertThat(credit.accountId()).isEqualTo(ACCOUNT_B);
        assertThat(credit.credit()).isEqualByComparingTo("100.00");
        assertThat(credit.debit()).isEqualByComparingTo("0");
    }

    @Test
    void imbalanced_posting_is_rejected_and_never_created() {
        // SUM(debit)=100.00 vs SUM(credit)=50.00 -> invariant violation.
        assertThatThrownBy(() -> Posting.create("bad", "EUR", "key-2", List.of(
                leg(ACCOUNT_A, EntrySide.DEBIT, "100.00"),
                leg(ACCOUNT_B, EntrySide.CREDIT, "50.00"))))
                .isInstanceOf(UnbalancedPostingException.class)
                .hasMessageContaining("SUM(debit)=100.00")
                .hasMessageContaining("SUM(credit)=50.00");
    }

    @Test
    void posting_with_only_debit_legs_is_rejected() {
        assertThatThrownBy(() -> Posting.create("bad", "EUR", "key-3", List.of(
                leg(ACCOUNT_A, EntrySide.DEBIT, "10.00"),
                leg(ACCOUNT_B, EntrySide.DEBIT, "5.00"))))
                .isInstanceOf(UnbalancedPostingException.class);
    }

    @Test
    void posting_requires_at_least_two_legs() {
        assertThatThrownBy(() -> Posting.create("single", "EUR", "key-4",
                List.of(leg(ACCOUNT_A, EntrySide.DEBIT, "10.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one debit and one credit leg");
    }

    @Test
    void posting_requires_idempotency_key_and_valid_currency() {
        List<EntryLeg> legs = List.of(
                leg(ACCOUNT_A, EntrySide.DEBIT, "10.00"),
                leg(ACCOUNT_B, EntrySide.CREDIT, "10.00"));
        assertThatThrownBy(() -> Posting.create("r", "EUR", null, legs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
        assertThatThrownBy(() -> Posting.create("r", "EUR", " ", legs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
        assertThatThrownBy(() -> Posting.create("r", "XYZ", "k", legs))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Posting.create("r", "EURO", "k", legs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legs_must_be_positive_and_single_sided() {
        assertThatThrownBy(() -> new EntryLeg(ACCOUNT_A, EntrySide.DEBIT, new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntryLeg(ACCOUNT_A, EntrySide.DEBIT, new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntryLeg(ACCOUNT_A, EntrySide.DEBIT, new BigDecimal("1.005")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 2 decimal places");
    }

    @Test
    void currency_is_normalized_to_uppercase() {
        Posting posting = Posting.create("r", "eur", "k", List.of(
                leg(ACCOUNT_A, EntrySide.DEBIT, "10.00"),
                leg(ACCOUNT_B, EntrySide.CREDIT, "10.00")));
        assertThat(posting.currency()).isEqualTo("EUR");
        assertThat(posting.entries()).allSatisfy(e -> assertThat(e.currency()).isEqualTo("EUR"));
    }
}
