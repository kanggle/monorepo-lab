-- TASK-SCM-BE-050 — widen the actor-identity columns so a client-credentials
-- caller's subject (client_id) no longer overflows.
--
-- Root cause: `purchase_orders.buyer_account_id`, `po_status_history.actor_account_id`
-- and `audit_log.actor_account_id` were all sized VARCHAR(36) on the implicit
-- assumption that the actor identity (`ActorContextJwtAuthenticationConverter`
-- stores the raw JWT `sub`) is always a 36-char UUID. That holds for a human
-- operator (UUID or email <=36 chars) but NOT for scm's documented machine
-- caller: the `scm-platform-internal-services-client` client-credentials token
-- carries `sub == client_id` (37 chars — one over the limit), so any PO drafted
-- by that identity fails on flush with
--   `ERROR: value too long for type character varying(36)`
-- (Hibernate DataException -> 500). See specs/integration/iam-integration.md
-- Edge Case E1.
--
-- Fix chosen (TASK-SCM-BE-050 Scope §1 option a): widen the columns rather than
-- deriving a bounded synthetic id at the JWT boundary. This keeps the stored
-- value the *true* `sub`, directly verifiable against the token (the audit
-- contract in data-model.md: "IAM `sub` claim of the actor"), and avoids a
-- reverse-lookup mapping table. Truncation/hashing is explicitly rejected
-- (Failure Scenario A) — it would make the identity lossy/ambiguous.
--
-- Width = 255, with real headroom (not "just barely fits 37" — Edge Case in the
-- ticket): RFC 5321 caps an email at 254 chars, and an OAuth2 `client_id`
-- (RFC 6749 §2.2) has no standard length ceiling, so 255 comfortably covers any
-- realistic future machine-client registration as well as UUIDs (36) and emails.
-- The next client-credentials client can therefore be registered without
-- silently reopening this overflow.
--
-- All three columns are widened together (Failure Scenario B: fixing only
-- `buyer_account_id` would leave the two currently NULL-tolerant sibling
-- `actor_account_id` columns latently broken for any future non-NULL write path).
--
-- Widening a varchar's max length is a catalog-only change in PostgreSQL (no
-- table rewrite, no data loss) — purely additive.

ALTER TABLE purchase_orders
    ALTER COLUMN buyer_account_id TYPE VARCHAR(255);

ALTER TABLE po_status_history
    ALTER COLUMN actor_account_id TYPE VARCHAR(255);

ALTER TABLE audit_log
    ALTER COLUMN actor_account_id TYPE VARCHAR(255);
