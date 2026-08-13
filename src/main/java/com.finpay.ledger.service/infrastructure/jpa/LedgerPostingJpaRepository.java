package com.finpay.ledger.service.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerPostingJpaRepository extends JpaRepository<LedgerPostingEntity, UUID> {

    Optional<LedgerPostingEntity> findByIdempotencyKey(String idempotencyKey);
}
