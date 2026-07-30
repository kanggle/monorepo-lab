# Task ID

TASK-BE-567

# Title

Revoke orphaned `membership-service-client` OAuth credential (V0009 seed) — zero consumers since TASK-MONO-394 retired its
only intended caller; deferred decision from TASK-MONO-400, now made by the user (2026-07-30)

# Status

done

# Owner

backend

# Task Tags

- code
- security
- db-migration

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Revoke the `membership-service-client` OAuth2 `client_credentials` registration seeded by
`V0009__seed_community_membership_oauth_clients.sql`. It has had **zero consumers** since iam-platform's own
`membership-service` — its only ever-intended caller — was retired by `TASK-MONO-394` (2026-07-14). `TASK-MONO-400`
(merged) measured this and explicitly deferred the revocation decision ("정리 여부는 이 티켓의 질문이 아니다" — whether to
clean it up is not that ticket's question). This task makes that deferred decision (user-selected 2026-07-30: revoke) and
executes it.

**`community-service-client`** — the sibling row seeded in the same `V0009` migration — is genuinely live (consumed by
`fan-platform`'s deployed `community-service` via `IamClientCredentialsTokenProvider` → `HttpMembershipChecker`, scope
`membership.read`) and **must not be touched, revoked, or have its scopes altered by this task.**

After this task: `membership-service-client` no longer exists in `oauth_clients` on any database that runs Flyway forward
from a clean state, and `auth-api.md`'s Registered Clients table no longer lists it as an active credential.

---

# Scope

## In Scope

- Add a new Flyway migration `V0029__revoke_membership_service_client.sql` under
  `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/` that removes **only** the
  `membership-service-client` row from `oauth_clients` (`DELETE FROM oauth_clients WHERE client_id =
  'membership-service-client'`).
- Update `projects/iam-platform/specs/contracts/http/auth-api.md` § Registered Clients: remove the
  `membership-service-client` row (the table documents each client's Flyway version, so removing the row is the correct
  form here — contrast with `platform/error-handling.md`'s RETIRED-marker convention, which is for a whole *section*, not
  a single contract-table row).
- Re-verify (AC-0) immediately before implementing that `membership-service-client` still has zero consumers on current
  `main` — time has passed since this was filed; a new consumer may have appeared.

## Out of Scope

- `community-service-client` — untouched, still live. Its row, its scopes, and the shared `oauth_scopes` rows
  (`account.read`, `membership.read`, tenant `fan-platform`) are unaffected — those are referenced by
  `community-service-client` too, and scopes live in each client's own JSON `scopes` column, not a join table, so deleting
  one client row cannot cascade into the other's grant.
- `V0009` itself — immutable, already-applied Flyway migration. Never edited (checksum-verified on apply).
- Building any real `membership-service` outbound-caller feature — that plan was superseded by `TASK-MONO-394` retiring
  iam's own `membership-service`; `fan-platform`'s separately-owned `membership-service` is a different, unrelated service
  and is not affected by this task in any way.
- Any admin-API (`TASK-BE-258`) runtime revocation flow against already-running databases — this is a seed-level fix so
  that fresh/CI/new-environment databases never receive the dead credential in the first place, not an operational action
  against a live production database (which is out of this task's reach/knowledge).

---

# Acceptance Criteria

- [ ] **AC-0 (re-verify gate)** — `git grep -n "membership-service-client"` across the whole repo, at implementation time,
      shows the same zero-runtime-consumer picture `TASK-MONO-400` recorded (hits only in the V0009 seed itself, docs,
      contracts, and closed task files — no `.java`/`.yml` construct that would send it as a live `client_id`). If a
      consumer now exists, **STOP** — this task's premise is false; re-scope instead of proceeding.
- [ ] **AC-1** — `V0029__revoke_membership_service_client.sql` deletes exactly the one `oauth_clients` row and nothing
      else. Diff confined to that new file plus the AC-2 contract edit.
- [ ] **AC-2** — `auth-api.md` § Registered Clients no longer lists `membership-service-client`.
- [ ] **AC-3** — `community-service-client`'s row and the `account.read`/`membership.read` `oauth_scopes` rows are
      unaffected — confirmed by re-reading the migration's `WHERE` clause (scoped to one `client_id`) and, if a local
      Testcontainers/DB run is available, by an actual Flyway migrate + query showing `community-service-client` still
      resolves and its scopes are unchanged.
- [ ] **AC-4** — Auth-service's existing test suite passes with the new migration applied (Flyway checksum/count tests if
      present; `BcryptHashPinTest` is unaffected since it pins the *hash value*, shared by several other still-live
      clients, not `membership-service-client` specifically — confirmed no test currently asserts
      `membership-service-client` resolves as a live client).
- [ ] **AC-5** — No `fan-platform` / `community-service` file touched.

---

# Related Specs

- `tasks/done/TASK-MONO-400-the-orphan-claim-is-false-fan-uses-those-credentials.md` — measured 0 consumers for
  `membership-service-client`, explicitly deferred the revoke-or-keep decision to a future ticket (this one).
- `tasks/done/TASK-MONO-394-*.md` — retired iam's own `membership-service`, the client's only intended caller.
  **HARDSTOP-05 frozen — read-only, do not edit.**
- `projects/iam-platform/PROJECT.md`

# Related Skills

- No dedicated skill found for Flyway migration authoring in this project — follow the existing migration files'
  established documentation style (header comment explaining why) directly.

---

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` § Registered Clients

---

# Target Service

- `auth-service` (iam-platform)

---

# Architecture

Follow `projects/iam-platform/specs/services/auth-service/architecture.md`.

---

# Implementation Notes

- `V0009` is never edited — Flyway migrations are immutable once applied (checksum-verified on every startup). The fix is
  an additive migration (`V0029`) that deletes the row, following the exact pattern any other seed-cleanup would use in
  this project.
- `oauth_clients.scopes` is a per-row JSON array column (see `V0009` — `'["account.read","membership.read"]'`), not a
  join table against `oauth_scopes`. Deleting the `membership-service-client` row cannot affect
  `community-service-client`'s own scope grant.

---

# Edge Cases

- If AC-0 finds a new consumer since this task was filed (2026-07-30), STOP and re-file as a "keep, document why" task
  instead of proceeding with revocation.
- Any database that has already applied `V0009` picks up `V0029` on its next Flyway run and the row disappears; any
  environment spinning up fresh applies both `V0009` then `V0029` in order, so it never carries the dead row past
  migration completion either way.

---

# Failure Scenarios

- **F1 — the DELETE also removes shared scope rows or touches `community-service-client`.** Guarded by AC-1/AC-3 — the
  migration's `WHERE` clause must be scoped to exactly one `client_id`; scopes are a separate, structurally unaffected
  table.
- **F2 — silently reintroducing the exact bug `TASK-MONO-400` caught**: assuming "service retired ⇒ client orphaned"
  without re-grepping consumers at execution time, since time has passed since this task was filed and observations can
  go stale. Guarded by AC-0 — re-measure, don't inherit the filing-time observation as still-true fact.

---

# Test Requirements

- Re-run auth-service's existing test suite (unit + any Testcontainers-backed migration/integration tests) with `V0029`
  present — no new test file is strictly required since no existing test asserts `membership-service-client` resolves as
  live (only `BcryptHashPinTest`'s code *comment* mentions it, not an assertion, and that test pins a hash value shared by
  several other still-live clients).

---

# Definition of Done

- [ ] `V0029` migration added.
- [ ] `auth-api.md` updated.
- [ ] Existing tests passing with the new migration applied.
- [ ] No other file touched.

---

# Provenance

Filed as the explicit follow-up `TASK-MONO-400` deferred ("membership-service-client 정리 여부는 이 티켓의 질문이 아니다—
관찰만 기록한다"), surfaced while auditing `platform/error-handling.md` commonalization for `TASK-MONO-496`/`TASK-MONO-497`
this session. The revoke-vs-keep decision was presented to the user 2026-07-30 and the user chose revoke.

분석=Sonnet 5 / 구현 권장=Sonnet 5 (single scoped DB migration + one contract-table-row edit; the judgment call was already
made by the user, not left to the implementer).
