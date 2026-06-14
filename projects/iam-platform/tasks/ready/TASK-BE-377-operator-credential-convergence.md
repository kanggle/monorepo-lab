# Task ID

TASK-BE-377

# Title

ADR-MONO-035 **O2 / step 4c** — operator login/credential convergence: the unified IAM OIDC credential becomes the operator's **primary** login (admin-panel authenticates via the OIDC base login + the already-wired ADR-014 `POST /api/admin/auth/token-exchange`), and `admin_operators.password_hash` is **demoted to nullable break-glass** (an emergency local login retained for when the IdP/OIDC path is unavailable; full removal deferred). TOTP/2FA stays admin-service-internal (O4). Realizes ADR-032 D6-A ("one account = one credential") for operators without a risky auth-path big-bang.

# Status

ready

# Owner

backend

# Task Tags

- iam
- admin-service
- security
- adr-035

---

# Dependency Markers

- **executes**: ADR-MONO-035 (ACCEPTED) **O2** + **O5 step 4c**. Completes ADR-MONO-034 **U2** (the deferred operator login/credential consolidation).
- **depends on**: step 4a (BE-376) + step 4b (MONO-261/262/263) — operators already carry domain `roles` and `account_type` is fully dropped; the OIDC credential is now the operator's only consumer-side credential.
- **reuses**: the already-wired ADR-014 token-exchange (`TokenExchangeService`, BE-298) — no new auth surface; `OperatorAuthenticationFilter`; `AdminLoginService` (which already null-guards a missing `password_hash`).
- **keeps internal**: TOTP/2FA (`admin_operator_totp`), `oidc_subject`, `finance_default_account_id` stay on admin-service / `admin_operators` (O4).
- **safety**: break-glass `password_hash` is **demoted, not removed** (O6 — operator login availability preserved; full removal is a deferred follow-up once OIDC-only admin login is proven).

# Goal

`admin_operators.password_hash` becomes **nullable**. An operator may now exist **without** a local password — their primary (and only required) login is the IAM OIDC credential, exchanged into an operator token via the existing ADR-014 token-exchange. The local password login (`POST /api/admin/auth/login`) is retained as **break-glass**: it works only for operators that still have a `password_hash`; a null-hash operator cannot local-login (already the `AdminLoginService` behavior) and must use OIDC. Operator provisioning (`POST /api/admin/operators`) makes the password **optional** — when omitted the operator is created OIDC-only (`password_hash = NULL`); when supplied it is hashed and retained as break-glass.

# Scope

- **NEW migration** `projects/iam-platform/apps/admin-service/src/main/resources/db/migration/V0037__demote_password_hash_to_nullable_break_glass.sql` — `ALTER TABLE admin_operators MODIFY COLUMN password_hash VARCHAR(255) NULL` (drop `NOT NULL`). Plain MODIFY (no `COMMENT`/`AFTER` ordering concern, §22). No backfill (existing rows keep their hash = break-glass).
- **MODIFY** `AdminOperatorJpaEntity.java`: `@Column(name = "password_hash", length = 255, nullable = false)` → `nullable = true`; the `create(...)` factory accepts a nullable `passwordHash` (OIDC-only operator = null); doc the break-glass semantics.
- **MODIFY** `CreateOperatorUseCase.java`: `passwordHash = (password == null || password.isBlank()) ? null : passwordHasher.hash(password)` (OIDC-only when absent; break-glass hash when supplied). No other flow change (identity link 3d, roles, audit unchanged).
- **MODIFY** `CreateOperatorRequest.java`: drop `@NotBlank` from `password` (keep `@Size(min=10,max=255)` + `@Pattern` — Bean Validation skips both on null, so an absent password is accepted and a supplied one still enforces policy).
- **MODIFY** `AdminLoginService.java`: doc the existing null-`password_hash` branch as the **break-glass-absent** case (null hash → `INVALID_CREDENTIALS`, the operator must use OIDC). **No behavior change** — the L80 guard already does this; the timing-leveled dummy verify still runs.
- **SPECS**: `data-model.md` (`password_hash` `NOT NULL` → `NULL`; note break-glass + OIDC-primary), `security.md` (NEW § *Operator Credential Convergence* — OIDC primary via token-exchange, password break-glass nullable, TOTP unchanged), `admin-api.md` (`POST /api/admin/operators` `password` → optional with the OIDC-only note; `POST /api/admin/auth/login` note that null-hash operators 401), `architecture.md` (§Operator-Token Minting Paths — OIDC token-exchange is the primary operator login; password login is break-glass).
- **TESTS** — admin-service Testcontainers IT is **authoritative**:
  - `TokenExchangeIntegrationTest`: NEW case — an OIDC-only operator (seeded with `password_hash = NULL`, `oidc_subject` set) successfully token-exchanges and the minted operator token authenticates `/api/admin/me` (proves OIDC primary works for a password-less operator).
  - `AdminLoginControllerTest` (or a login IT): a null-`password_hash` operator presenting a password → `401 INVALID_CREDENTIALS` (break-glass absent); a break-glass operator (with hash) → login still works (regression).
  - `CreateOperatorUseCaseTest`: `password = null/blank` → `NewOperator.passwordHash() == null`; `password` supplied → hashed.
- NO `password_hash` **removal** (deferred). NO TOTP change. NO token-exchange logic change (reused as-is).

# Acceptance Criteria

- **AC-1** `admin_operators.password_hash` is nullable after V0037; an operator row with `password_hash = NULL` persists and Hibernate `ddl-auto=validate` passes (CI iam Testcontainers IT).
- **AC-2** An OIDC-only operator (`password_hash = NULL`, `oidc_subject` set, `status=ACTIVE`) successfully exchanges its OIDC subject token for an operator token that authenticates `/api/admin/**` (IT).
- **AC-3** A null-`password_hash` operator cannot local-login (`POST /api/admin/auth/login` → `401 INVALID_CREDENTIALS`); a break-glass operator (with `password_hash`) still logs in unchanged (regression).
- **AC-4** `POST /api/admin/operators` with no `password` creates an OIDC-only operator (`password_hash = NULL`); with a `password` creates a break-glass operator (hashed). Password policy (`@Size`/`@Pattern`) still enforced when supplied.
- **AC-5** Net-zero for existing operators: every existing row keeps its `password_hash` (no backfill, no down-migration); TOTP/2FA, `oidc_subject`, `finance_default_account_id`, roles, audit are byte-behavior-unchanged.
- **AC-6** admin-service Docker-free `:test` GREEN locally; CI `Integration (iam, Testcontainers)` is the authoritative schema/wiring/login-path gate.

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O2 + § O4 + § O5 4c + § O6)
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (§ U2 — the deferred consolidation this completes)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (the OIDC→operator token-exchange this makes primary)
- `projects/iam-platform/specs/services/admin-service/{data-model,security,architecture}.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` — `POST /api/admin/operators` (`password` optional), `POST /api/admin/auth/login` (null-hash → 401), `POST /api/admin/auth/token-exchange` (unchanged — already primary).
- `platform/contracts/jwt-standard-claims.md` — unchanged (operator credential storage is IdP-internal; no claim shape change).

# Edge Cases

- OIDC-only operator (null hash) presents a password → timing-leveled dummy verify still runs (no miss/wrong-password oracle), then `401` (break-glass absent).
- Operator with a break-glass `password_hash` → both paths work (OIDC token-exchange = primary; password login = break-glass fallback).
- Operator created with no password AND never provisioned an `oidc_subject` → un-authenticatable until `oidc_subject` is set (fail-SAFE lockout, not a bypass — provisioning concern, not a 4c bug).
- TOTP-required operator with a break-glass password → 2FA enforcement unchanged (O4); OIDC primary path is unaffected by TOTP in step 4 (deferred OIDC step-up, ADR-032 D4-B).

# Failure Scenarios

- If `password_hash` is **removed** (not just nullable) → breaks break-glass availability (O6) + out of step-4 scope; must be a nullable MODIFY only.
- If the null-hash branch in `AdminLoginService` is changed to mint a token without a password verify → operator-login bypass; the null-hash branch must stay fail-closed (`401`).
- If TOTP/2FA enforcement is moved or weakened → out of O4 scope; the security path must be untouched.
- If existing operators lose their `password_hash` (a backfill to NULL) → removes their break-glass; the migration must be additive (nullable) with no data change.
- If the token-exchange resolution is widened (e.g. to accept email instead of `oidc_subject`) → out of scope; the OIDC→operator link key stays `oidc_subject` (ADR-014 D3 / data-model).
