package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.application.GetPostingUseCase;
import com.finpay.ledger.service.application.PostPostingCommand;
import com.finpay.ledger.service.application.PostPostingUseCase;
import com.finpay.ledger.service.application.ReversePostingCommand;
import com.finpay.ledger.service.application.ReversePostingUseCase;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST transport ↔ use case mapping only (Rule 3). All business logic lives in
 * the application/domain layers. Errors use common-web problem details.
 */
@RestController
@RequestMapping("/api/v1/postings")
public class PostingController {

    private final PostPostingUseCase postPostingUseCase;
    private final GetPostingUseCase getPostingUseCase;
    private final ReversePostingUseCase reversePostingUseCase;

    public PostingController(
            PostPostingUseCase postPostingUseCase,
            GetPostingUseCase getPostingUseCase,
            ReversePostingUseCase reversePostingUseCase) {
        this.postPostingUseCase = postPostingUseCase;
        this.getPostingUseCase = getPostingUseCase;
        this.reversePostingUseCase = reversePostingUseCase;
    }

    @PostMapping
    public ResponseEntity<PostingResponse> post(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostPostingRequest request) {
        var result = postPostingUseCase.post(
                new PostPostingCommand(request.legs(), request.currency(), idempotencyKey));
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PostingResponse.from(result.posting()));
    }

    @GetMapping("/{postingId}")
    public PostingResponse get(@PathVariable UUID postingId) {
        return PostingResponse.from(getPostingUseCase.get(postingId));
    }

    @PostMapping("/{postingId}/reversals")
    public ResponseEntity<ReversePostingResponse> reverse(
            @PathVariable UUID postingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReversePostingRequest request) {
        var result = reversePostingUseCase.reverse(
                new ReversePostingCommand(postingId, idempotencyKey, request.reason()));
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ReversePostingResponse.from(result.reversal(), result.original()));
    }
}