# TASK-MONO-462 — Reconcile TOKEN_TENANT_MISMATCH HTTP status: platform catalog (403) vs code/contract (401/400)

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (a source-of-truth ruling with a real semantic question, not a mechanical doc fix)

> Root-level because the fix touches `platform/error-handling.md` (shared platform file, § Task Rules). Split out of the iam
> reconciliation task (`TASK-BE-553`) deliberately: this is a cross-layer source-of-truth conflict, not iam-internal doc drift.
> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`, CONFIRMED).

---

## Goal

Three artifacts disagree on the HTTP status for `TOKEN_TENANT_MISMATCH`, and the disagreement crosses the source-of-truth
hierarchy — so it cannot be silently "corrected" in one direction. Decide the canonical status and align all artifacts.

**Measured state (current `main`):**
- `platform/error-handling.md:510` — `| TOKEN_TENANT_MISMATCH | 403 | Token tenant_id claim does not match the targeted resource tenant |`.
- Legacy REST refresh (`POST /api/auth/refresh`): `apps/auth-service/.../presentation/exception/AuthExceptionHandler.java:35-40`
  returns **401**; `TokenTenantMismatchException.java:5` javadoc says "Maps to HTTP 401"; `auth-api.md:435` also says **401**.
- SAS OAuth2 token-endpoint refresh grant: `SasRefreshTokenAuthenticationProvider.java:196-199` throws
  `OAuth2Error(INVALID_GRANT, "TOKEN_TENANT_MISMATCH")` → RFC 6749 §5.2 `invalid_grant` → **400**.

## The source-of-truth tension (must be resolved, not assumed)

Per `CLAUDE.md` § Source of Truth Priority, `platform/` (layer 5) **outranks** `specs/contracts/` (layer 6). So the catalog's
403 formally outranks the auth-api contract's 401 — which would make the **code** the drifted side, not the catalog.

**Argument the catalog (403) is intended:** its own siblings for the same "authenticated-but-not-authorized-for-this-resource"
shape are 403 — `OAUTH_INSUFFICIENT_SCOPE` (`:513`) and `SESSION_OWNERSHIP_MISMATCH` (`:520`). A valid token targeting the
wrong tenant is semantically Forbidden, not Unauthenticated. This is a coherent reason 403 was catalogued.

**Argument the catalog (401) is stale:** three lower-layer artifacts (contract + exception javadoc + handler) deliberately
chose 401, and only the catalog says 403 — possibly the catalog was never updated when the REST path was implemented.

This task must **rule**, not paper over. Note also the SAS path is a *different status again* (400 invalid_grant) for the same
semantic error — the ruling must address whether that is acceptable divergence (REST vs OAuth2 token endpoint) or a third bug.

## Scope

**In scope:** a documented ruling + the alignment it implies:
- **If 403 is canonical:** change `AuthExceptionHandler` (and the exception javadoc) to return 403; keep catalog; update
  `auth-api.md:435` to 403; add a note that the SAS token endpoint surfaces the same semantic error as `400 invalid_grant` per RFC 6749.
- **If 401 is canonical:** change `platform/error-handling.md:510` to 401 (and record why the higher layer defers to the
  contract here — e.g. the catalog was stale); keep code/contract; footnote the SAS 400 path.

**Out of scope:** unrelated error codes; the iam doc drifts in `TASK-BE-553`.

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins on facts, ruling decides the target)** — Re-confirm all three statuses at current `main`
  (the REST handler, the exception javadoc, the SAS provider, the catalog line, and `auth-api.md`). Line numbers may shift.
- **AC-1 (the ruling)** — Explicitly choose 403 or 401 as canonical for the REST path, with the rationale (semantic + SoT
  priority + sibling-consistency) recorded in the PR body. Do not leave the catalog and code disagreeing.
- **AC-2** — Every artifact for the REST path (catalog, `auth-api.md`, exception javadoc, handler) states the chosen status;
  a grep for `TOKEN_TENANT_MISMATCH` shows no residual disagreement.
- **AC-3** — The SAS token-endpoint path (400 `invalid_grant`) is either explicitly documented as acceptable RFC-6749 divergence
  or filed as a follow-up if judged a defect — not silently ignored.
- **AC-4** — If code changes (403 option), add/adjust a test asserting the new status on the REST refresh path.

## Related Specs
- `platform/error-handling.md` § Auth / Token
- `projects/iam-platform/specs/contracts/http/auth-api.md:435`
- `CLAUDE.md` § Source of Truth Priority (layer 5 vs 6)

## Related Contracts
- `auth-api.md` error table (the contract that currently says 401).

## Edge Cases
- The SAS and REST paths are genuinely different endpoints (OAuth2 token endpoint vs custom REST) — RFC 6749 constrains the SAS
  path to `invalid_grant` (400), so full status unification across both may not be correct; the ruling should say so.
- `platform/` is not classifier-blocked; edits land normally.

## Failure Scenarios
- **F1 — editing the catalog to 401 by reflex because "3 artifacts agree".** The catalog *outranks* the contract; majority
  count is not the tiebreaker — semantics + SoT priority are. AC-1 forces an actual ruling.
- **F2 — ignoring the SAS 400 path.** Guarded by AC-3; the same error code surfaces two statuses today.
