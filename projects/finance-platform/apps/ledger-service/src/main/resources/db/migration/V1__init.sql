-- finance-platform ledger-service initial schema (MySQL 8, InnoDB, utf8mb4).
-- Double-entry general ledger (first increment). Separate database from
-- account-service's finance_db (downstream derivation — the ledger never writes
-- back to the wallet store).
-- fintech F2/F3/F5/F6: balanced journal entries; immutable once posted
-- (no UPDATE/DELETE path for journal_entry / journal_line); integer minor-unit
-- money (BIGINT) + ISO currency VARCHAR(3); append-only audit_log;
-- processed_events consumer dedupe (F1). Multi-tenant: every table carries
-- tenant_id. TASK-FIN-BE-007.

-- ---------------------------------------------------------------------------
-- ledger_account — chart of accounts. The two platform GL accounts
-- (CASH_CLEARING, SETTLEMENT_SUSPENSE) are seeded at startup; per-customer
-- wallet accounts (CUSTOMER_WALLET:{accountId}) are created lazily on first
-- posting. normal_side: DEBIT for assets, CREDIT for liabilities.
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_account (
    code            VARCHAR(100) NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    normal_side     VARCHAR(10)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT ck_ledger_account_type CHECK (type IN
        ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    CONSTRAINT ck_ledger_account_normal_side CHECK (normal_side IN ('DEBIT','CREDIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ledger_account_tenant ON ledger_account (tenant_id);

-- ---------------------------------------------------------------------------
-- journal_entry — aggregate root. Insert-only (immutable, F3): no UPDATE/DELETE
-- path. A REVERSAL entry references the original via reversal_of_entry_id; the
-- source_* columns carry the account-service transaction + signed event
-- provenance (source_event_id keys the dedupe; source_transaction_id keys the
-- reversal lookup). version supports optimistic-lock parity (T7).
-- ---------------------------------------------------------------------------
CREATE TABLE journal_entry (
    entry_id                VARCHAR(36)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL,
    posted_at               DATETIME(6)  NOT NULL,
    source_type             VARCHAR(30)  NOT NULL,
    source_transaction_id   VARCHAR(64)  NOT NULL,
    source_event_id         VARCHAR(64)  NOT NULL,
    reversal_of_entry_id    VARCHAR(36),
    version                 BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_journal_entry_tenant_txn
    ON journal_entry (tenant_id, source_transaction_id);
CREATE INDEX idx_journal_entry_tenant_posted
    ON journal_entry (tenant_id, posted_at);

-- ---------------------------------------------------------------------------
-- journal_line — one debit-or-credit line of an entry. Insert-only (F3). The
-- entry_id + posted_at are denormalized onto the line so the per-account view
-- and the trial-balance totals are simple tenant-scoped line queries. Money is
-- BIGINT minor units + currency VARCHAR(3) (F5 — never a float).
-- ---------------------------------------------------------------------------
CREATE TABLE journal_line (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    entry_id            VARCHAR(36)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    ledger_account_code VARCHAR(100) NOT NULL,
    direction           VARCHAR(10)  NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    posted_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_journal_line_direction CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT fk_journal_line_entry FOREIGN KEY (entry_id)
        REFERENCES journal_entry (entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_journal_line_entry ON journal_line (entry_id);
CREATE INDEX idx_journal_line_account
    ON journal_line (tenant_id, ledger_account_code, posted_at);

-- ---------------------------------------------------------------------------
-- audit_log — append-only (F6). No UPDATE/DELETE path: the adapter only
-- inserts; written in the same Tx as the journal entry posting.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id         VARCHAR(64)  NOT NULL,
    aggregate_type    VARCHAR(40)  NOT NULL,
    aggregate_id      VARCHAR(64)  NOT NULL,
    action            VARCHAR(40)  NOT NULL,
    actor             VARCHAR(64)  NOT NULL,
    after_state       VARCHAR(1024),
    reason            VARCHAR(256),
    occurred_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_audit_log_tenant_aggregate
    ON audit_log (tenant_id, aggregate_type, aggregate_id, occurred_at);

-- ---------------------------------------------------------------------------
-- processed_events — consumer idempotency dedupe store (F1/T8). Keyed on the
-- signed envelope event_id; a duplicate is skipped without mutation so
-- re-delivery posts at most one entry. Distinct from libs/java-messaging's
-- outbox processed_events (that outbox auto-config is excluded — ledger is a
-- terminal consumer, no outbox).
-- ---------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id                VARCHAR(64)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL,
    topic                   VARCHAR(200) NOT NULL,
    source_transaction_id   VARCHAR(64)  NOT NULL,
    processed_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_processed_events_topic ON processed_events (topic, processed_at);
