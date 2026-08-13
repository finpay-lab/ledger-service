package com.finpay.ledger.service.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerAccountBalanceJpaRepository extends JpaRepository<LedgerAccountBalanceEntity, UUID> {
}
