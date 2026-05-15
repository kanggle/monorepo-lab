# Task ID

TASK-BE-295

# Title

GAP auth-service MID GO sweep (Signals 1+2+3) — hoist tenant-aware methods onto `TokenBlacklist` / `LoginAttemptCounter` ports (remove `instanceof Redis*` casts) + extract duplicated `resolveTenantType` + collapse duplicated blacklist helper. Behavior-neutral, review-gated.

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

Close the GAP auth-service "MID GO" retrofit-era refactor backlog (the
`project_refactor_sweep_status.md` 잔여 우선순위 1, sibling to the DONE
`TASK-BE-288` admin-service sweep). The BE-229 multi-tenancy retrofit added
tenant-aware methods to the **concrete Redis adapters** but never extended the
**port interfaces**, so application/use-case code does
`if (port instanceof RedisXxx tenantAware) tenantAware.method(tenantId, …)`
casts — a layer violation explicitly forbidden by
`specs/services/auth-service/architecture.md` Forbidden Dependencies
("application에서 JPA 엔터티·Redis 키 직접 사용 — 반드시 domain의 포트
인터페이스 경유").

After this task: the tenant-aware methods are on the port **interfaces**, the
6 `instanceof Redis*` cast sites are gone, the duplicated `resolveTenantType`
private method (2 copies) is a single shared helper, and the byte-identical
`blacklist` write helper duplicated across `LogoutUseCase` /
`RefreshTokenUseCase` collapses into a direct port call. **Strictly
behavior-neutral** — runtime behavior, Redis key shapes, and multi-tenant
`tenant_id` resolution are byte-identical pre/post (BE-288 review-gated
precedent: silent multi-tenant persisted-state change is caught only in
review, not by CI green).

Project-internal — all paths under
`projects/global-account-platform/apps/auth-service/` (CLAUDE.md → that
project's `tasks/`).

---

# Scope

> **Reality-corrected from the 2026-05-15 dry-run** (memory was stale on
> count): the signal is **2 ports / 6 instanceof sites**, NOT "3 ports".
> There is no third `instanceof` port (`PasswordResetAttemptCounter` is not
> tenant-aware and never cast). The task is sized to verified current code on
> `main` (`d3c54205`).

## In Scope

**WI-1 — hoist tenant-aware methods onto the 2 port interfaces (eliminates 6 `instanceof Redis*` casts).**

- `domain/repository/TokenBlacklist.java`: add
  `void blacklist(String tenantId, String jti, long ttlSeconds)` and
  `boolean isBlacklisted(String tenantId, String jti)` to the interface.
  `infrastructure/redis/RedisTokenBlacklist` already implements these
  (currently non-`@Override` public methods) → add `@Override`. Keep the
  existing legacy 1-arg/2-arg interface methods as **`@Deprecated` default
  (or retained) methods** delegating to the `"fan-platform"`
  (= `TenantContext.DEFAULT_TENANT_ID`) fallback — they are the documented
  BE-229 default-tenant fallback AND the legacy-key read path; do not remove
  (minimizes blast radius, preserves the legacy `refresh:blacklist:{jti}`
  read-only fallback).
- `domain/repository/LoginAttemptCounter.java`: add 2-arg
  `getFailureCount/incrementFailureCount/resetFailureCount(String tenantId,
  String emailHash)`. `infrastructure/redis/RedisLoginAttemptCounter` already
  implements → add `@Override`. Keep legacy 1-arg interface methods
  `@Deprecated`-delegating to `"fan-platform"`.
- Delete the `instanceof` helper methods and call the port interface directly
  with the **same `tenantId` value the code already computes today**:
  - `RefreshTokenUseCase` `isBlacklisted` helper (≈L185-190) + `blacklist`
    helper (≈L195-201).
  - `LogoutUseCase` `blacklist` helper (≈L110-116).
  - `LoginUseCase` `getFailureCount`/`resetFailureCount`/`incrementFailureCount`
    helpers (≈L201-228).
- Remove the now-unused `import …infrastructure.redis.RedisTokenBlacklist`
  (LogoutUseCase, RefreshTokenUseCase) and
  `import …infrastructure.redis.RedisLoginAttemptCounter` (LoginUseCase).

**WI-2 — extract duplicated `resolveTenantType`.**
`LoginUseCase` (≈L189-196) and `RefreshTokenUseCase` (≈L174-179) hold an
identical private `resolveTenantType(tenantId)` (`"fan-platform"` →
`"B2C_CONSUMER"`, else `"B2B_ENTERPRISE"`). Extract to a single shared
helper. Natural home = `domain/tenant/TenantContext` (already owns the
literals `DEFAULT_TENANT_ID="fan-platform"` / `DEFAULT_TENANT_TYPE=
"B2C_CONSUMER"`) as a static mapping method. **Preserve the
`// TODO: fetch from account-service` intent** on the extracted method (this
is a known-temporary stub — do not silently "finalize" the mapping). Delete
both private copies.

**WI-3 — collapse the duplicated `blacklist` write helper.**
WI-1 makes the `LogoutUseCase` / `RefreshTokenUseCase` `blacklist(tenantId,
jti, ttl)` helper a one-line port call → remove the helper, inline the direct
port call (the ~100%-identical duplication disappears as a side effect of
WI-1; no separate abstraction needed).

**WI-4 — rewrite the affected use-case unit tests (mandatory, review-gated).**
`LoginUseCaseTest`, `RefreshTokenUseCaseTest`, `LogoutUseCaseTest` currently
mock the **port interface**, so `instanceof Redis*` is always false and they
assert the **legacy 1-arg/2-arg** signatures
(`verify(tokenBlacklist).blacklist(eq(JTI), anyLong())`,
`verify(loginAttemptCounter).resetFailureCount(anyString())`, etc.). After
WI-1 the use-cases call the **tenant-aware** signatures → rewrite the mocks
and verifications to the tenant-aware methods, asserting **the exact same
`tenant_id` value flows** as the production runtime path does today
(byte-identical multi-tenant resolution). This is the BE-288-class
review-gated assertion.

## Out of Scope

- **Signal 4 — `SocialIdentity` JPA leak** (`OAuthLoginUseCase` /
  `OAuthLoginTransactionalStep` directly use `SocialIdentityJpaEntity` /
  `SocialIdentityJpaRepository`; no domain port). This is a genuine
  new-abstraction introduction (new port + domain model + adapter + JPA
  dirty-checking-inside-`@Transactional` preservation + OAuth use-case + OAuth
  test changes) with real behavior-preservation subtlety. **Deferred to a
  follow-up `TASK-BE-296` candidate** — mirrors the BE-288 → BE-289 split
  (behavior-neutral mechanical sweep first, riskier abstraction as a separate
  deliberate task). Record in INDEX as the named follow-up.
- `infrastructure.oauth.*` / `OAuthUserInfo` imports in the OAuth use-cases
  (3 leaks) — established pattern, larger porting effort; explicitly **not**
  this sweep (documented residual, not an "incomplete sweep" finding).
- Any Redis key string change, `buildKey`/`buildLegacyKey` rename, or
  legacy-fallback read-path change (CRITICAL — see Edge Cases).
- Any `specs/contracts/` (HTTP/event envelope) change — grep confirms 0
  references to these ports; "contract change" here = internal Java
  port-interface only.
- The pre-existing `architecture.md` Identity-table `Data store: PostgreSQL`
  vs `dependencies.md`/JPA/`redis-keys.md` `MySQL` drift — **not caused by and
  not in scope for this sweep**; noted so the reviewer does not attribute it
  to the sweep (separate spec-drift ticket candidate).

---

# Acceptance Criteria

- [ ] `grep -rn "instanceof Redis" apps/auth-service/src/main` = **0**
      (all 6 cast sites removed).
- [ ] `grep -rn "import com.example.auth.infrastructure.redis.Redis" apps/auth-service/src/main/java/com/example/auth/application` = **0**.
- [ ] `TokenBlacklist` interface declares the 2 tenant-aware methods;
      `RedisTokenBlacklist` implements them with `@Override`; legacy methods
      retained `@Deprecated`. Same for `LoginAttemptCounter` (3 tenant-aware
      methods).
- [ ] Exactly **one** `resolveTenantType` definition repo-wide
      (`grep -rn "resolveTenantType" apps/auth-service/src/main` shows 1 def +
      its call sites); both private copies deleted; the
      `// TODO: fetch from account-service` intent preserved on the survivor.
- [ ] `LogoutUseCase` / `RefreshTokenUseCase` no longer contain a private
      `blacklist(...)` instanceof helper; the duplicated write helper is gone.
- [ ] **Behavior-neutral assertion**: Redis key builders unchanged —
      `RedisTokenBlacklist.buildKey`/`buildLegacyKey` and
      `RedisLoginAttemptCounter.buildKey` produce byte-identical strings
      (`refresh:blacklist:{tenant_id}:{jti}`, legacy `refresh:blacklist:{jti}`,
      `login:fail:{tenant_id}:{email_hash}`); method names unchanged
      (`redis-keys.md` cross-refs them). `git diff` shows no change to the
      key-pattern string literals or the legacy read-fallback path.
- [ ] WI-4: `LoginUseCaseTest`, `RefreshTokenUseCaseTest`, `LogoutUseCaseTest`
      updated to the tenant-aware signatures, each asserting the **same
      `tenant_id`** the production path resolves today (explicit
      `verify(...).method(eq(expectedTenantId), …)`).
- [ ] `./gradlew :apps:auth-service:test` (unit) green locally; integration
      tests rely on CI (Testcontainers blocker — BE-288 precedent: CI
      authoritative). Record CI run id in the impl/closure.
- [ ] No `specs/contracts/` change; no `redis-keys.md`/`dependencies.md`
      change required (key shapes + method names preserved).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy,
> integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`,
> the 5 trait files. Target service type per
> `specs/services/auth-service/architecture.md` Identity table → read the
> matching `platform/service-types/<type>.md` (exactly one).

- `specs/services/auth-service/architecture.md` — **the controlling spec**:
  Forbidden Dependencies line ~169 declares the exact port-boundary rule this
  sweep enforces; package layout lines ~80-127. Canonical ADR-MONO-012
  Identity-table form must be preserved if touched (recommend **no** spec edit
  — sweep only makes code comply with already-declared rule).
- `specs/services/auth-service/redis-keys.md` — key registry SoT; lines ~13/35
  define the canonical tenant-scoped keys and lines ~52-58 the legacy
  read-only fallback, naming `RedisTokenBlacklist.buildKey`/`buildLegacyKey`
  explicitly. Behavior-preservation reference (do NOT edit).
- `specs/services/auth-service/dependencies.md` — `social_identities` /
  `refresh_tokens` tables; no port/adapter class names. Reference only.
- `rules/traits/multi-tenant.md` (M1 tenant key prefix), `rules/traits/regulated.md`
  (R4 PII hashing) — the tenant-aware methods carry the `tenant_id` that these
  rules mandate; the sweep must not weaken either.

# Related Skills

- `.claude/commands/implement-task.md`, `.claude/commands/review-task.md`.
- `.claude/skills/backend/refactoring/SKILL.md` § Baseline Check + Worktree
  Dispatch Verification (sweep operational rules, PR #377).

---

# Related Contracts

- None. `grep -rn "TokenBlacklist|LoginAttemptCounter|resolveTenantType" specs/contracts/`
  = 0. No HTTP/event envelope touches these. The only "contract" changing is
  the internal Java port interface (`TokenBlacklist`, `LoginAttemptCounter`) —
  additive (new methods), legacy methods retained `@Deprecated`, so no caller
  outside auth-service is affected (these ports are auth-service-internal).

---

# Target Service

- `auth-service` (GAP / global-account-platform)

---

# Architecture

No architecture-style change. Hexagonal/layered boundary is *enforced*, not
altered — the sweep moves tenant-aware methods from concrete adapters up to
the port interfaces so the application layer stops downcasting. Preserves the
ADR-MONO-012 canonical `architecture.md` form (no spec edit).

---

# Implementation Notes

1. Read the verified code map: 2 ports = `domain/repository/TokenBlacklist.java`
   + `domain/repository/LoginAttemptCounter.java`; adapters =
   `infrastructure/redis/RedisTokenBlacklist.java` +
   `RedisLoginAttemptCounter.java` (already have the tenant-aware impls — just
   `@Override` + interface declaration). Use-cases =
   `application/{LoginUseCase,RefreshTokenUseCase,LogoutUseCase}.java`. Common
   home = `domain/tenant/TenantContext.java`.
2. **Behavior-neutral discipline (BE-288 precedent)**: the `tenantId` passed
   to the new port method must be *exactly* the value the
   `instanceof`-true branch passes today (the use-cases already compute
   `tenantIdForRateLimit` / `token.getTenantId()` / `?: DEFAULT_TENANT_ID`
   before the helper — call the port with that same value, change nothing
   about its derivation). The `instanceof`-false branch is unreachable in
   production (the injected bean is always the `@Component Redis*` adapter);
   removing it changes no runtime path — but it IS the branch unit tests hit,
   hence WI-4.
3. Keep legacy interface methods (`@Deprecated`, delegating to
   `TenantContext.DEFAULT_TENANT_ID`). They preserve the BE-229 default-tenant
   fallback and the legacy `refresh:blacklist:{jti}` read path. Mirrors the
   existing `CredentialRepository.findByAccountIdEmail` `@Deprecated`
   precedent.
4. `resolveTenantType` → `TenantContext` static; carry the
   `// TODO: fetch from account-service` comment over verbatim.
5. Tests: rewrite the 3 use-case unit tests to mock the tenant-aware interface
   and `verify` with `eq(expectedTenantId)`. Assert the resolved tenant id is
   identical to today's (e.g. for a fan-platform login it is `"fan-platform"`;
   for a resolved B2B tenant it is that tenant id — derive the expected value
   from the same inputs the production path uses).
6. Run `./gradlew :apps:auth-service:test` (unit, no Testcontainers). For
   integration tests defer to CI (Testcontainers Docker blocker —
   `project_testcontainers_docker_desktop_blocker`; BE-288 precedent: CI
   authoritative, record run id).
7. Branch (no `master` substring): `task/be-295-auth-port-tenant-hoist`.
   lifecycle ready → in-progress → review → done.

---

# Edge Cases

- **Redis key shapes (CRITICAL):** the sweep must not alter
  `refresh:blacklist:{tenant_id}:{jti}`, legacy `refresh:blacklist:{jti}`
  (read-only), `login:fail:{tenant_id}:{email_hash}`, nor the
  `buildKey`/`buildLegacyKey` method names (`redis-keys.md` cross-references
  them by name). The port hoist only changes *who declares the method*, not
  the key construction. Assert byte-identical.
- **Legacy fallback path:** `isBlacklisted(tenantId,jti)` in
  `RedisTokenBlacklist` checks both the tenant key and the legacy key — this
  read-path must remain intact (it is spec'd in `redis-keys.md` L52-58). Do
  not "simplify" it away while hoisting.
- **Default-tenant equivalence:** the legacy 1-arg path resolves to
  `"fan-platform"`. Use-cases already guard with `?: DEFAULT_TENANT_ID`, so
  calling the tenant-aware method with that guarded value is behaviorally
  identical to today's instanceof-true path. Confirm no use-case depends on
  the instanceof-false branch at runtime (it cannot — single `@Component`
  adapter).
- **`resolveTenantType` home in `domain/`:** `TenantContext` is a domain value
  object; adding a static pure mapping method is acceptable (no framework
  dependency, mirrors its existing literal ownership). Do not convert it to a
  service or fetch from account-service in this task (preserve the TODO).
- **Unit-test mock split:** before the sweep, `instanceof Redis*` is always
  false under Mockito mocks → tests assert legacy signatures. This is why WI-4
  is mandatory and why review (not CI) is the gate.

# Failure Scenarios

- The sweep changes the `tenantId` derivation (e.g. drops the
  `?: DEFAULT_TENANT_ID` guard, or passes a different scope) → silent
  multi-tenant persisted-state / rate-limit-bucket change in a
  regulated+audit-heavy service; CI stays green (unit tests mock the port);
  caught only by the WI-4 byte-identical tenant-id assertion + review. (Exact
  BE-288 Finding-1 class.)
- A Redis key string or `buildKey`/`buildLegacyKey` name is altered → spec
  `redis-keys.md` cross-ref drift + a live key-shape change (lockout/blacklist
  miss). Must assert byte-identical.
- The legacy interface methods are hard-removed instead of retained
  `@Deprecated` → the BE-229 default-tenant fallback + legacy-key read path
  break; large unrelated test churn. Keep them.
- Signal 4 (SocialIdentity) is pulled into this task → scope creep into a
  txn-dirty-check-sensitive new abstraction; the behavior-neutral guarantee
  is no longer cleanly assertable. Keep it deferred (TASK-BE-296).
- `instanceof` removed but the port interface method not actually added (only
  on concrete) → compile failure / NoSuchMethod; ensure interface declaration
  + `@Override` land together.

---

# Test Requirements

- WI-4 unit tests: `LoginUseCaseTest`, `RefreshTokenUseCaseTest`,
  `LogoutUseCaseTest` rewritten to tenant-aware port signatures, each with an
  explicit `verify(port).method(eq(<expected tenant_id>), …)` proving the
  resolved tenant id equals today's production-path value (behavior-neutral
  proof).
- `./gradlew :apps:auth-service:test` green locally (unit only — no
  Testcontainers).
- Integration tests: CI authoritative (Testcontainers Docker blocker).
  Record CI run id in impl PR + closure. Expect auth-service IT (incl.
  global-account-platform Testcontainers) green — the change is internal port
  hoist, no key/SQL/contract change.
- Behavior-preservation verification: `git diff` on
  `RedisTokenBlacklist`/`RedisLoginAttemptCounter` shows only
  `@Override`/interface-conformance additions, **zero** change to key string
  literals or the legacy read path.

---

# Definition of Done

- [ ] WI-1: tenant-aware methods on both port interfaces; 6 `instanceof Redis*`
      sites removed; redis infra imports out of `application/`; legacy methods
      retained `@Deprecated`
- [ ] WI-2: single `resolveTenantType` (TODO intent preserved); 2 copies
      deleted
- [ ] WI-3: duplicated `blacklist` write helper collapsed (no instanceof
      helper remains)
- [ ] WI-4: 3 use-case unit tests rewritten, byte-identical tenant-id
      asserted
- [ ] Redis key strings + `buildKey`/`buildLegacyKey` names + legacy
      read-fallback byte-identical (no spec edit needed)
- [ ] `:apps:auth-service:test` unit green; CI run id recorded; IT green on CI
- [ ] Signal 4 deferred as TASK-BE-296 candidate (recorded in GAP INDEX)
- [ ] Branch `task/be-295-auth-port-tenant-hoist`; lifecycle ready → review →
      done; review-gated (multi-tenant byte-identical, BE-288 precedent)
- [ ] Ready for review
