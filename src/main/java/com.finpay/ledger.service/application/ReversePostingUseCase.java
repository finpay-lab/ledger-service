package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.DomainEvent;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingNotFoundException;
import com.finpay.ledger.service.domain.PostingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Use case: reverse a posting. Reversal never deletes the original rows; it
 * posts an offsetting reversal posting and marks the original
 * {@code REVERSED} (Rule 9 — re-reversals are rejected). Idempotent by
 * {@code idempotencyKey} (Rule 6): a repeated key returns the existing reversal
 * without side effects. Both aggregates and their outbox rows commit together.
 */
@Service
public class ReversePostingUseCase {

    private final PostingRepository postingRepository;
    private final DomainEventPublisher eventPublisher;

    public ReversePostingUseCase(PostingRepository postingRepository, DomainEventPublisher eventPublisher) {
        this.postingRepository = postingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ReversePostingResult reverse(ReversePostingCommand command) {
        if (command == null || command.postingId() == null) {
            throw new IllegalArgumentException("postingId is required");
        }
        var existing = postingRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            Posting found = existing.orElseThrow();
            if (found.reversalOfPostingId() == null) {
                throw new IllegalArgumentException("idempotency key already used for a different operation");
            }
            Posting original = postingRepository.findById(found.reversalOfPostingId())
                    .orElseThrow(() -> new PostingNotFoundException(found.reversalOfPostingId()));
            return new ReversePostingResult(found, original, false);
        }
        Posting original = postingRepository.findById(command.postingId())
                .orElseThrow(() -> new PostingNotFoundException(command.postingId()));
        Posting reversal = Posting.createReversal(original, command.idempotencyKey(), command.reason());
        original.markReversed(reversal.postingId(), command.reason());

        Posting savedOriginal = postingRepository.save(original);
        Posting savedReversal = postingRepository.save(reversal);
        List<DomainEvent> events = new ArrayList<>();
        events.addAll(reversal.pullDomainEvents());
        events.addAll(original.pullDomainEvents());
        eventPublisher.publish(events);
        return new ReversePostingResult(savedReversal, savedOriginal, true);
    }
}