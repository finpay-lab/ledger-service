package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.EntrySide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One leg of a posting request. {@code amount} is a decimal string with at most
 * 2 fractional digits (currency minor units).
 */
public record PostingLegRequest(
        @NotNull UUID accountId,
        @NotNull EntrySide side,
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
