package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.application.GetBalanceUseCase;
import com.finpay.ledger.service.application.PostPostingCommand;
import com.finpay.ledger.service.application.PostPostingUseCase;

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
@RequestMapping("/api/v1")
public class LedgerController {

    private final PostPostingUseCase postPostingUseCase;
    private final GetBalanceUseCase getBalanceUseCase;

    public LedgerController(PostPostingUseCase postPostingUseCase, GetBalanceUseCase getBalanceUseCase) {
        this.postPostingUseCase = postPostingUseCase;
        this.getBalanceUseCase = getBalanceUseCase;
    }

    @PostMapping("/postings")
    public ResponseEntity<PostingResponse> post(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostingRequest request) {
        var result = postPostingUseCase.post(new PostPostingCommand(
                request.reference(),
                request.currency(),
                idempotencyKey,
                request.toEntryLegs()));
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PostingResponse.from(result.posting(), result.created()));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse balance(@PathVariable UUID accountId) {
        var balance = getBalanceUseCase.get(accountId);
        return new BalanceResponse(balance.accountId(), balance.currency(), balance.balance(), balance.version());
    }
}
