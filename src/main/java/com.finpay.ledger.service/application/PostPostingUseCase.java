package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.AccountBalance;
import com.finpay.ledger.service.domain.AccountBalanceRepository;
import com.finpay.ledger.service.domain.EntryLeg;
import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: post a double-entry transaction and update the affected account
 * balances. Idempotent by {@code idempotencyKey} (Rule 6): a repeated key
 * returns the stored posting with no side effects; the unique constraint on
 * {@code idempotency_key} is the DB backstop for simultaneous duplicates.
 *
 * The posting and every balance update are written in one transaction (no
 * remote calls inside, Rule 5). Optimistic locking on the balance rows
 * (optimistic-lock version) prevents concurrent postings from losing updates:
 * the loser fails on commit. The DB invariant SUM(debit)=SUM(credit) is the
 * final backstop, checked by deferred constraint triggers at COMMIT.
 */
@Service
public class PostPostingUseCase {

    private final PostingRepository postingRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public PostPostingUseCase(PostingRepository postingRepository, AccountBalanceRepository accountBalanceRepository) {
        this.postingRepository = postingRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Transactional
    public PostPostingResult post(PostPostingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required (Rule 6)");
        }
        var existing = postingRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            // Idempotent replay: no new posting, no balance deltas.
            return new PostPostingResult(existing.orElseThrow(), false);
        }
        Posting posting = Posting.create(command.reference(), command.currency(), command.idempotencyKey(), command.legs());
        for (LedgerEntry entry : posting.entries()) {
            AccountBalance balance = accountBalanceRepository.findById(entry.accountId())
                    .orElseGet(() -> AccountBalance.open(entry.accountId(), entry.currency()));
            balance.apply(entry);
            accountBalanceRepository.save(balance);
        }
        postingRepository.save(posting);
        return new PostPostingResult(posting, true);
    }
}
