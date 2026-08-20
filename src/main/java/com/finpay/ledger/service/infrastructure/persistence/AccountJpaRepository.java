package com.finpay.ledger.service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, String> {}
