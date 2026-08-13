package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.EntryLeg;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/postings}. The double-entry balance is
 * validated by the domain aggregate and enforced by the DB at COMMIT.
 */
public record PostingRequest(
        @NotBlank String reference,
        @NotBlank String currency,
        @NotEmpty List<@Valid PostingLegRequest> legs
) {

    public List<EntryLeg> toEntryLegs() {
        return legs.stream()
                .map(leg -> new EntryLeg(leg.accountId(), leg.side(), leg.amount()))
                .toList();
    }
}
