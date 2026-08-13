package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.AccountBalance;
import com.finpay.ledger.service.domain.AccountBalanceNotFoundException;
import com.finpay.ledger.service.domain.AccountBalanceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case: read the current ledger balance of an account. The balance is
 * always derived from — and asserted equal to — the account's immutable entry
 * stream (V1 DB invariant).
 */
@Service
public class GetBalanceUseCase {

    private final AccountBalanceRepository accountBalanceRepository;

    public GetBalanceUseCase(AccountBalanceRepository accountBalanceRepository) {
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Transactional(readOnly = true)
    public AccountBalance get(UUID accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        return accountBalanceRepository.findById(accountId)
                .orElseThrow(() -> new AccountBalanceNotFoundException(accountId));
    }
}
