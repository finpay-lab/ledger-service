package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.AccountNotFoundException;
import com.finpay.ledger.service.domain.AccountRepository;
import com.finpay.ledger.service.domain.EntrySpec;
import com.finpay.ledger.service.domain.IllegalPostingException;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingFactory;
import com.finpay.ledger.service.domain.PostingRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Use case: record an immutable double-entry posting. Idempotent by
 * {@code businessRef} (Rule 6): a replayed reference returns the already
 * committed posting instead of creating a duplicate. Validates that every leg
 * references an existing, open account in the matching currency; the balanced
 * double-entry invariant itself is enforced by {@link PostingFactory}.
 */
public final class RecordPostingUseCase {

    private final PostingRepository postings;
    private final AccountRepository accounts;
    private final PostingFactory postingFactory;

    public RecordPostingUseCase(PostingRepository postings, AccountRepository accounts,
                                PostingFactory postingFactory) {
        this.postings = Objects.requireNonNull(postings, "postings");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.postingFactory = Objects.requireNonNull(postingFactory, "postingFactory");
    }

    public Posting record(String businessRef, List<EntrySpec> legs, Instant postedAt) {
        var existing = postings.findByBusinessRef(businessRef);
        if (existing.isPresent()) {
            return existing.get();
        }

        for (EntrySpec leg : legs) {
            var account = accounts.findById(leg.accountId())
                    .orElseThrow(() -> new AccountNotFoundException(leg.accountId()));
            if (!account.isOpen()) {
                throw new IllegalPostingException(
                        "Account " + leg.accountId() + " is " + account.status() + ", not OPEN");
            }
            if (!account.currency().equals(leg.currency())) {
                throw new IllegalPostingException(
                        "Posting currency " + leg.currency() + " does not match account "
                                + leg.accountId() + " currency " + account.currency());
            }
        }

        Posting posting = postingFactory.recordPosting(businessRef, legs, postedAt);
        postings.save(posting);
        return posting;
    }
}