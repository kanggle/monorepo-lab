# Task ID

TASK-BE-556

# Title

refactor-spec ecommerce: structural polish (dash/path/anchor/envelope/overview-row)

# Status

done

# Owner

backend

# Task Tags

- adr

---

# Goal

`/refactor-spec` dry-run (2026-07-22) over all 110 ecommerce spec files (contracts 30 / features 9 / services 62 / use-cases 7 / integration 1). The 2026-07-21 reconciliation audit already covered content/requirement drift, so this is STRUCTURAL polish only. Zero broken file references were found. This task lands the **seven verified-clean, meaning-preserving fixes** and records the items skipped after verification and the semantic findings that are out of refactor-spec scope.

After this task, the seven cited defects are gone; no requirement, contract, or decision changed.

---

# Scope

## In Scope — applied (all verified meaning-preserving)

1. **title separator** `features/marketplace-settlement.md:1`, `features/multi-tenancy-and-marketplace.md:1` — `# Feature — X` → `# Feature: X` (colon, matching the 7 sibling features).
2. **path prefix** `features/marketplace-settlement.md:60` — `contracts/events/settlement-subscriptions.md` → `specs/contracts/events/…` (the same file's L78 already uses `specs/contracts/…`).
3. **dash style** `use-cases/payment-and-refund.md` — ASCII ` -- ` → em-dash ` — ` (10×), matching the 6 sibling use-cases.
4. **blank line** `use-cases/wishlist.md` — removed the stray blank between `## Related Contracts` and its bullet list (sibling convention).
5. **envelope reality-alignment** `contracts/schemas/README.md:13` — the descriptive event envelope wrongly showed `{…, version, payload}`; the real envelope (verified against `order-events.md`/`user-events.md`) is `{ event_id, event_type, occurred_at, source, tenant_id, payload }` — corrected (`version` removed, `tenant_id` added). Descriptive/unused file; no live contract changed.
6. **dead-anchor / ambiguous ADR** `services/shipping-service/external-integrations.md:66,280` — bare `ADR-005` resolves to the *wrong* in-project ADR (`ADR-005-korean-search-analyzer`); the real referent is iam-platform `ADR-005-service-to-service-workload-identity`. Disambiguated inline + added an explicit cross-project link in References.
7. **missing public-surface row** `services/order-service/overview.md` — added `GET /api/orders/verify-purchase` (JWT, used by review-service) which is contracted at `order-api.md:150` and cited by deps.md but was omitted from the Public-surface table.

## Out of Scope — dropped after verification (NOT applied; the dry-run over-classified these)

- **signup-and-login.md:10 path base** — dry-run flagged it as inconsistent, but `specs/…` for the same-project ref and `projects/iam-platform/…` for the cross-project ref is the *correct* convention (project-relative own / repo-root cross). False positive.
- **fulfillment-events.md `## Overview`** — only 9/15 event contracts carry `## Overview` (40% lack it), so there is no clear standard to normalize toward.
- **account-lifecycle-subscriptions.md:5 `[TASK-BE-132](../../../../README.md)`** — resolves to the portfolio hub README, but the ecommerce project README does not mention BE-132 either, so the "correct" target is unclear (real task file? drop the link?). Needs judgment, not a mechanical retarget.

## Findings — out of refactor-spec scope (require decisions / semantic owner; NOT fixed)

- **🔴 `auth-service/` vs `auth-service-deprecated/` contradiction** — two folders describe the same decommissioned service with **contradicting** architecture (DDD-hybrid+OAuth vs Layered-CRUD); `auth-service/` has only `architecture.md` (siblings have 4 files). An agent reading "auth-service architecture" gets contradictory facts. Needs a consolidation decision (merge into one folder / cross-reference the vintage).
- **topic-naming drift** — ecommerce mainline event topics are `{context}.{aggregate}.{event}` **unversioned**, not the platform `{domain|service}.{aggregate}.{version}` (confirms the `design-event.md` finding). But a topic string is contract identity; changing it is a breaking change, ADR-gated (already documented as a live divergence in `events/README.md §1`). Report only.
- **possible real contract defects (→ semantic owner):** `order.cancelled` (`wms-shipment-subscriptions.md`) vs `order.order.cancelled` (`payment-events.md`) topic-string mismatch; `recipient` (`order-api.md:337` admin) vs `recipientName` (user order-api) response-field drift.
- **content contradictions (→ content-drift track):** `order-service/overview.md` lists consuming `payment.payment.failed` but `architecture.md` Event-consumption omits it; `batch-worker/overview.md` still lists `expiredSessionCleanupJob` as active while Responsibilities marks it REMOVED (IAM owns it now).

## Leave as-is (deliberate conventions)

- envelope / `{code,message,timestamp}` error / `## Consumer Rules` inline duplication across contracts (accepted inline-per-contract convention, ADR-gated per `schemas/README.md`).
- M1–M7 multi-tenancy restatement across 9 service `architecture.md` (deliberate link-plus-restate convention).
- The 2 ADR-facet feature files (`marketplace-settlement`, `multi-tenancy-and-marketplace`) using a numbered format instead of the standard 6-section shape (different genre by design).
- `admin-dashboard/` retired-service file-set gaps.

---

# Acceptance Criteria

- [x] Seven in-scope fixes applied; each verified against the actual target (heading / sibling / real envelope / contract).
- [x] No requirement/contract/decision changed — all seven are meaning-preserving.
- [ ] Cross-references in the modified files still resolve (dead-anchor checker); the new iam-platform ADR-005 link resolves.
- [ ] The out-of-scope findings are filed / routed (esp. the auth-service consolidation decision and the two possible topic/field contract defects). The topic-naming drift must NOT be auto-resolved.

---

# Related Specs

- `features/marketplace-settlement.md`, `features/multi-tenancy-and-marketplace.md`, `use-cases/payment-and-refund.md`, `use-cases/wishlist.md` (modified)
- `services/shipping-service/external-integrations.md`, `services/order-service/overview.md`, `contracts/schemas/README.md` (modified)
- `services/auth-service/architecture.md` + `services/auth-service-deprecated/architecture.md` (Finding — consolidation)

# Related Contracts

- `contracts/http/order-api.md` (verify-purchase endpoint — the source of the added overview row; unchanged)
- No API or event contract semantics changed.

---

# Edge Cases

- The schemas/README envelope correction (#5) was verified against two live event contracts before editing — it aligns a descriptive file to reality, it does not redefine the envelope.
- The order-service overview row (#7) mirrors an already-contracted endpoint; it adds no new requirement.
- `auth-service` consolidation must not be auto-resolved by picking one architecture description — the correct vintage is a code/owner question.

# Failure Scenarios

- **F1 — "correcting" the schemas envelope by guessing** would risk re-introducing a wrong shape; guarded by verifying against `order-events.md`/`user-events.md` first.
- **F2 — retargeting the `TASK-BE-132` link to the ecommerce README** would point at a README that doesn't mention BE-132; guarded by dropping it to a Finding.
- **F3 — auto-fixing a topic string** (`order.cancelled` / `{version}`) would silently change a published contract; guarded by reporting, not fixing.
