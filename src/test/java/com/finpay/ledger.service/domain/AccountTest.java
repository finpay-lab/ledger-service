package com.finpay.ledger.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AccountTest {

    private final UUID accountId = UUID.randomUUID();

    private Account openAccount() {
        return new Account(accountId, "Customer EUR wallet", "EUR", AccountType.ASSET,
                EntrySide.DEBIT, Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void account_starts_open_with_expected_fields() {
        Account account = openAccount();

        assertThat(account.id()).isEqualTo(accountId);
        assertThat(account.name()).isEqualTo("Customer EUR wallet");
        assertThat(account.currency()).isEqualTo("EUR");
        assertThat(account.type()).isEqualTo(AccountType.ASSET);
        assertThat(account.normalBalanceSide()).isEqualTo(EntrySide.DEBIT);
        assertThat(account.status()).isEqualTo(AccountStatus.OPEN);
        assertThat(account.isOpen()).isTrue();
    }

    @Test
    void legal_transitions_are_accepted() {
        Account account = openAccount();

        account.freeze();
        assertThat(account.status()).isEqualTo(AccountStatus.FROZEN);
        assertThat(account.isOpen()).isFalse();

        account.unfreeze();
        assertThat(account.status()).isEqualTo(AccountStatus.OPEN);

        account.freeze();
        account.close();
        assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void direct_close_from_open_is_legal() {
        Account account = openAccount();
        account.close();
        assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void illegal_transitions_are_rejected() {
        Account account = openAccount();
        account.close();

        assertThatThrownBy(account::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED -> CLOSED");
        assertThatThrownBy(account::freeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED -> FROZEN");
        assertThatThrownBy(account::unfreeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED -> OPEN");
    }

    @Test
    void self_transition_frozen_is_rejected() {
        Account account = openAccount();
        account.freeze();

        assertThatThrownBy(account::freeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FROZEN -> FROZEN");
    }

    @Test
    void rejects_invalid_currency() {
        assertThatThrownBy(() -> new Account(UUID.randomUUID(), "bad", "eu", AccountType.ASSET,
                EntrySide.DEBIT, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}