package com.finpay.ledger.service.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.ledger.service.domain.anomaly.LedgerPosting;
import com.finpay.ledger.service.domain.anomaly.TransferEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Maps the JSON event envelopes defined in {@code finpay-platform/contracts/events/v1}
 * ({@code LedgerEntryPosted}, {@code TransferCreated}/{@code TransferCompleted}/
 * {@code TransferFailed}) onto the domain records consumed by the anomaly
 * model. Lives in infrastructure so the domain never sees Jackson.
 */
public final class AnomalyEventParser {

    private final ObjectMapper objectMapper;

    public AnomalyEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LedgerPosting parseLedgerEntry(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payload = requiredNode(root, "payload");
            return new LedgerPosting(
                    requiredText(root, "eventId"),
                    requiredText(payload, "postingId"),
                    requiredText(payload, "accountId"),
                    decimal(requiredText(payload, "amount")),
                    requiredText(payload, "currency"),
                    Instant.parse(requiredText(root, "occurredAt")));
        } catch (RuntimeException | java.io.IOException e) {
            throw new IllegalArgumentException("Cannot parse LedgerEntryPosted: " + e.getMessage(), e);
        }
    }

    public TransferEvent parseTransfer(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payload = requiredNode(root, "payload");
            return new TransferEvent(
                    requiredText(root, "eventId"),
                    requiredText(payload, "transferId"),
                    requiredText(payload, "from"),
                    requiredText(payload, "to"),
                    decimal(requiredText(payload, "amount")),
                    requiredText(payload, "currency"),
                    Instant.parse(requiredText(root, "occurredAt")));
        } catch (RuntimeException | java.io.IOException e) {
            throw new IllegalArgumentException("Cannot parse transfer event: " + e.getMessage(), e);
        }
    }

    private static JsonNode requiredNode(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isMissingNode()) {
            throw new IllegalArgumentException("Missing required field '" + field + "'");
        }
        return child;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode child = requiredNode(node, field);
        if (!child.isTextual() || child.asText().isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' must be a non-blank string");
        }
        return child.asText();
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
