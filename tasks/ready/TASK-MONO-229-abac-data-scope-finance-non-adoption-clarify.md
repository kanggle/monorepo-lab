# TASK-MONO-229 — Clarify ABAC data-scope: finance non-adoption + erp re-point recorded

**Status:** ready

**Type:** TASK-MONO (shared platform docs)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (doc-only correction)

---

## Goal

Correct two now-known inaccuracies in the ABAC data-scope contract and ADR after a survey established the real finance/erp situation (see TASK-ERP-BE-019):

1. **finance is NOT a future data-scope adopter.** `platform/abac-data-scope.md` § 3 lists `finance → accounting-unit ids *(future)*`. The finance-platform survey found finance is **single-tenant by design** (`PROJECT.md` § Out of Scope: "다수 organization 을 격리하는 SaaS 가 아님 (단일 금융 서비스 운영)"); `Account.tenantId` is always the literal `"finance"`, there is no accounting-unit / org-unit / cost-center partition field, and no list endpoint to scope. Finance has **no natural data-scope axis** — introducing one would mean inventing a domain dimension (a HARDSTOP-09 architecture decision), not adopting an existing pattern. The `*(future)*` row is a false promise and must become an explicit non-adoption note.
2. **erp now uses the canonical reader.** ADR-025 § D5/§ D7 recorded the erp re-point as OPTIONAL/deferred. TASK-ERP-BE-019 executes it (erp's three inline parsing sites now use `com.example.security.jwt.AbacDataScope`). Record this in the ADR § 6 history (append-only) so the "optional/deferred" wording is not read as still-pending.

This is a **doc-only** clarification: no decision is reversed, no contract semantics change. It corrects forward-looking claims to match reality.

## Scope

**In scope (shared paths only):**
- `platform/abac-data-scope.md` § 3 — replace the `finance → accounting-unit ids *(future)*` table row with a non-adoption note (finance is single-tenant; no data-scope axis; the closest field `ownerRef` is a per-customer encrypted identifier, not an org partition). Keep erp + wms rows. Optionally note erp consumes the claim via the canonical `AbacDataScope` reader (TASK-ERP-BE-019).
- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § 6 — append one status-history row: erp re-point executed (TASK-ERP-BE-019) + finance non-adoption clarified. Append-only; do NOT edit the D1–D7 decision prose (a small forward-pointer in § D2's domain list `finance → … (future)` may be updated to "(non-adopting — single-tenant)" since that line is descriptive, not a decision).

**Out of scope:**
- Any code (the erp re-point is TASK-ERP-BE-019).
- Any finance-platform change (no spec, no code — finance simply does not adopt).
- The wms/erp interpretation rows (unchanged).
- Reversing any ADR decision.

## Acceptance Criteria

- **AC-1** — `platform/abac-data-scope.md` § 3 no longer claims finance is a `*(future)*` adopter; it states finance does not adopt data-scope and why (single-tenant, no org-unit axis). The contract remains project-agnostic (no service-internal entity names beyond the illustrative level already present).
- **AC-2** — `ADR-MONO-025` § 6 has a new append-only history row recording the erp re-point (TASK-ERP-BE-019) and the finance non-adoption clarification.
- **AC-3** — No decision prose (D1–D7 directions) is reversed; the change is descriptive accuracy only.
- **AC-4** — Markdown is well-formed; no broken cross-references.

## Related Specs

- (none — doc-only)

## Related Contracts

- `platform/abac-data-scope.md` (the contract being corrected)
- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` (the ADR history being appended)

## Edge Cases

- **Shared-agnostic boundary** — `platform/abac-data-scope.md` is a shared regulation; the finance non-adoption note must stay at the illustrative level (it already names erp/wms/finance as illustrations). Do not import finance-internal entity names beyond what is needed to state "single-tenant, no org-unit axis".
- **Append-only ADR** — the § 6 history table is append-only; add a row, do not rewrite prior rows.

## Failure Scenarios

- **F1 — leaving the false `*(future)*` promise** — a later agent reads § 3 and tries to add finance data-scope, hits the no-axis wall, re-derives the blocker. The non-adoption note prevents the wasted cycle.
- **F2 — over-editing the ADR decision prose** — rewriting D1–D7 would blur the as-decided record. Confined to an append-only history row + a one-word descriptive fix in the D2 illustration list.
