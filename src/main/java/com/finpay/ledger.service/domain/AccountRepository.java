package com.finpay.ledger.service.domain;

import java.util.Optional;
import java.util.UUID;

/** Persistence contract for ledger accounts. Implementations live in infrastructure. */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    void save(Account account);
}