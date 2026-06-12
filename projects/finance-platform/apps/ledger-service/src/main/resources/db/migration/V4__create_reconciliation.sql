-- finance-platform ledger-service reconciliation schema (4th increment,
-- TASK-FIN-BE-010, fintech F8). MySQL 8, InnoDB, utf8mb4 — parity with V1/V2/V3.
-- The ledger reconciles its clearing accounts (CASH_CLEARING / SETTLEMENT_SUSPENSE)
-- against an ingested external statement (bank / PG): 1:1 match by (amount,
-- currency, direction); anything unmatched → a ReconciliationDiscrepancy in an
-- OPEN operator review queue. F8 — no auto-close: a discrepancy is RECORDED and
-- surfaced; only the operator resolve use case flips OPEN→RESOLVED.
-- Money is BIGINT minor units + currency CHAR(3) (F5 — never a float).
-- String/CHAR(36) ids (the ledger id convention — journal_entry's VARCHAR(36)
-- form, NOT the V3 outbox UUID type). Multi-tenant: every table carries tenant_id.

-- ---------------------------------------------------------------------------
-- reconciliation_statement — a batch of external settlement lines for ONE
-- reconciled clearing account. Ingested once + immutable (only its lines'
-- match_status flips during matching). source ∈ {BANK,PG,OTHER}.
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_statement (
    statement_id        CHAR(36)     NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    ledger_account_code VARCHAR(100) NOT NULL,
    source              VARCHAR(10)  NOT NULL,
    statement_date      DATE         NOT NULL,
    ingested_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (statement_id),
    CONSTRAINT ck_recon_statement_source CHECK (source IN ('BANK','PG','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_recon_statement_tenant_account
    ON reconciliation_statement (tenant_id, ledger_account_code, statement_date);

-- ---------------------------------------------------------------------------
-- reconciliation_statement_line — one external settlement line. direction is
-- relative to the reconciled account (a deposit credit ↔ the account's debit).
-- match_status is UNMATCHED on ingest, flipped to MATCHED by the matcher. Money
-- is BIGINT minor units + currency CHAR(3) (F5).
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_statement_line (
    line_id         CHAR(36)     NOT NULL,
    statement_id    CHAR(36)     NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    external_ref    VARCHAR(128) NOT NULL,
    amount_minor    BIGINT       NOT NULL,
    currency        CHAR(3)      NOT NULL,
    direction       VARCHAR(10)  NOT NULL,
    value_date      DATE         NOT NULL,
    description     VARCHAR(256),
    match_status    VARCHAR(10)  NOT NULL,
    PRIMARY KEY (line_id),
    CONSTRAINT fk_recon_line_statement FOREIGN KEY (statement_id)
        REFERENCES reconciliation_statement (statement_id),
    CONSTRAINT ck_recon_line_direction CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_recon_line_match_status CHECK (match_status IN ('UNMATCHED','MATCHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_recon_line_statement ON reconciliation_statement_line (statement_id);

-- ---------------------------------------------------------------------------
-- reconciliation_match — a recorded 1:1 link between a matched statement line and
-- an internal journal entry on the reconciled account. Insert-only. The
-- (tenant_id, journal_entry_id) index backs the unmatched-internal-lines anti-join
-- (a journal line whose entry is NOT already here is a matching candidate).
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_match (
    match_id            CHAR(36)     NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    statement_line_id   CHAR(36)     NOT NULL,
    external_ref        VARCHAR(128) NOT NULL,
    journal_entry_id    CHAR(36)     NOT NULL,
    ledger_account_code VARCHAR(100) NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    matched_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_recon_match_tenant_entry ON reconciliation_match (tenant_id, journal_entry_id);
CREATE INDEX idx_recon_match_line ON reconciliation_match (tenant_id, statement_line_id);

-- ---------------------------------------------------------------------------
-- reconciliation_discrepancy — a recorded mismatch (mirrors the account-service
-- placeholder columns: expected_minor / actual_minor / status / detected_at).
-- type ∈ {UNMATCHED_EXTERNAL,UNMATCHED_INTERNAL,AMOUNT_MISMATCH}. Recorded OPEN
-- (F8 — never auto-closed); the operator resolve use case flips it to RESOLVED and
-- stamps resolution_type / note / resolved_by / resolved_at. statement_id is the
-- detection provenance (for the statement detail read). Money is BIGINT minor +
-- currency CHAR(3) (F5).
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_discrepancy (
    discrepancy_id      CHAR(36)     NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    statement_id        CHAR(36),
    ledger_account_code VARCHAR(100) NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    external_ref        VARCHAR(128),
    journal_entry_id    CHAR(36),
    expected_minor      BIGINT       NOT NULL,
    actual_minor        BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(10)  NOT NULL,
    resolution_type     VARCHAR(20),
    note                VARCHAR(512),
    resolved_by         VARCHAR(128),
    resolved_at         DATETIME(6),
    detected_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (discrepancy_id),
    CONSTRAINT ck_recon_discrepancy_type CHECK (type IN
        ('UNMATCHED_EXTERNAL','UNMATCHED_INTERNAL','AMOUNT_MISMATCH')),
    CONSTRAINT ck_recon_discrepancy_status CHECK (status IN ('OPEN','RESOLVED')),
    CONSTRAINT ck_recon_discrepancy_resolution CHECK (resolution_type IS NULL OR resolution_type IN
        ('MATCHED_MANUALLY','WRITTEN_OFF','ACCEPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_recon_discrepancy_queue ON reconciliation_discrepancy (tenant_id, status, detected_at);
CREATE INDEX idx_recon_discrepancy_statement ON reconciliation_discrepancy (statement_id);
