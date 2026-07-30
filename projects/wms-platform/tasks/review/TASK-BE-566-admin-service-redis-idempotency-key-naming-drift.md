# Task ID

TASK-BE-566

# Title

Fix `admin-service` `RedisIdempotencyStore` Redis key entity segment
(`idem` → `idempotency`) — spec-aligned; `master-service` explicitly
excluded (its own spec pins `idem` as an intentional, ADR-governed
divergence, TASK-BE-293 WI-3)

# Status

review

# Owner

backend

# Task Tags

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

If any section is missing or incomplete, this task must not be implemented.

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능. `RedisIdempotencyStore` in all 5 wms
  services predates this task and is stable (last touched by
  `TASK-BE-527`/`TASK-BE-565`, neither of which changed the key prefix).
- **관련 (비차단)**: `TASK-BE-293` (done) is the origin of the
  `master-service`-vs-siblings key-shape divergence decision this task
  relies on to scope `master-service` OUT. Read
  `specs/services/master-service/idempotency.md` § "Cross-Service
  Idempotency Key Conventions (Intentional Divergence)" before touching
  anything idempotency-key-shaped in this project.
- **비-관련(혼동 주의)**: `TASK-BE-565` (done) touched the same 5
  `RedisIdempotencyStore` siblings but fixed `InMemoryIdempotencyStore`'s
  lock race — a different class, different bug. Do not confuse the two
  when grepping history.

---

# Goal

A monorepo-wide naming-convention audit compared `wms-platform`'s 5
`RedisIdempotencyStore` implementations against
[`platform/naming-conventions.md`](../../../../platform/naming-conventions.md)
§ Redis Keys (`{service}:{entity}:{identifier}`). 3 of 5 already use the
full `idempotency` entity segment (`inbound:idempotency:`,
`outbound:idempotency:`, `inventory:idempotency:`). 2 use an abbreviated
`idem`: `master:idem:` and `admin:idem:`.

**Investigation finding (this task's key contribution): the two `idem`
cases are NOT the same kind of defect.**

- **`admin-service` — genuine drift, safe to fix.**
  `specs/services/admin-service/idempotency.md` § 1.3 "Redis Storage
  Layout" already documents the canonical format as
  `admin:idempotency:{method}:{path_hash}:{idempotency_key}` — i.e. the
  service's own spec has *always* said `idempotency`, and the running
  code (`admin:idem:`) has silently drifted from its own already-written
  spec. Fixing the code to match the spec is a pure string-literal
  rename with no architectural content.

- **`master-service` — spec explicitly forbids this exact rename,
  excluded from this task.**
  `specs/services/master-service/idempotency.md` § "Storage" documents
  `master:idem:{SHA-256(idempotencyKey || ":" || method || ":" || path)}`
  as the canonical format, and § "Cross-Service Idempotency Key
  Conventions (Intentional Divergence)" is "the single authoritative
  reference for the WMS request-idempotency key shape/cap divergence,"
  reconciled by `TASK-BE-293` WI-3, **decision (B): document the
  divergence as intentional (NOT normalize)**. That section states in
  its own words: *"Normalizing would force a behavioural change across
  five services' implementation code (out of this spec task's scope by
  mandate) and would set a WMS-/portfolio-wide idempotency-key
  convention — that is ADR governance territory, not a unilateral
  per-task decision."* Renaming `master:idem:` → `master:idempotency:`
  — even as "only" an entity-segment string change — is exactly the
  normalization that section reserves for a future ADR-governed,
  cross-service task. Per `CLAUDE.md` Source of Truth Priority,
  `specs/services/` outranks an ungrounded task description, and per
  `rules/README.md`/`CLAUDE.md` Hard Stop discipline this is a
  spec-vs-task-instruction conflict that must not be worked around
  unilaterally. **`master-service` is therefore explicitly out of scope
  for this task** (see Scope § Out of Scope and Provenance below); if
  the portfolio later wants to force convergence, that is a future
  `TASK-MONO-*` + ADR per the master spec's own guidance, not this
  ticket.

This task fixes only `admin-service`.

---

# Scope

## In Scope

- `apps/admin-service/src/main/java/com/wms/admin/infra/idempotency/RedisIdempotencyStore.java`
  — rename `ENTRY_PREFIX`/`LOCK_PREFIX` string literals and the
  class-level Javadoc from `admin:idem:` / `admin:idem:lock:` to
  `admin:idempotency:` / `admin:idempotency:lock:`.
- Re-grep `apps/admin-service/` for any other literal occurrence of the
  old prefix strings (tests, docs, config) after the rename and fix any
  found (exhaustive grep performed during investigation found none
  outside the one file being changed — re-verify at implementation
  time in case of drift since investigation).

## Out of Scope

- **`master-service`** — `apps/master-service/src/main/java/com/wms/master/adapter/out/idempotency/RedisIdempotencyStore.java`
  (and its `RedisIdempotencyStoreTest.java`, and
  `specs/services/master-service/idempotency.md`) are **not touched by
  this task**. See Goal above — the service's own spec explicitly
  documents the current `idem` shape as an intentional, already-litigated
  (`TASK-BE-293`), ADR-governance-territory decision.
- `inbound-service` / `outbound-service` / `inventory-service`
  `RedisIdempotencyStore` — already correct (`{service}:idempotency:`),
  not touched.
- `InMemoryIdempotencyStore` (any service, any method) — different
  class, out of scope; see `TASK-BE-565` (done) for its own recent fix.
- Redis key **TTL** values, lock semantics, `StoredResponse` shape, or
  the `IdempotencyStore` interface — unchanged. This is a pure
  string-literal rename of the key namespace prefix; old-format
  `admin:idem:*` keys are not migrated (all keys in this store carry a
  mandatory TTL per `platform/naming-conventions.md` § Redis Keys — they
  expire naturally, no migration script needed).
- Any API/event contract — this is an internal Redis key naming detail,
  not a public contract.

---

# Acceptance Criteria

- [ ] AC-1 — `RedisIdempotencyStore.ENTRY_PREFIX` in `admin-service` ==
      `"admin:idempotency:"`; `LOCK_PREFIX` == `"admin:idempotency:lock:"`.
- [ ] AC-2 — Class-level Javadoc updated to reference the new prefixes
      (`admin:idempotency:{storageKey}` / `admin:idempotency:lock:{storageKey}`).
- [ ] AC-3 — `grep -R "admin:idem:"` (literal, not matching
      `admin:idempotency:`) across `apps/admin-service/` returns 0 hits
      after the change.
- [ ] AC-4 — `apps/master-service/**` byte-unchanged
      (`git diff --numstat origin/main -- apps/master-service` empty).
- [ ] AC-5 — `apps/inbound-service/**`, `apps/outbound-service/**`,
      `apps/inventory-service/**` byte-unchanged (not touched by this
      task at all).
- [ ] AC-6 — `admin-service` Gradle `test` task passes GREEN, both
      before (baseline) and after the change.

---

# Related Specs

> **Before reading further**: follow `platform/entrypoint.md` Step 0 —
> read `PROJECT.md`, then load `rules/common.md` plus any
> `rules/domains/<domain>.md` / `rules/traits/<trait>.md` matching the
> declared classification (`domain: wms`, `traits: [transactional,
> integration-heavy]`). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/naming-conventions.md` § Redis Keys (`{service}:{entity}:{identifier}`,
  mandatory TTL)
- `specs/services/admin-service/idempotency.md` § 1.3 "Redis Storage
  Layout" — canonical format this fix aligns code to.
- `specs/services/master-service/idempotency.md` § "Storage" and §
  "Cross-Service Idempotency Key Conventions (Intentional Divergence)" —
  the reason `master-service` is excluded from this task.
- `tasks/done/TASK-BE-293-wms-spec-drift-gateway-routes-openitems-idempotency.md`
  — origin of the intentional-divergence decision.

# Related Skills

- `.claude/skills/backend/` (per `.claude/skills/INDEX.md`)

---

# Related Contracts

- None — internal Redis key naming detail, not an HTTP/event contract.

---

# Target Service

- `admin-service` (only)

---

# Architecture

Follow `specs/services/admin-service/architecture.md` (Layered — per
`PROJECT.md` § Overrides, `admin-service` is the one WMS service that
deviates from Hexagonal). `RedisIdempotencyStore` lives in
`infra/idempotency/`, the Layered-style infrastructure package for this
service.

---

# Implementation Notes

- This is a **pure string-literal rename** — do not touch method bodies,
  the `IdempotencyStore` interface, `StoredResponse`, TTL handling, or
  lock semantics.
- No test currently asserts the literal `admin:idem:` string (verified by
  exhaustive grep of `apps/admin-service/` during investigation) — unlike
  `master-service`'s `RedisIdempotencyStoreTest`, which does pin the
  literal prefix as a constant (another reason `master-service` is
  excluded: a rename there would also require editing that test, which is
  exactly the "behavioural change" the master spec's divergence section
  says needs ADR governance, not a unilateral edit). Re-run the grep at
  implementation time before declaring AC-3 satisfied, in case a test was
  added between investigation and implementation.

---

# Edge Cases

- **In-flight requests during deploy**: a request whose idempotency
  record was written under the old `admin:idem:` prefix before deploy,
  retried after deploy, will not find it under the new
  `admin:idempotency:` prefix and will re-execute as a "first" call. This
  is an accepted, bounded, self-healing edge case — the old-prefix key
  still carries its original TTL (max 24h per `idempotency.md` § 1.3) and
  expires naturally; no migration script is needed or in scope (per
  `platform/naming-conventions.md` § Redis Keys' mandatory-TTL guarantee).
- **Lock key collision with the new prefix**: none — `admin:idempotency:`
  and `admin:idempotency:lock:` remain distinct prefixes (lock prefix is
  a superstring of the entry prefix plus `lock:`), same relationship as
  before the rename.

---

# Failure Scenarios

| # | Scenario | Expected / Mitigation |
|---|---|---|
| 1 | `master-service` accidentally touched | AC-4 (byte-unchanged diff) catches it |
| 2 | Only `ENTRY_PREFIX` renamed, `LOCK_PREFIX` or Javadoc left stale | AC-2/AC-3 grep catches it |
| 3 | A hidden test elsewhere asserts the old literal `admin:idem:` string | AC-3's exhaustive re-grep at implementation time catches it before it reaches CI |
| 4 | TTL or lock semantics accidentally changed while touching the file | AC-6 (existing test suite GREEN) catches behavioral regressions |

---

# Test Requirements

- `admin-service` Gradle `test` task: confirm GREEN as a baseline
  (pre-change) and GREEN again post-change (no new failures, no test
  content change required since none pins the literal prefix).
- `grep -R "admin:idem:"` (excluding the corrected `admin:idempotency:`
  superstring) across `apps/admin-service/` → 0 hits, run pre- and
  post-change to confirm the rename is complete and exhaustive.
- `git diff --numstat origin/main -- apps/master-service apps/inbound-service apps/outbound-service apps/inventory-service`
  → empty, confirming no cross-service leakage.

---

# Definition of Done

- [ ] Implementation completed (`admin-service` `RedisIdempotencyStore.java`
      prefixes + Javadoc renamed)
- [ ] Tests confirmed passing (baseline + post-change, `admin-service`
      Gradle `test` GREEN)
- [ ] Contracts updated if needed — N/A, no contract change
- [ ] Specs updated first if required — N/A, `admin-service` spec already
      documents the target format; code is being brought into alignment,
      not the spec
- [ ] `master-service` confirmed byte-unchanged
- [ ] Ready for review
