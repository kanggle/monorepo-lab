-- Per-day ASN number sequence table.
-- Authoritative reference: specs/services/inbound-service/domain-model.md §1 (asnNo auto-gen).
--
-- The table stores the last-issued sequence number per calendar day (YYYYMMDD).
-- AsnPersistenceAdapter.nextAsnNo() issues a single atomic
-- INSERT … ON CONFLICT … DO UPDATE … RETURNING last_seq to get the next value
-- without a separate SELECT; safe under concurrent writes.

CREATE TABLE asn_no_sequence (
    date_key   VARCHAR(8)   PRIMARY KEY,
    last_seq   BIGINT       NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
