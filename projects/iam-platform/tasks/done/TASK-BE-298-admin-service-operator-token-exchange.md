# Task ID

TASK-BE-298

# Title

admin-service operator token exchange (GAP OIDC → operator token, RFC 8693)

# Status

done

# Owner

backend

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

- **depends on**: ADR-MONO-014 ACCEPTED (TASK-MONO-110). This task is § D5 step 1.
- **prerequisite for**: platform-console `TASK-PC-FE-002a` (console exchange wiring + `console-integration-contract.md` §2.1/§2.2 self-contradiction fix) → which is the prerequisite for ADR-MONO-013 Phase 2 (`TASK-PC-FE-002`+ operator-parity slices). Phase 2 stays PAUSED until **this task is merged**.
- **spec-first**: spec/contract changes (below) land **before** any production code in the same PR; code follows the reconciled spec.

---

# Goal

Implement the GAP-side operator-auth bridge decided by **ADR-MONO-014 (ACCEPTED)** § D2/D3: a single new admin-service endpoint that exchanges a valid **GAP OIDC `platform-console-web` access token** for a short-lived **admin-service operator token** (`token_type=admin`, `iss=admin-service`), so the platform-console can call `/api/admin/**` operator endpoints (incl. the BE-296 registry) without widening the operator trust boundary (Option A was rejected in ADR-014 D1).

After this task:

- admin-service exposes `POST /api/admin/auth/token-exchange` (RFC 8693 grant) that validates the GAP OIDC subject token against auth-service JWKS + `platform-console-web` issuer/audience, resolves the OIDC subject to an `admin_operators` row **fail-closed**, and mints the existing operator token with tenant scope taken **from the operator row** (ADR-002 `tenant_id='*'` sentinel), never from the OIDC token.
- The "Admin IdP Boundary" invariant is preserved: admin-service still **self-issues** the operator token and still does not let a foreign token past `OperatorAuthenticationFilter`; the exchange is an additional *minting* path (sibling of the password+TOTP login mint), explicitly ADR-014-sanctioned.
- GAP specs/contracts are reconciled spec-first (ADR-014 § D4 GAP-side bullets).
- Testcontainers ITs prove valid-exchange, fail-closed-no-mapping, scope-from-operator-row, subject-token-invalid/expired, and regression of the existing password+TOTP operator login.

# Scope

## In Scope

### Spec-first (lands before code, ADR-014 § D4 GAP-side reconciliation)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` — add the `POST /api/admin/auth/token-exchange` contract: RFC 8693 request (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token`, `subject_token_type=urn:ietf:params:oauth:token-type:access_token`), success response (operator access token + its TTL; refresh model = re-exchange, no separate operator-refresh state per ADR-014 D2), error responses (subject-token invalid/expired → 401; no operator mapping → 401 fail-closed; wrong grant/params → 400), and its placement under the gateway public-path `/api/admin/**` subtree + Authentication Exceptions list (it is callable *without* a prior operator JWT — like `/api/admin/auth/login`).
- `projects/global-account-platform/specs/services/admin-service/architecture.md` — amend the **"Admin IdP Boundary"** section with an ADR-014-sanctioned exception note: the exchange is an **additional operator-token minting path** (admin-service still self-issues; `OperatorAuthenticationFilter` is NOT widened to a 2nd issuer — Option A rejected). Canonical form intact (Identity table + `### Service Type Composition` H3 preserved, ADR-MONO-012).
- `projects/global-account-platform/specs/services/admin-service/security.md` — add a "GAP OIDC Subject-Token Validation" subsection: validate against auth-service JWKS (`/.well-known/jwks.json`), required `iss`/`aud` (= `platform-console-web` / GAP issuer), `exp`/`nbf`/signature (RS256), clock-skew tolerance; reject token_type that is not a GAP OIDC access token. Operator token minting reuses the existing signing key/claims (no new key).
- `projects/global-account-platform/specs/services/admin-service/data-model.md` — record the **OIDC subject ↔ operator link-key sub-decision** (ADR-014 D3): choose `admin_operators.oidc_subject VARCHAR` (provisioned, explicit, deterministic) **or** verified-email match; document the choice + a forward Flyway migration if a column is added (forward-only, idempotent, per data-model migration policy). The OIDC token MUST NOT elevate scope — scope is read from `admin_operators.tenant_id` only.
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Authentication — add a note: the operator token is now obtained via the new exchange; the producer requirement (`token_type=admin`, `iss=admin-service`) is **unchanged**.

### Code (admin-service, follows the reconciled spec)
- `POST /api/admin/auth/token-exchange` controller + application service: subject-token validation (auth-service JWKS client — reuse existing JWKS/JWT infra if present, else a focused validator), OIDC-subject→`admin_operators` resolution (fail-closed → existing `OperatorUnauthorizedException` / `401 TOKEN_INVALID`), operator-token minting via the **existing** operator-JWT issuer (same path as login success; reuse, do not duplicate claim assembly), tenant scope strictly from the operator row.
- Gateway public-path entry for `/api/admin/auth/token-exchange` (same pattern as the registry/login public paths) so the call reaches admin-service without a pre-existing operator JWT.
- Flyway migration **only if** the link-key sub-decision adds `admin_operators.oidc_subject` (forward-only, idempotent guard, GAP-only — consistent with the auth-service V0016 lesson: structural, normalization-immune, no cross-statement user variable).

### Tests
- Testcontainers ITs (CI-Linux authoritative; local Docker may be unavailable — skip cleanly): (1) valid GAP OIDC token + mapped operator → operator token with `token_type=admin`/`iss=admin-service` and scope from the operator row; (2) valid OIDC token, **no** `admin_operators` mapping → `401` fail-closed (no token minted); (3) SUPER_ADMIN operator → `tenant_id='*'` scope from row, not from OIDC; (4) subject token invalid signature / wrong issuer / wrong audience / expired → `401`; (5) **regression**: existing `POST /api/admin/auth/login` (password+TOTP) operator-token path unchanged; `OperatorAuthenticationFilter` still rejects a raw GAP OIDC token on `/api/admin/**` (Option A stays rejected).
- Unit tests for the subject-token validator + the OIDC-subject→operator resolver (fail-closed branch).

## Out of Scope

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.1/§2.2 self-contradiction rewrite + console server-side exchange wiring — that is **ADR-014 § D5 step 2 = `TASK-PC-FE-002a`** (platform-console project, cross-project; this task is GAP project-internal). BE-298 only adds the GAP-side `console-registry-api.md` §Authentication note.
- Phase 2 operator-parity screens (`TASK-PC-FE-002`+).
- Option A/C/D (rejected in ADR-014 D1) — do not widen `OperatorAuthenticationFilter` to accept OIDC; do not add an admin credential login to the console; do not unify operator/user IdP.
- Operator-refresh state for exchanged tokens — ADR-014 D2: console re-exchanges with its GAP-rotated token; no separate operator-refresh row for the exchange path.
- admin-web retirement (ADR-MONO-013 Phase 3, parity-gated).

# Acceptance Criteria

- [ ] GAP spec/contract reconciliation (admin-api.md + admin-service architecture.md + security.md + data-model.md + console-registry-api.md §Authentication) merged **in the same PR, before/with** the code; canonical architecture.md form intact.
- [ ] `POST /api/admin/auth/token-exchange` validates the GAP OIDC subject token (issuer/audience/exp/signature against auth-service JWKS) and rejects invalid/expired with `401`.
- [ ] OIDC subject → `admin_operators` resolution is deterministic and **fail-closed** (no mapping → `401`, no token minted); the link-key sub-decision (oidc_subject column vs verified email) is documented in data-model.md and implemented as specced.
- [ ] Minted token is the **existing** operator token (`token_type=admin`, `iss=admin-service`, RS256, same signing key/JWKS), TTL ≤ the operator access TTL; tenant scope is read from `admin_operators.tenant_id` (incl. `'*'` SUPER_ADMIN sentinel), never from the OIDC token.
- [ ] `OperatorAuthenticationFilter` is unchanged and still rejects a raw GAP OIDC token on `/api/admin/**` (Option A remains rejected); the exchange is a separate minting path only.
- [ ] Testcontainers ITs (valid / no-mapping-401 / scope-from-row / subject-invalid-401 / login-regression) pass on CI Linux; unit tests for validator + resolver pass.
- [ ] `:admin-service:test` (+ any touched service) BUILD SUCCESSFUL; CI path-filter classifies correctly; zero regression to existing GAP operator auth.
- [ ] ADR-MONO-014 § D5 step 1 satisfied; task references ADR-MONO-014 + dependency markers; on merge, `TASK-PC-FE-002a` becomes unblocked.

# Related Specs

> Target project = `global-account-platform`. Target service = `admin-service`. Follow `platform/entrypoint.md`; admin-service `Service Type` per its `architecture.md` → read the matching `platform/service-types/<type>.md` (exactly one).

- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (§ D2/D3/D4/D5 — authoritative decision)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5 (the deferred bridge this realises)
- `projects/global-account-platform/specs/services/admin-service/architecture.md` § "Admin IdP Boundary" / operator-JWT issuance
- `projects/global-account-platform/specs/services/admin-service/security.md` (operator JWT boundary, bootstrap-token precedent for a scoped mint)
- `projects/global-account-platform/specs/services/admin-service/data-model.md` (`admin_operators` schema; oidc_subject link-key)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` (auth endpoints + Authentication Exceptions subtree)
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Authentication (BE-296 producer requirement — unchanged)
- `projects/global-account-platform/specs/services/auth-service/` (SAS / OIDC JWKS — subject-token issuer; `platform-console-web` client = auth-service V0015)
- GAP `docs/adr/ADR-002` (`tenant_id='*'` platform-scope sentinel), GAP `ADR-001` (OIDC AS), GAP `ADR-003` (`platform-console-web` public-client lineage)
- `platform/hardstop-rules.md` (HARDSTOP-06/09 — the mandate ADR-MONO-014 discharges)

# Related Skills

- `.claude/skills/` — backend-engineer (Spring Security resource-server / JWT validation), api-designer (RFC 8693 contract), security review.

---

# Related Contracts

- **Changed (this task, spec-first)**: `admin-api.md` (new `POST /api/admin/auth/token-exchange`), `console-registry-api.md` § Authentication (note only — producer requirement unchanged).
- **Not changed here**: `console-integration-contract.md` (platform-console; ADR-014 D5 step 2 = `TASK-PC-FE-002a`).

---

# Target Service

- `global-account-platform` / `apps/admin-service` (operator IdP boundary owner; mints operator tokens; owns `OperatorAuthenticationFilter` + ADR-002 tenant scope).
- `apps/gateway-service` (public-path entry for the new endpoint — same pattern as login/registry).

---

# Architecture

- admin-service `Service Type` governs (per its `architecture.md`); resolve and read exactly the matching `platform/service-types/<type>.md`.
- Hexagonal: subject-token validation + OIDC-subject→operator resolution are application/domain concerns; the auth-service JWKS client is an infrastructure adapter. Operator-token minting **reuses** the existing issuer component (no claim-assembly duplication).
- ADR-014 Model B exception is explicit and bounded: one new minting endpoint; the self-issuing IdP boundary and `OperatorAuthenticationFilter` single-issuer invariant are preserved.

---

# Implementation Notes

- Spec-first is a hard gate (ADR-014 § D4; HARDSTOP-06 discipline): reconcile admin-api.md / architecture.md / security.md / data-model.md / console-registry-api.md §Authentication, then implement to the reconciled spec, in one PR.
- Reuse existing infra: auth-service JWKS validation may already have a client/validator (resource-server config elsewhere in GAP); prefer reuse over a new JWKS fetcher. Operator-token minting MUST go through the existing login-success issuer path.
- Fail-closed everywhere: any ambiguity in subject-token validation or operator resolution → `401`, never a minted token. No scope ever derived from the OIDC token.
- Migration discipline (if `oidc_subject` column added): forward-only, idempotent, MySQL-structural, no cross-statement user variable, with a non-Docker shape-pin test — directly apply the TASK-BE-297 V0016 cycle-3 lesson (byte-identical CI failure = fix-not-applied; cross-statement state unreliable under Flyway).
- Recommend implementation model: **Opus** (cross-service identity, RFC 8693, fail-closed mapping, security-critical — interpretive). Dispatch `Agent(subagent_type="backend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring (sandbox push regex).

---

# Edge Cases

- GAP OIDC token valid but issued to a *different* client (not `platform-console-web`) → reject (audience check), `401`.
- OIDC subject maps to a **deactivated/locked** operator → fail-closed `401` (do not mint); same as no mapping.
- SUPER_ADMIN operator → scope `tenant_id='*'` from the row; the OIDC token carrying any tenant claim is ignored.
- auth-service JWKS unreachable → fail-closed `401`/`503` (no token minted; do not fall back to trusting the token unverified).
- Clock skew between auth-service and admin-service → bounded tolerance in `exp`/`nbf` validation (documented in security.md).
- Replay: exchanged token is short-lived (≤ operator access TTL); console re-exchanges on GAP refresh — no long-lived operator state from the exchange path.

---

# Failure Scenarios

- Option A creep (widening `OperatorAuthenticationFilter` to accept OIDC) → explicitly rejected by ADR-014 D1; regression IT pins that a raw OIDC token still 401s on `/api/admin/**`.
- Scope leak: deriving tenant scope from the OIDC token instead of the operator row → AC + IT (SUPER_ADMIN scope-from-row) prevent it; security review gate.
- Spec/code drift: implementing before reconciling specs → HARDSTOP-06; AC binds spec reconciliation into the same PR ahead of code.
- console-integration-contract.md left self-contradictory → tracked: it is ADR-014 D5 step 2 / `TASK-PC-FE-002a`, not silently dropped; BE-298 cross-references it.
- Migration no-op regression (if column added) → mitigated by the TASK-BE-297 V0016 cycle-3 discipline (structural, idempotent, non-Docker shape-pin).

---

# Test Requirements

- Testcontainers ITs (CI-Linux authoritative): valid exchange / no-mapping-401 / scope-from-operator-row / subject-token-invalid|expired-401 / existing-login-regression / `OperatorAuthenticationFilter`-still-rejects-raw-OIDC.
- Unit: subject-token validator (issuer/audience/exp/signature branches) + OIDC-subject→operator resolver (mapped / unmapped fail-closed / deactivated).
- If `oidc_subject` migration added: non-Docker migration shape-pin test (structural functions present; no cross-statement user variable; idempotency guard NULL-safe) — TASK-BE-297 V0016 pattern.
- `:admin-service:test` (+ touched services) BUILD SUCCESSFUL; ADR/spec internal-link lint clean.

---

# Definition of Done

- [ ] GAP specs/contracts reconciled (spec-first, same PR, ahead of code)
- [ ] `POST /api/admin/auth/token-exchange` implemented per the reconciled spec (fail-closed, scope-from-row, existing-issuer reuse)
- [ ] `OperatorAuthenticationFilter` / Admin IdP self-issuing boundary unchanged (Option A stays rejected)
- [ ] Testcontainers ITs + unit tests green on CI; zero regression to existing operator auth
- [ ] Acceptance Criteria all satisfied; ADR-MONO-014 § D5 step 1 closed
- [ ] Ready for review (on merge → `TASK-PC-FE-002a` unblocked; Phase 2 still gated on that)
