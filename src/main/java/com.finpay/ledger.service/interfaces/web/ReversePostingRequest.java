package com.finpay.ledger.service.interfaces.web;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/postings/{postingId}/reversals body. */
public record ReversePostingRequest(
        @NotBlank(message = "reason is required") String reason
) {
}