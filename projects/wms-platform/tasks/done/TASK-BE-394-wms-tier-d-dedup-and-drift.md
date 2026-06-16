# TASK-BE-394 — wms spec Tier D dedup + the duplication-induced drift it surfaced (code-verified, from TASK-BE-385 § Findings)

Status: done

## Goal

Close the **Tier D finding** that TASK-BE-385 flagged ("identical recaps restated across specs → convert to cross-refs; **verify identity per file first**, low urgency"). Per-file verification — the explicit precondition BE-385 attached — reveals that **most of the flagged "duplication" is not pure duplication** (it is legitimate summary + service-local context that already cross-references the canonical), and that the one place with genuine verbatim duplication has **silently drifted in three copies**. This task fixes the genuine drift, de-duplicates that one table to a single canonical, and records the per-file verification verdict for the not-actionable items so the Tier D finding is honestly closed.

Doc-only: `git diff origin/main -- 'projects/wms-platform/apps/**'` MUST be empty.

## Scope

### Actionable — `admin_event_dedupe` is triplicated and 2 of 3 copies have drifted

The dedupe table appears in three admin specs. The canonical physical schema is **`admin-service/database-design.md` §6** (named CHECK constraint, conventional `idx_*` index name matching every other admin table, the authoritative 4-outcome enum). The other two copies drifted:

- **D1a — `admin-service/domain-model.md` §15 (value drift)**: the `outcome` enum lists **3** values (`APPLIED` / `IGNORED_DUPLICATE` / `FAILED`) — it is **missing `IGNORED_DUPLICATE_LATE`**, the LWW signal that `database-design.md` §6 explicitly calls out as "the 4-outcome enum [that] distinguishes admin's LWW projection from other services' 3-outcome dedupe" and that `idempotency.md` documents as "Unique to admin." Fix: add the 4th outcome + a one-line note, cross-reference the canonical.
- **D1b — `admin-service/idempotency.md` §2.2 (index-name + constraint-representation drift)**: the inline `CREATE TABLE` block names the index `admin_event_dedupe_processed_at_idx` (canonical: `idx_admin_event_dedupe_processed_at`) and expresses the outcome set as an SQL `-- comment` instead of the canonical named `CHECK` constraint. Fix: replace the duplicate inline DDL block with a cross-reference to `database-design.md` §6 (single source of truth), **keeping** the useful service-local prose (the `IGNORED_DUPLICATE` / `IGNORED_DUPLICATE_LATE` semantics + retention).

### Actionable (light) — notification "sketch" pointer

- **D2 — `notification-service/domain-model.md` "Persistence Layout"**: the inline DDL is explicitly self-labelled a *sketch* ("full SQL in BE-043 implementation PR") and is column-compatible with the canonical `notification-service/database-design.md` (no value drift — BE-387 already reconciled the dedupe CHECK there). The sketch is a deliberate illustrative aid, so it stays; only its stale pointer ("BE-043 implementation PR") is retargeted to the canonical spec `database-design.md` so a reader is sent to a durable source, not a merged PR.

### NOT actionable — verified, recorded so Tier D closes honestly

- **Event/topic tables in every `*/architecture.md`** (inbound/outbound/inventory/master/admin/notification "Event Publication" / "Event Consumption"): verified **NOT** verbatim duplication. Each is an (event-name | topic | **service-local trigger/effect**) summary index whose trigger/effect column is service-local context absent from the canonical, and which already cross-references the full payload schemas ("Full schemas: `contracts/events/*.md`"). Converting these to bare cross-refs would **lose** the local context — net-negative. Left intact.
- **admin error-code "table" vs `rules/domains/wms.md`**: `admin-service/architecture.md` already **cross-references** `rules/domains/wms.md § Standard Error Codes — Admin / Operations` (it does not restate the registry). The per-rule `SETTING_VALIDATION_ERROR` etc. in validation tables are legitimate domain logic, not a registry copy. Not a duplication.

## Related Specs

- `projects/wms-platform/specs/services/admin-service/domain-model.md` (D1a)
- `projects/wms-platform/specs/services/admin-service/idempotency.md` (D1b)
- `projects/wms-platform/specs/services/admin-service/database-design.md` (canonical — referenced, unedited)
- `projects/wms-platform/specs/services/notification-service/domain-model.md` (D2)

## Related Contracts

None changed. The canonical DDL (`database-design.md` §6) is the fix target's reference, not edited. No event payload, topic name, REST shape, or Flyway/DDL value is altered — D1a **adds a missing enum value to a stale doc copy to match the canonical**, it does not change the schema.

## Code / canonical verification (fix direction is canonical-anchored)

- **D1a/D1b canonical = 4 outcomes + `idx_admin_event_dedupe_processed_at`**: `admin-service/database-design.md` §6 — `CONSTRAINT ck_admin_dedupe_outcome CHECK (outcome IN ('APPLIED','IGNORED_DUPLICATE','IGNORED_DUPLICATE_LATE','FAILED'))`, `CREATE INDEX idx_admin_event_dedupe_processed_at`. Index-naming convention confirmed against the §16 index inventory (every admin index is `idx_*`). `idempotency.md` §2.2 prose independently documents the 4th outcome as admin-unique LWW.
- **D2 no value drift**: `notification-service/database-design.md` §3 dedupe CHECK = `('QUEUED','FILTERED','NO_RULE','ERROR')` (4-value, BE-387-reconciled); the domain-model sketch omits the CHECK entirely (less-detailed, not contradictory) → column-compatible, pointer-only fix.
- **Not-actionable check**: outbound + admin `architecture.md` event sections each carry a trigger/effect column + "Full schemas: …events.md" cross-ref (summary, not payload dup); admin `architecture.md` § backfill audit item 6 already cross-references `rules/domains/wms.md` for error codes.

## Edge Cases

- The same `admin_event_dedupe` schema lives in 3 specs; only the **physical** copy (`database-design.md` §6) is canonical. The `domain-model.md` §15 entity-field table and `idempotency.md` §2.2 stay as docs but defer to the canonical for the authoritative enum/index — fixing the drift, not deleting the sections.
- D2 keeps the illustrative sketch (it aids reading and is self-disclaimed); only the dangling PR pointer is repaired.

## Failure Scenarios

- **F1 — over-converting legitimate summaries**: guarded by the per-file verification (§ Code verification) — the event/topic tables and the already-cross-referenced error codes are explicitly left intact with recorded rationale, not blindly converted.
- **F2 — touching apps/** or a contract value**: guarded by AC-2 — D1a only adds a missing enum value to a doc to match the canonical; no apps/Flyway/contract edit.

## Acceptance Criteria

- **AC-1 (canonical-verified)** — every edit aligns a lower-fidelity copy to the canonical `database-design.md` §6 (D1) or a durable spec pointer (D2). No value is invented; `IGNORED_DUPLICATE_LATE` is added because the canonical + the implementation already carry it.
- **AC-2 (doc-only / meaning-preserving)** — `git diff origin/main -- 'projects/wms-platform/apps/**'` is empty. No Flyway/enum-value/contract/topic change. All edits under `specs/`.
- **AC-3 (single source of truth)** — after the fix, `admin_event_dedupe`'s authoritative enum (4 outcomes) and index name appear in exactly one canonical place (`database-design.md` §6); the other two specs reference it rather than re-declaring a drifting copy.
- **AC-4 (no over-reach)** — the architecture.md event/topic summary tables, the already-cross-referenced admin error codes, and the deliberate notification sketch DDL are left intact (only the sketch's stale pointer is repaired); rationale recorded.
- **AC-5 (no new dead refs)** — every added cross-reference (`database-design.md` §6 etc.) resolves.
