# Task ID

TASK-BE-303

# Title

auth-service `SocialIdentityJpaEntity` app-unused factory/mutators removal — BE-289-class silent-default foot-gun cleanup (post-BE-300 dead code, last GAP sweep residual)

# Status

review

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

- **depends on**: TASK-BE-300 (DONE — extracted `domain/social/SocialIdentity` + `SocialIdentityRepository` port + `SocialIdentityJpaEntity.toDomain()/fromDomain()`; that refactor is what made the entity's `create()`/mutators production-dead).
- **origin**: TASK-BE-300 / TASK-BE-302 non-blocking observation **(b)** — the single tracked GAP sweep residual (`project_refactor_sweep_status` § BE-302 closure: "잔존 비차단 관찰 (b) 유일 … @Deprecated-보존 discipline, 소규모 잔존 후보").
- **precedent**: TASK-BE-289 — BE-288's port refactor left `AdminOperatorRoleJpaEntity.create` legacy 4-arg overload; BE-289 (deliberate follow-up) removed it to kill the silent-default foot-gun + migrated tests off it. This task is the exact same class for `SocialIdentityJpaEntity`.
- **prerequisite for**: nothing. Closes the GAP refactor-sweep + spec-drift backlog to **true 0**.
- **spec-first / spec-only**: NO. Production code + test only. No spec/contract/schema/event change (the entity's persisted shape, columns, and JPA query slice are byte-unchanged). No ADR — see § Decision authority.

---

# Goal

After TASK-BE-300 swapped the OAuth login/callback flow onto the domain `SocialIdentity` + `SocialIdentityRepository` port (+ `toDomain()`/`fromDomain()` mapping), the JPA entity's construction/mutation API is **production-dead**:

- `SocialIdentityJpaEntity.create(5-arg)` — no production caller.
- `SocialIdentityJpaEntity.create(4-arg)` `@Deprecated` overload — no production caller; it is a **silent-default foot-gun** (callers omit `tenantId`; it silently defaults to `"fan-platform"` — the exact BE-288/BE-289 class of multi-tenant silent-default risk that BE-289 established must be removed once a deliberate follow-up can do it).
- `updateLastUsedAt()` / `updateProviderEmail()` — no production caller; the live mutators are now on the domain `SocialIdentity` (`OAuthLoginTransactionalStep:68,70` call them on the *domain* object, not the entity).

The only remaining users are 4 call sites of `create(4-arg)` in `SocialIdentityJpaRepositoryTest` (a `@DataJpaTest` query slice). BE-300 deliberately preserved these under the BE-295 "@Deprecated-보존" discipline (don't widen a behavior-neutral port-extraction task with dead-code removal — defer to a separate deliberate task). This **is** that deliberate follow-up.

Remove the 4 app-unused methods; migrate the persistence test's 4 construction sites onto the production-consistent `SocialIdentityJpaEntity.fromDomain(SocialIdentity.create(...))` path (the exact route `SocialIdentityRepositoryAdapter.save()` uses). The test continues to validate the identical JPA query slice (`findByProviderAndProviderUserId` + `findByAccountId`), composite-unique, and Flyway-validated schema — only the entity construction route changes, and it changes to the one production actually exercises.

# Decision authority (why no ADR / decision-gate)

This is **dead-code removal of an internally-redundant API** — not a convention choice — so it follows the BE-289 / BE-290 G7 / BE-294 discipline and requires **no ADR and no decision-gate**:

- The removed methods have **zero production callers** (grep-verified; AC-1). The domain `SocialIdentity` already owns the identical factory/mutator semantics (`SocialIdentity.create` javadoc: "Semantics are byte-identical to the prior `SocialIdentityJpaEntity.create(5-arg)`").
- The test migration target (`fromDomain(SocialIdentity.create(accountId, null, provider, providerUserId, providerEmail))`) is **byte-behavior-identical** to the removed `entity.create(4-arg)`: id `null` → JPA INSERT; `tenantId == null` → `"fan-platform"`; `connectedAt == lastUsedAt == Instant.now()`. Verified against `SocialIdentity.create` (auth-service `domain/social/SocialIdentity.java:46-52`) + `fromDomain` (`infrastructure/persistence/SocialIdentityJpaEntity.java:84-95`).
- No competing convention exists (BE-289 already set the precedent that the analogous legacy overload is removed in a deliberate follow-up). Per BE-290/BE-294: removing now-dead code that the codebase's own newer abstraction supersedes is reality-alignment, not an ADR trigger.

# Scope

## In Scope

1. `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/persistence/SocialIdentityJpaEntity.java` — delete the 4 app-unused members + their javadoc:
   - `static SocialIdentityJpaEntity create(String, String, String, String, String)` (5-arg) + javadoc.
   - `@Deprecated static SocialIdentityJpaEntity create(String, String, String, String)` (4-arg) + javadoc.
   - `void updateLastUsedAt()`.
   - `void updateProviderEmail(String)`.
   - **Keep**: all fields, JPA annotations, `@NoArgsConstructor(access = PROTECTED)`, `@Getter`, `toDomain()`, `fromDomain()`, and `import java.time.Instant;` (still referenced by the `connectedAt`/`lastUsedAt` field types). The entity's persisted shape is byte-unchanged.
2. `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/SocialIdentityJpaRepositoryTest.java` — migrate the 4 `SocialIdentityJpaEntity.create(accountId, provider, providerUserId, providerEmail)` call sites (L63, L82, L93, L94) to `SocialIdentityJpaEntity.fromDomain(SocialIdentity.create(accountId, null, provider, providerUserId, providerEmail))`; add `import com.example.auth.domain.social.SocialIdentity;`. All `@Test` methods, `@DisplayName`s, assertions, and the count (5 tests) byte-unchanged.
3. `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/domain/social/SocialIdentity.java` — javadoc-only: the `create` javadoc cross-references the now-removed `SocialIdentityJpaEntity.create(5-arg)`. Reword to state the semantics inline (`tenantId` defaults to `"fan-platform"` when null; `connectedAt == lastUsedAt == Instant.now()`) without the dead cross-ref — BE-289 WI-1 "grep=0 production dead-ref" discipline. No behavior change.

## Out of Scope

- `toDomain()` / `fromDomain()` — production path (adapter), untouched.
- `SocialIdentityRepository` port / `SocialIdentityRepositoryAdapter` / `OAuthLoginUseCase` / `OAuthLoginTransactionalStep` — BE-300 final state, untouched.
- `SocialIdentityJpaRepository` (Spring Data interface, incl. `findByAccountId`) — the test still exercises it; untouched.
- Any spec / contract / data-model / Flyway / event — the `social_identities` table shape, columns, composite-unique, and JPA query derivation are byte-unchanged. None touched.
- Any other GAP service / non-auth file.
- An ADR — explicitly not warranted (§ Decision authority).

# Acceptance Criteria

- **AC-1 (dead-code gone, no production caller existed)**: post-removal, `grep -rn "SocialIdentityJpaEntity.create\|\.updateLastUsedAt()\|\.updateProviderEmail(" auth-service/src/main` → **0** (production never called them; the live `.updateLastUsedAt()/.updateProviderEmail()` at `OAuthLoginTransactionalStep:68,70` are on the *domain* `SocialIdentity`, unaffected). `grep -rn "SocialIdentityJpaEntity\.create" auth-service` → **0** (test migrated too).
- **AC-2 (behavior-neutral construction)**: each migrated test site builds an entity with `id == null` (→ INSERT on `saveAndFlush`), `tenantId == "fan-platform"`, `connectedAt == lastUsedAt == Instant.now()` — identical to the removed `create(4-arg)`. No test assertion, `@DisplayName`, or test-method count changes (5 `@Test`).
- **AC-3 (entity persisted shape byte-identical)**: `SocialIdentityJpaEntity` field set, `@Column` definitions, `@Table`, `@Id/@GeneratedValue`, `@NoArgsConstructor(PROTECTED)`, `@Getter`, `toDomain()`, `fromDomain()` are byte-unchanged. `git diff` of the entity = pure deletion of the 4 members + their javadoc, nothing else.
- **AC-4 (dead-ref zero)**: no production javadoc/string references a removed method. `grep -rn "SocialIdentityJpaEntity.create" auth-service/src/main` → 0 incl. `SocialIdentity.java` javadoc (reworded).
- **AC-5 (auth-service build/test green)**: `:auth-service:test` compiles and passes; the `SocialIdentityJpaRepositoryTest` Testcontainers slice (CI-authoritative — `@ExtendWith(DockerAvailableCondition.class)`, may SKIP locally) validates the unchanged query slice on real MySQL in CI. Total auth-service test count unchanged vs BE-301 baseline (`460/0/0/22`), construction route only differs.
- **AC-6 (no behavioral surface beyond test internals)**: zero production runtime behavior change (only dead methods deleted + one javadoc reworded). `git diff --stat` = 3 files (1 entity, 1 test, 1 domain javadoc).
- **AC-7 (CI)**: code change → full pipeline incl. GAP Integration (Testcontainers) — the authoritative behavior-neutral proof (real MySQL `social_identities` INSERT + `findBy…` query slice). Self-review APPROVED.

# Related Specs

- `projects/global-account-platform/specs/services/auth-service/architecture.md` § Forbidden Dependencies (L169) + § 퍼시스턴스 (MySQL `social_identities`) — unchanged; this task removes dead code, the spec already reflects the BE-300 port reality.
- `project_refactor_sweep_status` (memory) § "architecture.md Data store drift closure (TASK-BE-302)" — records observation (b) as the sole remaining residual this task closes.

# Related Contracts

- None. No HTTP/event contract references `SocialIdentityJpaEntity`'s factory/mutators; they are infrastructure-internal construction helpers. Invisible across every service boundary. The `social_identities` persisted schema is byte-unchanged.

# Edge Cases

- **`Instant` import retention**: `create()`/`updateLastUsedAt()` were the only `Instant.now()` users, but `import java.time.Instant;` MUST remain — the `connectedAt`/`lastUsedAt` field declarations are typed `Instant`. Removing the import would break compilation; the diff must not touch it.
- **`@DataJpaTest` still meaningful**: the test's purpose is the JPA query derivation + composite-unique + Flyway-validated schema, not the construction helper. Migrating construction to `fromDomain(SocialIdentity.create(...))` keeps it exercising the *production* mapping (`SocialIdentityRepositoryAdapter.save` uses the same `fromDomain`), strictly increasing fidelity. `findByAccountId` (Spring Data interface method, not hoisted to the port per BE-300) is still tested directly on `SocialIdentityJpaRepository` — correct, untouched.
- **`tenantId == null` → `"fan-platform"`**: the removed `create(4-arg)` defaulted tenantId; `SocialIdentity.create(accountId, null, …)` reproduces it exactly (`tenantId != null ? tenantId : "fan-platform"`). Passing `null` (not `"fan-platform"`) is the faithful migration — proves the default path, identical persisted value.
- **No `@Deprecated` left dangling**: removing both `create` overloads also removes the only `@Deprecated` annotation on the entity — no orphaned deprecation javadoc remains.

# Failure Scenarios

- **A migrated test site changes a persisted value** (e.g. passes `"fan-platform"` literal instead of `null`, or sets a non-null id) → AC-2 rejects; persisted row would differ from the removed `create(4-arg)` semantics. Mitigation: pass `null` tenantId + use `SocialIdentity.create` (id always null) — verified against source.
- **`import java.time.Instant;` accidentally removed** (IDE "optimize imports" after deleting `Instant.now()` users) → compile failure (`connectedAt`/`lastUsedAt` field types). AC-3 (pure-deletion-of-4-members diff) + compile catch it.
- **A production caller actually existed (false dead-code assumption)** → disproven: AC-1 grep on `src/main` = 0 (the only `.updateLastUsedAt()/.updateProviderEmail()` production calls are on the domain `SocialIdentity`, post-BE-300). No misclassification.
- **Treated as needing an ADR / decision-gate (over-process)** → § Decision authority + BE-289 precedent: deliberate removal of code the codebase's own newer abstraction superseded, no competing convention = not an ADR trigger. Over-gating would be the error.
- **Scope creep into `toDomain`/`fromDomain` or the port** → Out-of-Scope; those are BE-300 production-path final state. Any edit there is a finding.

# Verification

1. `grep -rn "SocialIdentityJpaEntity\.create\|\.updateLastUsedAt()\|\.updateProviderEmail(" projects/global-account-platform/apps/auth-service/src/main` → 0 (AC-1/AC-4; the domain-object `updateLastUsedAt/updateProviderEmail` calls are on `SocialIdentity`, not matched by `SocialIdentityJpaEntity.` and semantically distinct).
2. `grep -rn "SocialIdentityJpaEntity\.create" projects/global-account-platform/apps/auth-service` → 0 (test migrated; AC-1).
3. `git diff SocialIdentityJpaEntity.java` → pure deletion of 2 `create` overloads + 2 mutators + their javadoc; fields/annotations/`toDomain`/`fromDomain`/`Instant` import byte-unchanged (AC-3).
4. `git diff SocialIdentityJpaRepositoryTest.java` → 4 construction sites swapped to `fromDomain(SocialIdentity.create(accountId, null, …))` + 1 added import; assertions/`@DisplayName`/test count unchanged (AC-2).
5. `git diff SocialIdentity.java` → javadoc-only rewording (no dead cross-ref, no behavior) (AC-4/AC-6).
6. `./gradlew :…:auth-service:test` (or `:test --rerun-tasks`) compiles + passes; count == BE-301 baseline `460/0/0/22` (Docker-gated slice SKIPs locally, runs in CI) (AC-5).
7. `git diff --stat` → 3 files (AC-6). CI: full pipeline incl. GAP Integration (Testcontainers) green = behavior-neutral authoritative proof (AC-7).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (tightly-scoped mechanical dead-code deletion + 4-site test migration onto a verified byte-identical production path; behavior-neutral, BE-289 precedent, no judgement beyond the already-verified equivalence) — executed directly this session given size / 리뷰=Opus 4.7 (inline self-review; pure-deletion-diff + construction-equivalence + dead-ref-zero discipline).
