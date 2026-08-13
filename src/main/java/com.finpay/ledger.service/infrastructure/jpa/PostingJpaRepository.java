package com.finpay.ledger.service.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostingJpaRepository extends JpaRepository<PostingEntity, UUID> {

    Optional<PostingEntity> findByIdempotencyKey(String idempotencyKey);
}