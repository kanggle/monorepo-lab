-- PutawayInstruction aggregate + PutawayLine + (append-only) PutawayConfirmation.
-- Authoritative reference: specs/services/inbound-service/domain-model.md §3, §4.
--
-- PutawayConfirmation is append-only — V7 (role grants) revokes UPDATE/DELETE
-- on this table for the application role.
--
-- These tables exist for future tasks (TASK-BE-032). No domain code in
-- TASK-BE-029 touches them.

CREATE TABLE putaway_instruction (
    id            UUID         PRIMARY KEY,
    asn_id        UUID         NOT NULL UNIQUE REFERENCES asn (id) ON DELETE CASCADE,
    warehouse_id  UUID         NOT NULL,
    planned_by    VARCHAR(100) NOT NULL,
    status        VARCHAR(40)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(100) NOT NULL,
    CONSTRAINT ck_putaway_instruction_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED'))
);

CREATE INDEX idx_putaway_instruction_asn       ON putaway_instruction (asn_id);
CREATE INDEX idx_putaway_instruction_warehouse ON putaway_instruction (warehouse_id);

CREATE TABLE putaway_line (
    id                       UUID         PRIMARY KEY,
    putaway_instruction_id   UUID         NOT NULL REFERENCES putaway_instruction (id) ON DELETE CASCADE,
    asn_line_id              UUID         NOT NULL REFERENCES asn_line (id),
    sku_id                   UUID         NOT NULL,
    lot_id                   UUID,
    lot_no                   VARCHAR(40),
    destination_location_id  UUID         NOT NULL,
    qty_to_putaway           INTEGER      NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_putaway_line_qty_positive CHECK (qty_to_putaway > 0),
    CONSTRAINT ck_putaway_line_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SKIPPED'))
);

CREATE INDEX idx_putaway_line_instruction ON putaway_line (putaway_instruction_id);
CREATE INDEX idx_putaway_line_asn_line    ON putaway_line (asn_line_id);
CREATE INDEX idx_putaway_line_destination ON putaway_line (destination_location_id);

-- Append-only confirmation record. No updates after creation (V7 enforces).
CREATE TABLE putaway_confirmation (
    id                       UUID         PRIMARY KEY,
    putaway_instruction_id   UUID         NOT NULL REFERENCES putaway_instruction (id),
    putaway_line_id          UUID         NOT NULL UNIQUE REFERENCES putaway_line (id),
    sku_id                   UUID         NOT NULL,
    lot_id                   UUID,
    planned_location_id      UUID         NOT NULL,
    actual_location_id       UUID         NOT NULL,
    qty_confirmed            INTEGER      NOT NULL,
    confirmed_by             VARCHAR(100) NOT NULL,
    confirmed_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_putaway_confirmation_qty_positive CHECK (qty_confirmed > 0)
);

CREATE INDEX idx_putaway_confirmation_instruction ON putaway_confirmation (putaway_instruction_id);
CREATE INDEX idx_putaway_confirmation_line        ON putaway_confirmation (putaway_line_id);
CREATE INDEX idx_putaway_confirmation_actual_loc  ON putaway_confirmation (actual_location_id);
