package com.finpay.ledger.service.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A ledger account: the accounting reference a double-entry posting is recorded
 * against. State transitions are restricted to the {@link AccountStatus} state
 * machine (Rule 9); the ledger is the source of truth for money movement, so a
 * frozen or closed account must not accept postings.
 */
public final class Account {

    private final UUID id;
    private final String name;
    private final String currency;
    private final AccountType type;
    private final EntrySide normalBalanceSide;
    private final Instant createdAt;
    private AccountStatus status;

    public Account(UUID id, String name, String currency, AccountType type,
                   EntrySide normalBalanceSide, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireNonBlank(name, "name");
        Currencies.requireIso4217(currency);
        this.currency = currency;
        this.type = Objects.requireNonNull(type, "type");
        this.normalBalanceSide = Objects.requireNonNull(normalBalanceSide, "normalBalanceSide");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = AccountStatus.OPEN;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String currency() {
        return currency;
    }

    public AccountType type() {
        return type;
    }

    public EntrySide normalBalanceSide() {
        return normalBalanceSide;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public AccountStatus status() {
        return status;
    }

    public boolean isOpen() {
        return status == AccountStatus.OPEN;
    }

    public void freeze() {
        transitionTo(AccountStatus.FROZEN);
    }

    public void unfreeze() {
        transitionTo(AccountStatus.OPEN);
    }

    public void close() {
        transitionTo(AccountStatus.CLOSED);
    }

    private void transitionTo(AccountStatus target) {
        status.requireTransitionTo(target);
        this.status = target;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof Account other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Account{id=" + id + ", name='" + name + "', currency=" + currency
                + ", type=" + type + ", status=" + status + '}';
    }
}