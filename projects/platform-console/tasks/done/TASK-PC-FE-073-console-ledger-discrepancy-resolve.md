# Task ID

TASK-PC-FE-073

# Title

console-web — finance ledger reconciliation discrepancy *resolve* mutation (the ledger surface's first operator action)

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: `TASK-PC-FE-072` (the read-only `features/ledger-ops` section — trial balance / periods / journal entry / reconciliation discrepancy queue). **MERGED** → origin/main (impl #1440 squash `85a0162a`). This task adds the **first mutation** to that section.
- **producer already on main (no cross-project prerequisite)**: the resolve endpoint — finance [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) **§ 2** `POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve` — is **already authored + merged** (TASK-FIN-BE-010, extended through the 11 ledger increments). It is **unchanged, consumed only**. The ledger shares the finance tenant gate (TASK-FIN-BE-005) — no new finance-side prerequisite.
- **spec-first**: the console-side contract amendment (**§ 2.4.7.1 read-binding carve-out + the new resolve-mutation sub-binding** in `console-integration-contract.md` + `console-web/architecture.md` ledger-ops mutation entries) lands **before/with** the code (this task's spec PR). The producer contract is **unchanged**.
- **part of**: the platform-console finance federation — turning the FE-072 **read-only** discrepancy queue into an **actionable operator tool** (the operator reviews + resolves reconciliation discrepancies, incl. the 11th-increment FX-difference `AMOUNT_MISMATCH`). The same read-only→single-write-pilot evolution the erp § 2.4.8 department write followed.
- **first ledger mutation → Opus**: the slice introduces the ledger surface's first mutation with a deliberate, producer-faithful header matrix (body-reason, **no Idempotency-Key**, confirm-gated, OPEN-only) and a contract carve-out — **Opus** for fidelity (the same reasoning that kept the FE-072 contract-bearing slice on Opus).

# Goal

Add the **first (and, at this increment, only) operator mutation** to the `features/ledger-ops` section: the reconciliation **discrepancy resolve**. An operator reviews an **OPEN** discrepancy in the queue (FE-072) and resolves it — closing the F8 operator-review loop that the read-only queue could only surface:

- **resolve** — `POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve`, request body `{ "resolutionType": <…>, "note": <…> }`, `resolutionType ∈ { MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`, `200` → the discrepancy with `status: "RESOLVED"` + a `resolution` sub-object (`resolutionType`, `note`, `resolvedBy`, `resolvedAt`).

This is the F8-sanctioned operator close — **never** an auto-resolve. The producer guards double-resolve with `409 RECONCILIATION_ALREADY_RESOLVED` and freezes closed periods with `422 RECONCILIATION_PERIOD_LOCKED`.

**Header matrix (honest, producer-faithful)** — the resolve mirrors the erp delegation-*revoke* body-reason shape, with one honest difference:

| Header | this resolve |
|---|---|
| `Authorization` | domain-facing IAM OIDC token (`getDomainFacingToken()`, **never** `getOperatorToken()`) |
| `Idempotency-Key` | **NO** — `reconciliation-api.md` § 2 defines none for resolve (unlike ledger `POST /entries`, which requires one); the `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the double-submit defence. Fabricating a key the producer ignores is forbidden (same honesty discipline as the no-429 rule). |
| `X-Operator-Reason` | **NO** — the reason rides in the body `note` (a required, non-empty operator narrative = the audit record). |
| `X-Tenant-Id` | **NO** — tenant from the JWT claim. |

The resolve is **confirm-gated** (a `resolutionType` selection + a **required** non-empty `note`; an empty `note` → no fetch) and offered **only on an OPEN discrepancy**. Every **other** ledger mutation (`POST /entries`, `/revaluations`, `/settlements`, reconciliation ingest) stays **out of scope**.

# Scope

## In Scope

### Spec-first (console-side — lands in this task's spec PR)

- `console-integration-contract.md` **§ 2.4.7.1** — amend the read-only binding into a "read + one mutation carve-out" + a new **Reconciliation discrepancy resolve mutation** sub-bullet (credential, header matrix, confirm-gated/reason-required, resolve-specific resilience).
- `console-web/architecture.md` — ledger-ops mutation entries (banner, tree `api`/`hooks`/`components`, Integration Rules bullet, Testing Strategy entry, References) updated from "mutation 0" to "1 mutation: discrepancy resolve".

### Code (console-web)

- `features/ledger-ops/api/types.ts` — `RESOLUTION_TYPES` (`MATCHED_MANUALLY | WRITTEN_OFF | ACCEPTED`) + `ResolveDiscrepancyBodySchema` (`resolutionType` enum + `note` min(1).max(512)). (No `idempotencyKey` — the producer defines none.)
- `features/ledger-ops/api/ledger-api.ts` — `resolveDiscrepancy(id, { resolutionType, note })` → POST, body `{ resolutionType, note }`, domain-facing token, **no `Idempotency-Key`**, no `X-Operator-Reason`; parse the returned `Discrepancy`. Map `409 RECONCILIATION_ALREADY_RESOLVED` / `422 RECONCILIATION_PERIOD_LOCKED` / `404` as `ApiError`; `503`/timeout as `LedgerUnavailableError`.
- `features/ledger-ops/hooks/use-ledger-ops.ts` — `useResolveDiscrepancy()` `useMutation` calling the proxy; `onSuccess` → invalidate the discrepancy list + detail queries.
- `features/ledger-ops/components/` — a `DiscrepancyResolveDialog` (confirm-gated: `resolutionType` select + required `note` textarea; empty `note` → confirm disabled, no fetch; Escape/cancel; pending state). Wire a **resolve** action button into `DiscrepancyQueue` / `DiscrepancyDetail`, shown **only on an OPEN discrepancy**. On success reflect `RESOLVED` + `resolution`. A feature-local `discrepancy-error.ts` maps resolve codes to inline messages (`RECONCILIATION_ALREADY_RESOLVED`, `RECONCILIATION_PERIOD_LOCKED`, `RECONCILIATION_DISCREPANCY_NOT_FOUND`).
- `app/api/ledger/reconciliation/discrepancies/[id]/resolve/route.ts` — same-origin **POST** proxy: validate `ResolveDiscrepancyBodySchema` (empty `note` → `400 VALIDATION_ERROR`, no upstream call), call `resolveDiscrepancy`, map errors via `mapLedgerError`.
- `shared/api/errors.ts` — add `RECONCILIATION_ALREADY_RESOLVED` + `RECONCILIATION_PERIOD_LOCKED` inline messages.

### Tests

- resolve api: domain-facing token (`getOperatorToken` absent); body `{ resolutionType, note }`; **`Idempotency-Key` absent assertion**; **`X-Operator-Reason` absent assertion**; `409`/`422`/`404`/`503`/timeout mapping.
- resolve proxy: POST route; empty `note` → `400` with **no** upstream call; `409`/`422` passthrough; domain-facing token (not operator).
- component: resolve action shown **only on OPEN**; confirm-gated (empty `note` → confirm disabled, no fetch); on success → `RESOLVED` reflected; `409` already-resolved inline; `422` period-locked inline; Escape/cancel; axe-clean.
- regression: §3.1 attestation-marker count stays **16**; the FE-072 read suites + all IAM/wms/scm/finance/erp suites still pass; the ledger read calls remain mutation-artifact-free (only the resolve is a mutation).

## Out of Scope

- Every **other** ledger mutation: `POST /entries` (manual posting), `/revaluations`, `/settlements`, reconciliation `POST /statements` (ingest) — operator-domain, deferred.
- Account-level drill + statement-detail reads (forward-declared from FE-072).
- Any change to the finance `reconciliation-api.md` producer contract (finance owns it — cross-reference only).
- A maker/checker approval over the resolve; bulk resolve.

# Acceptance Criteria

- [ ] Spec PR merged: § 2.4.7.1 read-binding carve-out + resolve sub-binding + architecture.md mutation entries, **before** the impl PR.
- [ ] An operator can resolve an **OPEN** reconciliation discrepancy from the queue/detail; on success it reflects `RESOLVED` + `resolution`.
- [ ] Header matrix is producer-faithful (test-asserted): domain-facing token; body `{ resolutionType, note }`; **no `Idempotency-Key`**; **no `X-Operator-Reason`**; no `X-Tenant-Id`.
- [ ] Confirm-gated + `note`-required: empty `note` → confirm disabled / no fetch.
- [ ] Resolve offered **only** on OPEN discrepancies (a RESOLVED row exposes no resolve affordance).
- [ ] Resilience: `409 RECONCILIATION_ALREADY_RESOLVED` → inline "already resolved, refresh"; `422 RECONCILIATION_PERIOD_LOCKED` → inline "period closed"; `404` → inline; `401` → re-login; `403` → inline; `503`/timeout → ledger section degrades.
- [ ] §3.1 attestation-marker count stays **16**; FE-072 read suites + sibling suites regression-green.
- [ ] CI: Build & Test (console-web unit/component + lint + a11y) green; no required check failing at merge.

# Related Specs

- [`console-integration-contract.md` § 2.4.7.1](../../specs/contracts/console-integration-contract.md) (this task — resolve-mutation carve-out) + § 2.4.8 erp delegation-revoke (the body-reason mutation precedent reused) + § 2.5 (resilience).
- [`console-web/architecture.md`](../../specs/services/console-web/architecture.md) — ledger-ops mutation entries + Auth Flow.

# Related Contracts

- finance [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) **§ 2** `POST /reconciliation/discrepancies/{id}/resolve` (authoritative, consumed read-write, **unchanged**) + § 4/§ 5 (the discrepancy read shapes from FE-072).
- finance [`iam-integration.md`](../../../finance-platform/specs/integration/iam-integration.md) § *platform-console Operator Read Consumer* (TASK-FIN-BE-005 — the finance tenant gate; already merged).

# Edge Cases

- Resolve a RESOLVED discrepancy (stale UI / concurrent operator) → `409 RECONCILIATION_ALREADY_RESOLVED` → inline "already resolved, refresh" (refetch, no crash).
- Resolve a discrepancy whose statement date is in a CLOSED period → `422 RECONCILIATION_PERIOD_LOCKED` → inline "period closed — resolve in the next open period".
- Empty / whitespace-only `note` → confirm disabled, no fetch (the reason is the audit record — required).
- `resolutionType` is one of the three known values; the select offers exactly `{ MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`.
- The 11th-increment FX-difference `AMOUNT_MISMATCH` discrepancy (carrying both `externalRef` + `journalEntryId`) is resolvable like any other OPEN discrepancy.
- Discrepancy id unknown → `404 RECONCILIATION_DISCREPANCY_NOT_FOUND` → inline.
- Operator not finance-eligible → the section is blocked (FE-072) → no resolve affordance reachable.

# Failure Scenarios

- ledger `503` / timeout / network on the resolve POST → `LedgerUnavailableError` → inline "temporarily unavailable, try again" (the resolve affordance re-enables on retry); the ledger section degrades, shell + other sections intact.
- ledger `401` (session expired) mid-resolve → whole-session re-login.
- ledger `403` (token not finance-scoped) → inline "not scoped".
- Malformed error body → defensive flat-envelope parser, synthetic code, no throw.
- A stray `429` (undocumented) → surfaced `ApiError`, no fabricated backoff (the no-429 honesty, asserted).
