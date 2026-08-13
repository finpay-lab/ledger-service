package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.EntrySide;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** POST /api/v1/postings body — double-entry legs of a new posting. */
public record PostPostingRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO-4217 code")
        String currency,

        @NotEmpty
        List<@Valid Leg> legs
) {

    public record Leg(
            @NotNull UUID accountId,
            @NotNull EntrySide side,
            @NotNull @DecimalMin(value = "0.0001", message = "amount must be positive") BigDecimal amount
    ) {
    }
}