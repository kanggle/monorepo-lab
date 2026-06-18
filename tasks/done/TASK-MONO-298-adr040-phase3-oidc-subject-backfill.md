---
id: TASK-MONO-298
title: "ADR-MONO-040 Phase 3 (part A) — operator oidc_subject email→account_id backfill + seed (dual-key retained)"
status: done
scope: cross-cutting
projects: [iam-platform]
tags: [code, test, migration, backfill, identity, adr-040]
analysis_model: "Opus 4.8"
impl_model: "Opus 4.8"
created: 2026-06-18
---

# TASK-MONO-298 — ADR-040 Phase 3 (part A): oidc_subject backfill + seed

## Goal

ADR-MONO-040 Phase 3 retires the transitional **DUAL-KEY** operator-resolution machinery that
Phase 2 (TASK-MONO-295) introduced. Phase 3 is split into two ordered tasks (ADR-019 D6
dual-accept→remove discipline):

- **Part A = THIS task (MONO-298)** — ship the **backfill mechanism** that migrates
  `admin_operators.oidc_subject` from **email** to **account_id**, update the seeds, and
  **RETAIN the dual-key/email fallback** so nothing regresses. Independently mergeable, main stays
  GREEN.
- **Part B = follow-up (MONO-299, gated on A merged + verified)** — remove the email fallback,
  the transitional `account_id` claim, and the 4 gateways' legacy-email fallback. NOT in scope here.

This ordering is the point: you backfill and verify FIRST, then remove the fallback. Removing the
fallback before the data is migrated would break every operator whose `oidc_subject` is still email.

## Current state (Phase 2, verified)

- `admin_operators.oidc_subject` (admin-service, V0027) stores the operator **login email** today
  (federation seed `tests/federation-hardening-e2e/fixtures/seed.sql` seeds it = email). UNIQUE
  index `uk_admin_operators_oidc_subject`. There is **no separate `account_id` column** — the
  backfill rewrites `oidc_subject` itself to the account UUID.
- Resolution is account_id-first → email-fallback via the shared
  `OperatorOidcSubjectResolver` (admin-service `application/`), called by `TokenExchangeService`
  (login-time exchange) and `OperatorAssignmentCheckUseCase` (assume-tenant).
- auth-service has `GET /internal/auth/credentials/{accountId}/email` (account_id→email — the
  WRONG direction for backfill). The backfill needs **email→account_id**.
- `auth_db.credentials` (account_id UNIQUE, email, tenant_id) owns the email↔account_id mapping;
  admin-service **cannot** reach auth_db directly (cross-DB).

## Design decision (user-chosen)

**Production-shaped runnable backfill** (not a doc-only SQL recipe): an admin-service idempotent
maintenance endpoint that resolves each email-shaped operator's account_id via a NEW auth-service
internal endpoint and updates `oidc_subject` in place. Plus seed updates so the demo/e2e DB starts
account_id-keyed.

## Scope (work items)

### A. auth-service — internal email→account_id endpoint
1. Add an internal endpoint resolving **email → account_id**, e.g.
   `POST /internal/auth/credentials/account-id-by-email` with body `{ "email": ..., "tenantId": ... }`
   → `{ "accountId": <uuid|null> }`. **Use POST + body (NOT a path/query param) — email is PII and
   must not land in URLs/access logs** (consistent with the Phase-2 "no PII in query logs"
   discipline). Resolve via `CredentialRepository` (add `findByEmail`/`findByEmailAndTenantId` if
   absent). Admin-service-internal only (network-restricted, like the existing
   `/internal/auth/credentials/...` endpoint). Returns null accountId when no credential matches
   (fail-soft, not an error).
   - **CRITICAL — tenant scoping**: VERIFY whether `credentials.email` is globally unique or
     unique per `(email, tenant_id)`. If an email can map to different accounts across tenants, the
     lookup MUST be scoped by the operator's tenant to avoid cross-tenant mis-resolution. Scope the
     endpoint accordingly (pass tenantId). If email is globally unique for operator accounts,
     document that and the tenant param may be advisory. Get this right — a wrong account_id in
     `oidc_subject` would mis-route an operator.

### B. admin-service — idempotent backfill maintenance endpoint
2. Add `POST /internal/admin/operator-oidc-subject-backfill` (internal-only) that:
   - Enumerates `admin_operators` rows whose `oidc_subject` is **email-shaped** (non-null, contains
     `@`, not already a UUID).
   - For each, resolves account_id via the auth-service endpoint (A), passing the operator's
     `tenant_id` for scoping.
   - On resolve → `UPDATE admin_operators SET oidc_subject = <account_id>`; on no-match → **leave
     unchanged** (fail-soft — the operator stays resolvable via the retained email fallback), log +
     count.
   - **Idempotent**: re-running is a no-op for rows already UUID-shaped (only email-shaped rows are
     processed). Returns a report `{ scanned, updated, skippedAlreadyUuid, skippedNull, unresolved }`.
   - **Audit-log** each update (operator_id, old→new key shape — do NOT log the email PII value;
     log that it was email-shaped). Use the existing audit mechanism if admin-service has one.
   - Consider a simple guard against concurrent runs if cheap; the UPDATE is idempotent regardless.

### C. Seed update (demo/e2e starts account_id-keyed)
3. Update `tests/federation-hardening-e2e/fixtures/seed.sql` (+ any dev seed / `V00xx` seed
   migration that seeds `admin_operators`) so `oidc_subject` = the **matching seeded
   `credentials.account_id`** (deterministic UUIDs — find the seeded credential account_id for each
   operator and set oidc_subject to it). With the dual-key fallback retained, account_id-first
   resolves directly; this sets up Part B's clean fallback removal. Keep the `email` column
   unchanged (login still uses it).

### D. Docs
4. Document the two internal endpoints (auth email→account_id; admin backfill) in the relevant
   `specs/contracts/http/internal/` (or integration spec) — internal, additive.
5. ADR-MONO-040 §6 — append a row: **Phase 3 part A (MONO-298) — backfill mechanism + seed shipped;
   dual-key fallback retained; fallback/transitional-claim removal = Part B (MONO-299) gated on this
   merged + verified.** No decision reversed (Phase 3 was already named deferred in the Phase-2 row).

### E. Tests (unit; Testcontainers ITs CI-only on this host — ensure they compile)
6. auth endpoint: resolves email→account_id; null when absent; tenant scoping (if applicable).
7. admin backfill endpoint: email-shaped row → updated to account_id; already-UUID row → skipped
   (idempotent re-run no-op); null oidc_subject → skipped; unresolved (auth returns null) → left
   unchanged + counted; report counts correct; audit logged without PII.
8. Regression: the existing dual-key resolution tests stay GREEN (fallback retained — NOT removed).

## Acceptance Criteria

- AC-1: auth-service internal `email→account_id` endpoint exists (POST + body, **no PII in URL**),
  tenant-scoped as required, returns null account_id (not an error) when no credential matches.
- AC-2: admin-service backfill endpoint is **idempotent** — processes only email-shaped
  `oidc_subject`, updates to account_id, re-run is a no-op (UUID-shaped skipped), returns a report,
  audit-logs each update without logging the email value.
- AC-3: unresolved operator → `oidc_subject` left unchanged + counted (fail-soft); still resolvable
  via the **retained** email fallback (no operator regresses).
- AC-4: federation seed (+ dev seeds) updated so `admin_operators.oidc_subject` = the seeded
  `account_id`; demo/e2e operators resolve account_id-first.
- AC-5: **dual-key fallback, transitional `account_id` claim (`TenantClaimTokenCustomizer`), and the
  4 gateways' legacy-email fallback are all RETAINED/untouched** (removal is Part B). Verify via diff
  they are not modified.
- AC-6: builds + unit tests GREEN —
  `./gradlew :projects:iam-platform:apps:auth-service:test`
  `./gradlew :projects:iam-platform:apps:admin-service:test`.
- AC-7: ADR-040 §6 row added (Part A shipped, Part B = follow-up gated); the two internal endpoints
  documented.

## Related Specs / Contracts

- `docs/adr/ADR-MONO-040-...md` §D3 (Phase 2/3), §6 (decision log — the Phase-2 row names this
  backfill as deferred Phase-3 work)
- `specs/contracts/http/internal/` (admin ↔ auth internal endpoints) — add the two new ones
- admin-service `OperatorOidcSubjectResolver`, `TokenExchangeService`, `OperatorAssignmentCheckUseCase`
  (the dual-key consumers — RETAINED, used to verify no regression)
- admin-service `AdminOperatorJpaEntity` + V0027 migration (`oidc_subject` column)
- auth-service `CredentialRepository` / `InternalCredentialController` (existing account_id→email
  endpoint — mirror its security/wiring for the new email→account_id one)
- `tests/federation-hardening-e2e/fixtures/seed.sql` (operator seed — oidc_subject email→account_id)

## Edge Cases

- **Email-shape detection**: treat non-null `oidc_subject` containing `@` (and not UUID-parseable)
  as email-shaped. UUID-parseable → already migrated, skip. Null → skip.
- **Cross-tenant email collision**: if `credentials.email` is unique per tenant, scope the lookup
  by the operator's tenant_id (AC-1 CRITICAL note) — never resolve to another tenant's account.
- **Super-admin operators** (`tenant_id = '*'` or multi-assignment): determine the correct account
  resolution (an operator has ONE account_id regardless of assigned tenants) — verify against the
  seed and the credentials model.
- **auth-service unavailable during backfill**: unresolved → fail-soft (left unchanged, retried on
  re-run). The endpoint must not abort the whole batch on one failure.
- **Re-run after partial backfill**: idempotent — already-UUID rows skipped, only remaining
  email-shaped rows processed.

## Failure Scenarios

- **Wrong account_id written** (cross-tenant collision): mitigated by tenant-scoped resolution
  (AC-1). A wrong `oidc_subject` would mis-authorize an operator — get the scoping right + test it.
- **Removing the fallback in this task**: OUT OF SCOPE — Part B. If you touch the fallback,
  transitional claim, or gateway filters, that is a scope violation (AC-5).
- **PII leak**: email in a URL/log → use POST body + log only the key-shape, never the email value.
- **Backfill not idempotent**: a non-idempotent runner that re-processes UUID rows could mis-resolve
  → enforce the email-shape filter (AC-2).
