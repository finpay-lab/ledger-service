package com.finpay.ledger.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class AccountBalanceTest {

    @Test
    void open_starts_at_zero_with_version_zero() {
        AccountBalance balance = AccountBalance.open(UUID.randomUUID(), "EUR");

        assertThat(balance.balance()).isEqualByComparingTo("0");
        assertThat(balance.currency()).isEqualTo("EUR");
        assertThat(balance.version()).isZero();
        assertThat(balance.updatedAt()).isNotNull();
    }

    @Test
    void credit_increases_and_debit_decreases_the_balance() {
        UUID accountId = UUID.randomUUID();
        AccountBalance balance = AccountBalance.open(accountId, "EUR");

        balance.apply(LedgerEntry.credit(accountId, new BigDecimal("150.00"), "EUR", Instant.now()));
        balance.apply(LedgerEntry.debit(accountId, new BigDecimal("40.00"), "EUR", Instant.now()));
        balance.apply(LedgerEntry.credit(accountId, new BigDecimal("10.00"), "EUR", Instant.now()));

        // balance = SUM(credit) - SUM(debit) = (150.00 + 10.00) - 40.00
        assertThat(balance.balance()).isEqualByComparingTo("120.00");
        // version mirrors last-known persisted state; the bump is Hibernate-owned
        assertThat(balance.version()).isZero();
    }

    @Test
    void applying_an_entry_for_another_account_or_currency_is_rejected() {
        AccountBalance balance = AccountBalance.open(UUID.randomUUID(), "EUR");

        assertThatThrownBy(() -> balance.apply(LedgerEntry.credit(UUID.randomUUID(), new BigDecimal("1.00"), "EUR", Instant.now())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match balance account");
        assertThatThrownBy(() -> balance.apply(LedgerEntry.credit(balance.accountId(), new BigDecimal("1.00"), "USD", Instant.now())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void open_rejects_unknown_currency() {
        assertThatThrownBy(() -> AccountBalance.open(UUID.randomUUID(), "XYZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
