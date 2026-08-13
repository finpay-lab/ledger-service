-- ADR-0004: transactional outbox. Rows inserted in the same tx as the business
-- change so events are never lost between commit and Kafka publish (at-least-once).
CREATE TABLE outbox (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    event_id        UUID         NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

-- Relay polling index (find unpublished oldest-first).
CREATE INDEX idx_outbox_unpublished_created ON outbox (published, created_at);