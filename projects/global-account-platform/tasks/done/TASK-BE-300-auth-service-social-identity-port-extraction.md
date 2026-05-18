# Task ID

TASK-BE-300

# Title

auth-service SocialIdentity domain-port extraction (behavior-neutral application→infrastructure leak removal — Signal 4)

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

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

- **depends on**: nothing (self-contained GAP auth-service internal refactor).
- **follows pattern**: `TASK-BE-295` (auth-service port tenant-aware hoist) + `TASK-BE-288`→`289` (admin-service operator/totp port extraction) — same "behavior-neutral port hoist, adapter = delegation only, byte-identical assertions" discipline. Precedent template = `RefreshTokenRepository` (domain port) + `RefreshTokenJpaEntity.toDomain()/fromDomain()` + `RefreshTokenRepositoryAdapter`.
- **prerequisite for**: nothing (clears the last named GAP auth-service refactor backlog item, "Signal 4" from `project_refactor_sweep_status`).
- **spec-first**: **no spec change** — `specs/services/auth-service/architecture.md` L169 already declares the Forbidden Dependency this enforces ("❌ `application`에서 JPA 엔터티 직접 사용 — 반드시 `domain`의 포트 인터페이스 경유"); L94-96 already documents the `domain/repository/` port location. The rule pre-exists; this task makes the code comply (identical posture to TASK-BE-295 "spec 무편집, 룰 기선언"). No contract/schema/event change.

---

# Goal

Remove the **Signal 4** layering violation: `application/OAuthLoginUseCase` and `application/OAuthLoginTransactionalStep` directly `import com.example.auth.infrastructure.persistence.SocialIdentityJpaEntity` / `SocialIdentityJpaRepository`, violating `architecture.md` § Forbidden Dependencies L169 (`application` → JPA entity direct use). `social_identities` is the only auth-service aggregate with **no domain port** (Credential, RefreshToken, DeviceSession, … all have `domain/repository/*` ports + mapping adapters).

Extract a `SocialIdentity` domain model + `SocialIdentityRepository` domain port + `SocialIdentityRepositoryAdapter`, exactly mirroring the established `RefreshToken` precedent, so that:

- `application/` no longer imports `infrastructure.persistence.*`; it depends only on `domain.social.SocialIdentity` + `domain.repository.SocialIdentityRepository`.
- The OAuth callback flow's observable behavior is **byte-identical** — same DB row outcomes (existing-identity update vs new-identity insert), same `lastUsedAt`/`providerEmail` mutation semantics, same `tenantId` defaulting, same transactional boundary, same returned `OAuthLoginResult`, same events. No HTTP/event/contract/schema change.

This is a pure internal-layering refactor. The `social_identities` table, columns, indexes, and net post-transaction DB state are unchanged.

# Scope

## In Scope

### New (domain + adapter — mirror `RefreshToken` precedent exactly)

- `domain/social/SocialIdentity.java` — POJO domain model (no Spring/JPA imports). Fields: `Long id` (nullable — persistence identity, null = unsaved), `accountId`, `tenantId`, `provider`, `providerUserId`, `providerEmail`, `connectedAt`, `lastUsedAt`. All-args constructor (used by adapter `toDomain`). `static SocialIdentity create(accountId, tenantId, provider, providerUserId, providerEmail)` — **byte-identical** to `SocialIdentityJpaEntity.create(5-arg)`: `tenantId == null → "fan-platform"`, `connectedAt = lastUsedAt = Instant.now()`, `id = null`. Mutators `updateLastUsedAt()` (`lastUsedAt = Instant.now()`) and `updateProviderEmail(String)` (`providerEmail = email`) — semantics identical to the entity's same-named methods. Getters.
- `domain/repository/SocialIdentityRepository.java` — port interface, **only the two methods the application uses**: `Optional<SocialIdentity> findByProviderAndProviderUserId(String provider, String providerUserId)` and `SocialIdentity save(SocialIdentity)`. (`SocialIdentityJpaRepository.findByAccountId` is **not** application-used → not hoisted to the port; YAGNI, matches RefreshTokenRepository exposing only used methods.)
- `infrastructure/persistence/SocialIdentityRepositoryAdapter.java` — `@Repository @RequiredArgsConstructor implements SocialIdentityRepository`, delegates to `SocialIdentityJpaRepository`, maps via `SocialIdentityJpaEntity::toDomain` / `fromDomain`. Pure delegation + mapping (shape-identical to `RefreshTokenRepositoryAdapter`).

### Modified

- `infrastructure/persistence/SocialIdentityJpaEntity.java` — **add** `SocialIdentity toDomain()` + `static SocialIdentityJpaEntity fromDomain(SocialIdentity)` (mirror `RefreshTokenJpaEntity`). `fromDomain` maps **all** fields including `id` (null → INSERT, non-null → JPA merge → UPDATE) so the existing-vs-new persistence branch and all unmutated columns (`connectedAt`/`accountId`/`tenantId`/`provider`/`providerUserId`) round-trip unchanged. Existing `create(...)` factories + `updateLastUsedAt`/`updateProviderEmail` left as-is (out of scope; harmless; now app-unused — recorded as a non-blocking observation, not removed — BE-295 "@Deprecated 보존" discipline).
- `application/OAuthLoginUseCase.java` — swap `SocialIdentityJpaRepository`+`SocialIdentityJpaEntity` import/field → `SocialIdentityRepository`+`SocialIdentity`. `findByProviderAndProviderUserId(...)` now returns `Optional<SocialIdentity>`; `.getAccountId()` read unchanged. Orchestration logic byte-identical.
- `application/OAuthLoginTransactionalStep.java` — same import/field swap. Existing-identity path: `identity.updateLastUsedAt()` + conditional `identity.updateProviderEmail(email)` (domain mutators, identical semantics) + `socialIdentityRepository.save(identity)`. New-identity path: `SocialIdentity.create(accountId, tenantContext.tenantId(), provider.name(), userInfo.providerUserId(), userInfo.email())` + `save(...)`. `@Transactional` boundary, conditional email-diff logic, and all other statements unchanged.
- `application/command/OAuthCallbackTxnCommand.java` — javadoc-only: `{@code SocialIdentityJpaEntity}` → `{@code SocialIdentity}` (prose consistency; no compile dependency — it never imported the JPA type).

### Tests

- Rewrite `OAuthLoginUseCaseTest.java` + `OAuthLoginTransactionalStepTest.java` mocks `SocialIdentityJpaRepository`/`SocialIdentityJpaEntity` → `SocialIdentityRepository`/`SocialIdentity`, with **byte-identical assertions** (verify the same `accountId`, `providerEmail`, `tenantId`, `provider`, `providerUserId`, new-vs-existing branch, and returned `OAuthLoginResult` — prove production-resolved values unchanged; BE-295 discipline guarding against mock-shape masking a behavior change).
- `infrastructure/persistence/SocialIdentityJpaRepositoryTest.java` — **unchanged** (`@DataJpaTest` on the Spring Data repo + entity; `toDomain`/`fromDomain` are purely additive, the repo/entity columns are structurally unchanged).

## Out of Scope

- **`infrastructure.oauth.*` leak** (`OAuthUserInfo`, `OAuthClient`, `OAuthClientFactory`, `OAuthProperties`, `OAuthProviderException` imported by `OAuthLoginUseCase`/`OAuthLoginTransactionalStep`/`OAuthCallbackTxnCommand`) — a **separate, pre-existing** pattern explicitly recorded out-of-scope in `project_refactor_sweep_status` ("(b) `infrastructure.oauth.*` import 3건 = 기존 패턴, scope 외"). Not touched here; noted as a non-blocking observation / future candidate. Mixing it in would break the "Signal 4 = SocialIdentity port" single-concern boundary.
- Removing/deprecating `SocialIdentityJpaEntity.create(...)` factories or its mutators — out of scope (BE-295 left legacy methods; their removal, if ever, is a separate deliberate AC à la BE-289).
- Any `social_identities` schema / data-model.md / contract / event change — none; this is internal layering only.
- `architecture.md` edit — the Forbidden Dependency (L169) + port location (L94-96) are already declared; no spec change (BE-295 precedent).
- account-service / admin-service / other GAP services — untouched.

# Acceptance Criteria

- **AC-1 (leak closed)**: `grep -rn "infrastructure\.persistence" auth-service/src/main/java/com/example/auth/application/` → **0** for `SocialIdentity*` (and no `import ...infrastructure.persistence.SocialIdentityJpa*` anywhere under `application/`). `application/` depends only on `domain.social.SocialIdentity` + `domain.repository.SocialIdentityRepository`.
- **AC-2 (behavior-neutral — the core gate)**: observable behavior byte-identical. (a) existing-identity path: `lastUsedAt` set to call-time `Instant.now()`, `providerEmail` updated **iff** `email != null && !email.equals(currentProviderEmail)`, all other columns (`id`/`accountId`/`tenantId`/`provider`/`providerUserId`/`connectedAt`) unchanged → row UPDATE. (b) new-identity path: INSERT with `tenantId` defaulting `null→"fan-platform"`, `connectedAt == lastUsedAt == Instant.now()`. (c) returned `OAuthLoginResult` (tokens/ttl/isNewAccount), published events, `@Transactional` boundary, and account-status guard all unchanged. Net post-commit DB state identical (SQL *shape* may differ — JPA merge SELECT+UPDATE vs managed dirty-flush — explicitly accepted per BE-295: the gain is layer isolation, not byte-identical SQL).
- **AC-3 (adapter = delegation only)**: `SocialIdentityRepositoryAdapter` git diff is structurally identical to `RefreshTokenRepositoryAdapter` (delegate + `toDomain`/`fromDomain` map); contains no business logic. `SocialIdentity.create`/mutators are line-for-line semantic mirrors of the entity's.
- **AC-4 (precedent conformance)**: new files placed/named per the established pattern — `domain/social/SocialIdentity`, `domain/repository/SocialIdentityRepository`, `infrastructure/persistence/SocialIdentityRepositoryAdapter`; `SocialIdentity` is a pure POJO (no `jakarta.persistence` / `org.springframework` import — architecture.md L166-167).
- **AC-5 (tests prove neutrality)**: `OAuthLoginUseCaseTest` + `OAuthLoginTransactionalStepTest` rewritten asserting the **same** field values as before (byte-identical `accountId`/`email`/`tenantId`/branch); `SocialIdentityJpaRepositoryTest` unchanged and still green. `:projects:global-account-platform:apps:auth-service:test` BUILD SUCCESSFUL, 0 regression vs the pre-change baseline.
- **AC-6 (CI authoritative)**: PR CI `Build & Test (JDK 21)` + `Integration (global-account-platform, Testcontainers)` green (Testcontainers runs the real `social_identities` upsert end-to-end — the authoritative behavior-neutral proof; local Docker may be unavailable, CI is authoritative per `project_testcontainers_docker_desktop_blocker`).

# Related Specs

- [specs/services/auth-service/architecture.md](../../specs/services/auth-service/architecture.md) § Allowed/Forbidden Dependencies (L149-173) + § Internal Structure Rule (L57-96) — the rule this enforces; **not edited** (pre-declared).
- `project_refactor_sweep_status` (memory) — Signal 4 origin + the explicit `infrastructure.oauth.*` out-of-scope note + the BE-288→289 / BE-295 behavior-neutral precedent.

# Related Contracts

- None. `social_identities` persistence is internal; OAuth HTTP contracts (`contracts/http/auth-api.md` OAuth endpoints) and emitted events are **unchanged** (no request/response/event shape touched). The refactor is invisible across every service boundary.

# Edge Cases

- **Existing-identity merge round-trip**: `SocialIdentity` must carry `id` + `connectedAt` (and all unmutated fields) verbatim from `findByProviderAndProviderUserId` so `fromDomain` → `jpaRepository.save()` issues a merge that does not null/clobber unmutated columns. (This is the "txn-dirty-check-sensitive" risk the backlog flagged — addressed by full-fidelity field round-trip, not by relying on a managed-entity dirty flush.)
- **Conditional email update**: the `email != null && !email.equals(currentProviderEmail)` decision stays in `OAuthLoginTransactionalStep` (application orchestration), reading `providerEmail` from the domain model — logic location and predicate unchanged.
- **`tenantId` null default**: domain `create` must reproduce `tenantId == null ? "fan-platform"` exactly (entity L49 parity); `OAuthLoginTransactionalStep` passes `TenantContext.defaultContext().tenantId()` (non-null in practice) — defensive branch preserved.
- **Non-txn pre-read vs txn upsert**: `OAuthLoginUseCase.callback` does a non-`@Transactional` `findByProviderAndProviderUserId` (TOCTOU note in its javadoc); `OAuthLoginTransactionalStep` re-reads + upserts inside the txn. Both now go through the port; the DB unique key `(tenant_id, provider, provider_user_id)` still guards concurrent inserts — behavior unchanged.
- **`findByAccountId`**: remains on `SocialIdentityJpaRepository` (Spring Data) for any infra/test use; deliberately **not** added to the domain port (no application caller).

# Failure Scenarios

- **Mapping omits a field** → merge nulls/overwrites an unmutated column (e.g. `connectedAt`) → data regression. Mitigation: AC-2/AC-3 require full-fidelity `toDomain`/`fromDomain` (every column, incl. `id`); Testcontainers IT (AC-6) exercises the real existing-identity update path and asserts unchanged columns.
- **`id` not round-tripped** → existing-identity path INSERTs a duplicate instead of UPDATE (or unique-key violation). Mitigation: `SocialIdentity` carries nullable `id`; `fromDomain` sets it; AC-2(a) + IT explicitly cover the update branch.
- **Mock-shape masks a behavior change in unit tests** (BE-288 Finding-1 class hazard) → green tests, broken production. Mitigation: AC-5 mandates byte-identical value assertions (same `accountId`/`email`/`tenantId`/branch), not just "port called"; CI Testcontainers IT is the authoritative real-DB gate.
- **Scope creep into `infrastructure.oauth.*`** → unrelated risk surface, breaks single-concern review. Mitigation: explicitly Out of Scope; a follow-up observation only.
- **Domain model accidentally imports Spring/JPA** → re-introduces a (reversed) layering violation (architecture.md L166-167). Mitigation: AC-4 grep gate on `domain/social/SocialIdentity.java`.

# Verification

1. `grep -rn "infrastructure.persistence.SocialIdentityJpa" projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/application/` → 0 (AC-1).
2. `grep -n "jakarta.persistence\|org.springframework" .../domain/social/SocialIdentity.java` → 0 (AC-4).
3. `SocialIdentityRepositoryAdapter` vs `RefreshTokenRepositoryAdapter` — same structural shape (delegate + map), no logic (AC-3).
4. `./gradlew :projects:global-account-platform:apps:auth-service:test` BUILD SUCCESSFUL; `OAuthLoginUseCaseTest`/`OAuthLoginTransactionalStepTest` rewritten with byte-identical value assertions; `SocialIdentityJpaRepositoryTest` unchanged & green; 0 regression vs baseline (AC-5).
5. PR CI: `Build & Test (JDK 21)` + `Integration (global-account-platform, Testcontainers)` green — real `social_identities` upsert (new + existing) end-to-end behavior-neutral proof (AC-2/AC-6).
6. Adapter/domain diff review: `SocialIdentity.create`/`updateLastUsedAt`/`updateProviderEmail` are line-for-line semantic mirrors of `SocialIdentityJpaEntity`'s; `fromDomain` round-trips every column incl. `id` (AC-2/AC-3).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (txn-dirty-check-sensitive behavior-neutral port extraction across a `@Transactional` boundary + byte-identical-assertion test rewrite — judgement-bearing, BE-295/288→289 class) / 리뷰=Opus 4.7 (inline self-review, review-checklist 6/6, behavior-neutral + adapter-delegation-only direct verification).
