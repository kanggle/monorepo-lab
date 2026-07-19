-- ADR-MONO-050 (TASK-BE-507): scm confirmed PO -> wms inbound expectation.
--
-- Additive extension of the existing `asn` aggregate (NO new expectation table):
-- wms realises the ADR's conceptual InboundExpectation(EXPECTED) as an `asn` row in its
-- existing initial `CREATED` state, with a new `source` value and PO-trace columns.
--
-- Idempotency reuses the existing `inbound_event_dedupe` (V5) for eventId dedup (D6.1);
-- the `(po_number, line)` business dedup (D6.2) is served by the partial index below.

-- Traceability back to the originating scm PO. Nullable — MANUAL / WEBHOOK_ERP ASNs
-- carry no PO reference.
ALTER TABLE asn ADD COLUMN po_number VARCHAR(40);
ALTER TABLE asn ADD COLUMN po_id     UUID;

-- Admit the new source without dropping the existing enum guard.
ALTER TABLE asn DROP CONSTRAINT ck_asn_source;
ALTER TABLE asn ADD CONSTRAINT ck_asn_source
    CHECK (source IN ('MANUAL', 'WEBHOOK_ERP', 'SCM_PROCUREMENT'));

-- Business-dedup lookup (D6.2): "is there an open expectation for this PO?".
-- Partial index — only scm-sourced rows carry a po_number.
CREATE INDEX idx_asn_po_number ON asn (po_number) WHERE po_number IS NOT NULL;
