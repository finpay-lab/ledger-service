package com.finpay.ledger.service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface EntryJpaRepository extends JpaRepository<EntryEntity, String> {

    List<EntryEntity> findByAccountIdOrderByPostedAtAsc(String accountId);

    @Query("select coalesce(sum(case when e.type = 'CREDIT' then e.amount else e.amount.negate() end), 0) " +
           "from EntryEntity e where e.accountId = :accountId")
    BigDecimal balanceOf(String accountId);
}
