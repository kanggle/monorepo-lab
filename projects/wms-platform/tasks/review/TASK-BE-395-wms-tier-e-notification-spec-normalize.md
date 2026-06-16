# TASK-BE-395 — wms notification-service spec-completion + idempotency heading normalization (Tier E from TASK-BE-385 § Findings)

Status: review

## Goal

Close the final **Tier E** item from TASK-BE-385 — the structural / missing-section normalization that BE-385 declined to do under the `/refactor-spec` constraint ("requires authoring → a dedicated spec-completion task"). This task brings `notification-service/domain-model.md` into the **sibling-standard shape** shared by the other six WMS service domain-models, and normalizes the idempotency-doc heading convention — sourcing **all** content from material already present in the notification specs (no new requirements, rules, or contract values are invented). With this, the BE-385 findings ledger (Tier A/B → BE-387, Tier C → BE-392, Tier D → BE-394, Tier E → here) is fully closed.

Doc-only: `git diff origin/main -- 'projects/wms-platform/apps/**'` MUST be empty.

## Scope

### E-A — `notification-service/domain-model.md` sibling-standard sections

Add the six standard sections BE-385 flagged as missing (present in inventory/master/outbound/etc. domain-models), each populated from existing notification content, with `---` dividers:

- **`## Scope`** — the 3 owned aggregates (RoutingRule / NotificationDelivery / NotificationEventDedupe) + 1 infra record (NotificationOutbox), and the terminal-consumer / event-only-consistency framing (from architecture.md + the existing aggregate descriptions).
- **`## Common Aggregate Shape`** — id / createdAt / updatedAt baseline; `NotificationDelivery` adds `version`; dedupe + outbox are append-only (no version/updated_at). All derived from the existing Persistence Layout DDL.
- **`## Entity Relationship Diagram`** — the four tables are FK-free, correlated by `eventId` (projection/fan-out service). Derived from the existing aggregates + persistence.
- **`## Aggregate Boundaries`** — per-aggregate owns / cross-via table; notes the absence of any W1-style multi-aggregate write transaction.
- **`## Forbidden Patterns (in code)`** — restates the already-declared invariants/state-machine/errors as the standard ❌ list (no-direct-status-UPDATE T4, append-only dedupe/outbox, immutable payloadSnapshot, single enabled rule per eventType, no secrets in lastError).
- **`## Open Items (Retrospective Backfill Audit)`** — the notification backfill audit (database-design.md, idempotency.md, registered error codes, the deferred v2 dedupe-cleanup job). The existing `## Out of Scope (v1)` v2-roadmap list is retained.

**Value-drift fix folded in (same class as BE-394)**: domain-model.md §3 `NotificationEventDedupe.outcome` lists **3** values (`QUEUED | FILTERED | ERROR`) — missing `NO_RULE`, which (a) the canonical `database-design.md` §3 `CHECK` carries and (b) the doc's **own** Domain Errors row references ("dedupe outcome=NO_RULE"). Corrected to the 4-value set.

### E-B — idempotency-doc heading convention — **DECLINED (verified, recorded)**

BE-385 flagged `notification-service/idempotency.md` + `master-service/idempotency.md` as non-numbered `## H2`s vs the numbered `## N. …` convention in admin/inbound/inventory/outbound. Per-file verification declines this purely-cosmetic renumbering:

1. **Anchor-break blast radius**: `master-service/idempotency.md`'s `## Cross-Service Idempotency Key Conventions (Intentional Divergence)` heading is the target of **4 cross-service markdown anchor links** (from outbound / inbound / admin / inventory idempotency.md, `#cross-service-idempotency-key-conventions-intentional-divergence`). Numbering the heading changes the GitHub slug and **breaks all 4 links** for zero functional gain.
2. **Structures legitimately differ**: the numbered convention is bound to the REST + Kafka **dual-mechanism** layout (`1. REST / 2. Kafka / 3. Decision Table / …`). notification (a REST-less terminal consumer: `Inbound (Kafka) / Outbound (Channel)`) and master (REST-key-focused: `Key Contract / Key Scope / Storage / Cross-Service Conventions / Request Hashing`) have different, justified section structures; prefixing `N.` would add numbers without making them structurally match.

Recorded as not-actionable (same verify-then-decline-cosmetic-churn judgment as the Tier D event-tables in TASK-BE-394).

### E-C — missing `## Test Requirements`

`admin-service/idempotency.md` and `inventory-service/idempotency.md` lack the `## Test Requirements` section that the sibling idempotency docs carry. Add it, sourced from the **existing** test specifications for those services (the idempotency-filter / dedupe test surfaces already described in their architecture.md § Testing Requirements + the sibling idempotency Test Requirements shape) — no new test obligations are invented.

## Related Specs

- `projects/wms-platform/specs/services/notification-service/domain-model.md` (E-A)
- `projects/wms-platform/specs/services/notification-service/idempotency.md` (E-B)
- `projects/wms-platform/specs/services/master-service/idempotency.md` (E-B)
- `projects/wms-platform/specs/services/admin-service/idempotency.md` (E-C)
- `projects/wms-platform/specs/services/inventory-service/idempotency.md` (E-C)
- sibling templates (read-only): inventory/master/outbound `domain-model.md`, sibling `idempotency.md`

## Related Contracts

None changed. No event payload, topic, REST shape, enum **value**, or Flyway/DDL is altered — the dedupe `NO_RULE` fix **adds a missing value to a stale doc copy to match the canonical** (it does not change the schema). All edits restructure / complete prose under `specs/`.

## Code / canonical verification (no invented content)

- **E-A `NO_RULE`**: `notification-service/database-design.md` §3 `CHECK (outcome IN ('QUEUED','FILTERED','NO_RULE','ERROR'))` (BE-387-reconciled) + the domain-model's own Domain Errors row `ROUTING_RULE_NOT_FOUND … dedupe outcome=NO_RULE`.
- **E-A sections**: every added section restates content already in notification `domain-model.md` (aggregates, invariants, state machine, errors, out-of-scope) and `architecture.md` (terminal-consumer role, deferred cleanup job). No new aggregate, field, rule, or error is introduced.
- **E-B / E-C**: heading-shape + section-shape alignment to the existing sibling idempotency docs; the test obligations in E-C are the dedupe/idempotency surfaces already enumerated in each service's architecture.md § Testing Requirements.

## Edge Cases

- The notification domain-model already has the substantive content under non-standard headings; this task **adds framing sections**, it does not delete or renumber the existing aggregate/value-object/persistence sections.
- `## Out of Scope (v1)` (notification's v2-roadmap) is distinct from `## Open Items (Retrospective Backfill Audit)` (what-exists audit) — both are kept.

## Failure Scenarios

- **F1 — inventing requirements while "authoring"**: guarded by AC-1 — every added sentence is traceable to existing notification spec content or the canonical; sections that would require a new decision (e.g., a real CRUD API for RoutingRule) are left as the existing v2 Out-of-Scope notes, not authored.
- **F2 — touching apps/** or a contract value**: guarded by AC-2 (doc-only); the `NO_RULE` addition aligns a doc to the canonical, no schema change.

## Acceptance Criteria

- **AC-1 (no invented content)** — every added section/sentence restates content already present in the notification specs or the canonical; no new aggregate, field, rule, error code, or test obligation is introduced.
- **AC-2 (doc-only / meaning-preserving)** — `git diff origin/main -- 'projects/wms-platform/apps/**'` is empty. No Flyway / enum-value / contract / topic change. All edits under `specs/`.
- **AC-3 (sibling-standard shape)** — after E-A, `notification-service/domain-model.md` carries the same standard sections (`Scope`, `Common Aggregate Shape`, `Entity Relationship Diagram`, `Aggregate Boundaries`, `Forbidden Patterns`, `Open Items`) as its six sibling domain-models, with `---` dividers.
- **AC-4 (convention consistency)** — after E-C, admin + inventory idempotency.md carry a `## Test Requirements` section (matching the sibling notification/master idempotency docs). E-B numbering is **declined** (would break 4 cross-service anchor links to master's heading; structures legitimately differ) — rationale recorded in § Scope.
- **AC-5 (no new dead refs)** — every added cross-reference resolves; no heading rename breaks an existing anchor link.
