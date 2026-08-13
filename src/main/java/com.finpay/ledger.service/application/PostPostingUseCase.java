package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: post a double-entry journal entry. Idempotent by {@code idempotencyKey}
 * (Rule 6): a repeated key returns the existing posting without side effects;
 * the unique constraint on {@code idempotency_key} is the DB backstop. The
 * aggregate and its outbox rows are written in one transaction (ADR-0004);
 * nothing is published to Kafka here (Rule 5).
 */
@Service
public class PostPostingUseCase {

    private final PostingRepository postingRepository;
    private final DomainEventPublisher eventPublisher;

    public PostPostingUseCase(PostingRepository postingRepository, DomainEventPublisher eventPublisher) {
        this.postingRepository = postingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PostPostingResult post(PostPostingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        var existing = postingRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            // Idempotent replay: no new aggregate, no new events.
            return new PostPostingResult(existing.orElseThrow(), false);
        }
        Posting posting = Posting.post(command.legs(), command.currency(), command.idempotencyKey());
        Posting saved = postingRepository.save(posting);
        // Same transaction: aggregate + outbox rows commit together.
        eventPublisher.publish(posting.pullDomainEvents());
        return new PostPostingResult(saved, true);
    }
}