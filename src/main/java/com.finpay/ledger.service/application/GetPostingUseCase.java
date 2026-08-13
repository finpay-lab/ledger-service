package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingNotFoundException;
import com.finpay.ledger.service.domain.PostingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Use case: read a posting by id. Read-only, no events emitted. */
@Service
public class GetPostingUseCase {

    private final PostingRepository postingRepository;

    public GetPostingUseCase(PostingRepository postingRepository) {
        this.postingRepository = postingRepository;
    }

    @Transactional(readOnly = true)
    public Posting get(UUID postingId) {
        if (postingId == null) {
            throw new IllegalArgumentException("postingId is required");
        }
        return postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));
    }
}