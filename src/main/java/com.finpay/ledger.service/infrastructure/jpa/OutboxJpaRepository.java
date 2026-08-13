package com.finpay.ledger.service.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessageEntity, UUID> {

    List<OutboxMessageEntity> findByPublishedFalseOrderByCreatedAtAsc(org.springframework.data.domain.Pageable pageable);
}