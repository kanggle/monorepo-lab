# Task ID

TASK-BE-294

# Title

GAP auth-service `refresh:blacklist` Redis key shape drift — `redis-keys.md` registry + 3 narrative specs stale `{jti}` vs canonical tenant-scoped `{tenant_id}:{jti}` (BE-290 deferred backlog (b))

# Status

done

# Owner

backend

# Task Tags

- refactor

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

# Goal

Close the `refresh:blacklist` Redis key multi-tenant shape drift that
TASK-BE-290 explicitly carved out of scope and recorded as deferred backlog
item (b) (`projects/global-account-platform/tasks/INDEX.md` § done →
TASK-BE-290: "`refresh:blacklist` 키 동일 multi-tenant drift … BE-290
scope(login:fail/G7) 외, 향후 task 후보").

After this task: the `refresh:blacklist` Redis key shape is identical across
**all** GAP auth-service specs and matches both the live code and the HTTP
contract Side Effects — a single canonical `refresh:blacklist:{tenant_id}:{jti}`
form in the key-registry SoT, with the pre-TASK-BE-229 legacy `{jti}` form
documented as a read-only backward-compat fallback (so the spec stops drifting
away from `RedisTokenBlacklist`).

Project-internal — all paths under `projects/global-account-platform/specs/`
(CLAUDE.md → that project's `tasks/`).

---

# Scope

## In Scope

**WI-1 — `refresh:blacklist` key shape drift (spec-only).**

The canonical tenant-scoped form `refresh:blacklist:{tenant_id}:{jti}` is
**triply confirmed** and is NOT in dispute (no decision gate — unlike BE-290's
G7 which had a 4-spec-vs-rule tension):

1. `specs/services/auth-service/architecture.md:207` already states
   `refresh:blacklist:{tenant_id}:{jti}` (BE-290 final state).
2. `specs/contracts/http/auth-api.md:342` "Side Effects":
   `refresh:blacklist:{tenant_id}:{jti}` Redis SET.
3. Live code `apps/auth-service/.../infrastructure/redis/RedisTokenBlacklist.java`
   — `buildKey(tenantId, jti)` writes `refresh:blacklist:{tenantId}:{jti}`
   (TASK-BE-229, the same originating task that tenant-scoped `login:fail`);
   `buildLegacyKey(jti)` is **read-only** on the legacy `refresh:blacklist:{jti}`
   for backward compatibility with tokens minted before BE-229.

The stale set still carrying the un-scoped `refresh:blacklist:{jti}` form:

- `specs/services/auth-service/redis-keys.md:35` (key pattern) and `:37`
  (example) — **the key-registry SoT among specs**; primary edit.
- `specs/services/auth-service/dependencies.md:30`.
- `specs/services/auth-service/overview.md:34`.
- `specs/features/authentication.md:40`.

Resolve by making `redis-keys.md` (registry) canonical first
(`refresh:blacklist:{tenant_id}:{jti}` pattern + tenant-prefixed example +
a tenant-segment rationale paragraph mirroring the `login:fail` registry
rationale BE-290 added L22–31, **plus** an explicit note that the legacy
`refresh:blacklist:{jti}` form is still **read** for backward compatibility —
matching `RedisTokenBlacklist.buildLegacyKey` so the spec and code agree),
then propagate the `{tenant_id}:{jti}` write form to the 3 narrative specs.

## Out of Scope

- `architecture.md:207` and `contracts/http/auth-api.md:342` — already
  canonical (`{tenant_id}:{jti}`); they are the reference anchors, not edited.
- `login:fail` key (G7) — already closed by TASK-BE-290.
- Any production code, migration, or test change. The code-state gate (AC)
  confirms `RedisTokenBlacklist` already writes the tenant-scoped form and
  only *reads* the legacy form for backward compat — there is **no live
  un-scoped write** to remediate. Spec-only is sufficient *and* the registry
  must document the legacy read-fallback so the spec matches code reality
  (otherwise a "spec says only tenant-scoped, code still reads legacy" drift
  is silently introduced).
- `DLQ_RETRY_EXHAUSTED` / any unrelated Redis key / any HTTP-or-event
  envelope change.

---

# Acceptance Criteria

- [ ] `grep -rn "refresh:blacklist" specs/` shows exactly one **write** key
      shape (`refresh:blacklist:{tenant_id}:{jti}`) across `redis-keys.md`,
      `dependencies.md`, `overview.md`, `features/authentication.md`, and the
      already-correct `architecture.md` + `contracts/http/auth-api.md` anchors.
      The only remaining `refresh:blacklist:{jti}` occurrence is the explicit
      **legacy read-fallback** note in `redis-keys.md` (clearly labelled as
      pre-BE-229 backward-compat read-only, not a write pattern).
- [ ] `redis-keys.md` Refresh Token Blacklist entry: pattern
      `refresh:blacklist:{tenant_id}:{jti}`, example tenant-prefixed (e.g.
      `refresh:blacklist:fan-platform:550e8400-…`), a `{tenant_id}`-segment
      rationale paragraph (multi-tenant isolation, `rules/traits/multi-tenant.md`
      M1), and a legacy-read-fallback note consistent with
      `RedisTokenBlacklist.buildLegacyKey`.
- [ ] code-state gate recorded in the impl commit: `RedisTokenBlacklist`
      already writes `refresh:blacklist:{tenant_id}:{jti}` (TASK-BE-229) and
      reads legacy `{jti}` for backward compat → spec-only closure is valid;
      **no code follow-up** is filed (there is no live un-scoped write
      exposure — contrast BE-290's gate, which had to confirm hashing).
- [ ] Naming Convention section remains accurate (BE-290's tenant-isolation
      bullet already covers `refresh:blacklist`; extend its example to cite
      `refresh:blacklist:{tenant_id}:…` alongside `login:fail:{tenant_id}:…`
      only if it improves clarity — no semantic change).
- [ ] No new broken links / orphans introduced (re-run `validate-rules`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy,
> integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`,
> the 5 trait files. This is a multi-tenant key-isolation (M1) consistency fix;
> not PII-in-key (jti is an opaque UUID, not regulated data).

- `specs/services/auth-service/redis-keys.md` — **key registry, WI-1 SoT
  among specs** (L24–42 Refresh Token Blacklist entry; primary edit).
- `specs/services/auth-service/architecture.md:207` — already-canonical
  reference anchor (`refresh:blacklist:{tenant_id}:{jti}`), not edited.
- `specs/services/auth-service/dependencies.md:30`,
  `specs/services/auth-service/overview.md:34`,
  `specs/features/authentication.md:40` — WI-1 stale propagation targets.
- `rules/traits/multi-tenant.md` — M1 (Redis key `<tenant_id>:…` prefix
  mandate) is the controlling rule; canonical is already established, this
  task only propagates it (no decision gate).

# Related Skills

- `.claude/commands/refactor-spec.md` — primary (spec drift reconciliation).
- `.claude/commands/validate-rules.md` — post-check.

> Note: TASK-BE-290's Related Skills cited `.claude/skills/{…}/SKILL.md`; the
> actual on-disk location is `.claude/commands/*.md` (recorded as advisory in
> the BE-290 review). Corrected here.

---

# Related Contracts

- `specs/contracts/http/auth-api.md:342` — "Side Effects" already states
  `refresh:blacklist:{tenant_id}:{jti}` (reference anchor confirming
  canonical); **no envelope change**.
- No event contract touched.

---

# Target Service

- `auth-service` (GAP / global-account-platform)

---

# Architecture

No architecture-style change. Spec-only registry + narrative alignment to an
already-established (code + architecture.md + contract) canonical key shape.

---

# Implementation Notes

1. Read `redis-keys.md` first; the Refresh Token Blacklist entry is the
   registry SoT. Make it canonical, then propagate to the 3 narrative specs.
2. **code-state gate (mandatory, BE-290 precedent)**: before closing as
   spec-only, confirm the caller→key-builder path. `RedisTokenBlacklist`
   `buildKey(tenantId, jti)` = write path = `refresh:blacklist:{tenantId}:{jti}`
   (already tenant-scoped, TASK-BE-229); `buildLegacyKey(jti)` = read-only
   backward-compat. There is **no live un-scoped write** — spec-only closure
   is valid; record this in the impl commit; do **not** file a code follow-up.
3. The substantive nuance vs a pure find-replace: the registry must
   **document the legacy `{jti}` read-fallback** (pre-BE-229 tokens), mirroring
   `buildLegacyKey`. Aligning the spec to "only `{tenant_id}:{jti}`" while the
   code still reads legacy `{jti}` would create a *new* spec↔code drift.
4. Mirror the form/voice of the `login:fail` registry rationale BE-290 added
   (`redis-keys.md` L22–31) — do not copy verbatim; `refresh:blacklist`'s
   tenant segment comes from the token's resolved `tenant_id` claim
   (post-auth), unlike `login:fail`'s pre-auth credential tenant.
5. "(writing) → ready" stage convention does not apply as a separate PR here
   — per `feedback_pr_bundling` / `feedback_pr_on_request` the task file +
   impl + closure land on one held branch `task/be-294-refresh-blacklist-
   tenant-scope` (based off `task/be-290-gap-spec-drift`, since this stacks
   on BE-290's `login:fail` registry edits in the same files).

---

# Edge Cases

- Legacy `refresh:blacklist:{jti}` (pre-BE-229 tokens): must be documented in
  the registry as a **read-only** backward-compat key, not removed — removing
  the mention would make the spec claim the legacy form is invalid while
  `RedisTokenBlacklist.buildLegacyKey` still reads it.
- SUPER_ADMIN / platform-scope: `refresh:blacklist` keys are derived from a
  refresh token that already carries a resolved `tenant_id` claim (post-auth),
  so `{tenant_id}` is always a concrete tenant — unlike `login:fail`'s pre-auth
  guard, the platform sentinel `'*'` does not arise here. State this explicitly
  if the registry rationale would otherwise be ambiguous (consistent with
  BE-290's `login:fail` sentinel note style).
- If `redis-keys.md` Refresh Token Blacklist entry is internally inconsistent
  (pattern vs example) after the edit, fix the registry's internal consistency
  as part of WI-1 (BE-290 Edge Case precedent).

# Failure Scenarios

- WI-1 aligns the narrative specs to `{tenant_id}:{jti}` but leaves the
  `redis-keys.md` registry stale → the SoT among specs still says `{jti}`,
  the drift is inverted not closed. The registry must be edited first.
- WI-1 strips every `refresh:blacklist:{jti}` mention including the legacy
  read-fallback → spec now claims only tenant-scoped keys exist while
  `RedisTokenBlacklist.buildLegacyKey` still reads the legacy form → a new
  spec↔code drift is introduced (the exact failure mode the code-state gate
  exists to prevent). The legacy form must survive as a documented read-only
  fallback.
- WI-1 treated as a decision-bearing key-shape choice (à la BE-290 G7) and a
  new shape is invented → the canonical is already triply-fixed (code +
  architecture.md:207 + auth-api.md:342); inventing anything other than
  `refresh:blacklist:{tenant_id}:{jti}` is a regression.

---

# Test Requirements

- Spec-only; no unit/integration test. Verification:
  - `grep -rn "refresh:blacklist" specs/` → single write shape
    `{tenant_id}:{jti}` across the 4 edited specs + 2 reference anchors; the
    only `{jti}`-form occurrence is the labelled legacy read-fallback in
    `redis-keys.md`.
  - code-state gate finding recorded (live code already tenant-scoped write +
    legacy read; no follow-up).
  - `validate-rules` clean (no new broken-link / orphan).

---

# Definition of Done

- [ ] Single `refresh:blacklist` write shape (`{tenant_id}:{jti}`) across all
      GAP auth-service specs; legacy `{jti}` documented as read-only fallback
- [ ] `redis-keys.md` registry canonical (pattern + example + rationale +
      legacy-read note), 3 narrative specs propagated
- [ ] code-state gate recorded; no code follow-up filed (no live un-scoped
      write)
- [ ] `validate-rules` no new broken-link/orphan
- [ ] Branch: `task/be-294-refresh-blacklist-tenant-scope` (off
      `task/be-290-gap-spec-drift`; substring `master` 금지)
- [ ] Ready for review
