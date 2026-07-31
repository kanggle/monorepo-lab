# Task ID

TASK-BE-568

# Title

ADR-MONO-058 D6 — iam-platform adopts the canonical `IamClientCredentialsTokenProvider` (`libs/java-security`) across `auth-service`/`account-service`/`admin-service`/`security-service` (4 copies)

# Status

ready

# Owner

backend

# Task Tags

- code
- security

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

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D6 found 7 copies of `IamClientCredentialsTokenProvider` across 3 projects
(iam, ecommerce, fan), a **live defect distributed unevenly** (missing UTF-8 Basic-auth encoding, missing timeouts on
several copies), not just duplication. `tasks/ready/TASK-MONO-501-adr058-d6-iam-client-credentials-token-provider-promotion.md`
promotes the canonical, already-fixed shape to `libs/java-security`. This task adopts that canonical class in
iam-platform's own 4 copies.

**Confirmed by direct code read (2026-07-31), not inherited from the ADR's 2026-07-29 audit table:** iam-platform's 4
copies —

- `apps/auth-service/src/main/java/com/example/auth/infrastructure/client/IamClientCredentialsTokenProvider.java`
- `apps/account-service/src/main/java/com/example/account/infrastructure/client/IamClientCredentialsTokenProvider.java`
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/client/IamClientCredentialsTokenProvider.java`
- `apps/security-service/src/main/java/com/example/security/service/infrastructure/client/IamClientCredentialsTokenProvider.java`

are **byte-structurally identical modulo package/javadoc/default `client-id`** and every one of them:

1. Builds its `RestClient` via **`RestClient.create()`** — no connect timeout, no read timeout at all on the
   token-fetch call itself (the exact "several iam copies still lack" gap the ADR § D6 names).
2. Encodes the Basic-auth header via **`Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes())`**
   — `.getBytes()` with no explicit `StandardCharsets.UTF_8`, i.e. the platform-default-charset bug RFC 7617 requires
   UTF-8 to avoid (the same defect class the ADR's `ecommerce/batch-worker` copy already fixed and the promotion task,
   `TASK-MONO-501`, is lifting into the canonical class).

So iam is not a marginal or stale audit hit — all 4 of its copies carry both halves of the live defect the ADR exists
to close.

---

# Scope

## In Scope

- **Blocked until `TASK-MONO-501` lands** (선행 — the canonical class must exist in `libs/java-security` before this
  task can compile against it). Re-check `libs/java-security`'s package for the promoted class at pickup time; if not
  yet landed, this task remains `ready` and un-startable (mirror the pattern `tasks/ready/TASK-MONO-495-*.md` used for
  its own not-yet-actionable placeholder state).
- Delete all 4 local `IamClientCredentialsTokenProvider` classes and repoint every caller
  (`AccountServiceClient`/`AdminAssignmentClient` in auth-service; `AuthServiceClient` in account-service;
  `AccountServiceClient`/`AuthServiceClient`/`SecurityServiceClient`/`AccountServiceTenantClient`/
  `AccountServiceOrgNodeClient` in admin-service; `AccountServiceClient` in security-service) to the canonical
  `libs/java-security` class.
- Wire each service's existing `@Value` properties (`iam.internal-client.token-uri` /
  `iam.internal-client.client-id` / `iam.internal-client.client-secret`, per-service default `client-id`, e.g.
  `auth-service-client`/`admin-service-client`/`account-service-client`/`security-service-client`) and the
  `internal.invoke` scope literal into the canonical class's constructor/builder — confirm the promoted shape actually
  exposes a parameterized `scope`, per `TASK-MONO-501`'s own scope (it promotes `fan-platform/community-service`'s
  parameterized-scope shape) — iam's current inline `"grant_type=client_credentials&scope=internal.invoke"` body must
  survive as an equivalent parameterized call, not a hardcoded literal baked into the shared class.
- Add explicit connect/read timeout configuration for the token-fetch call in all 4 services (currently absent — this
  closes the "zero timeout on the token endpoint" defect, not merely the duplication). Use each service's existing
  `internal.invoke`-adjacent downstream-timeout property pattern where one exists (e.g. admin-service already has
  `admin.downstream.connect-timeout-ms`/`admin.downstream.read-timeout-ms` for its other clients) or introduce a
  small, service-scoped `iam.internal-client.connect-timeout-ms`/`read-timeout-ms` pair (default 3000/5000ms,
  matching the fastest sibling client already in each service) if no existing property fits.

## Out of Scope

- The promotion itself (`TASK-MONO-501`) — this task only adopts, does not build, the canonical class.
- `ecommerce`/`fan-platform`'s own D6 adoption — separate per-project tasks.
- D7 (`ResilienceClientFactory` outbound-client adoption) — filed separately as
  `tasks/ready/TASK-BE-569-adr058-d7-adopt-resilience-client-factory.md`; do not fold the two into one PR even though
  both touch some of the same client classes (D6 changes the token *provider* dependency; D7 changes the
  *RestClient*-construction mechanism of the callers — keep them independently reviewable and independently
  revertable).
- Any business-logic change in the 9 caller classes beyond the constructor/DI wiring needed to consume the new
  provider type.
- D1/D2 — investigated during this task's filing and found **not applicable** to iam-platform's current code (see
  Provenance below); no code changes for those decisions belong in this task.

---

# Acceptance Criteria

- [ ] **AC-0 (re-verify gate).** At pickup time, re-grep `apps/*/src/main/java/**/IamClientCredentialsTokenProvider.java`
      across iam-platform — confirm the 4 copies (and their `RestClient.create()` + `.getBytes()` shape) still exist
      as described above. If the count or shape has changed, re-scope before proceeding.
- [ ] **AC-1.** `libs/java-security`'s promoted class exists and its constructor/builder supports: parameterized
      `tokenUri`/`clientId`/`clientSecret` (per-service), parameterized `scope`, and configurable connect/read
      timeouts. If it does not (e.g. `TASK-MONO-501` landed with a narrower shape), **STOP** and file a small
      follow-up against `TASK-MONO-501`'s implementer rather than working around the gap locally in iam.
- [ ] **AC-2.** All 4 local `IamClientCredentialsTokenProvider.java` files are deleted; the 9 caller classes compile
      against the canonical `libs/java-security` type.
- [ ] **AC-3.** Each of the 4 services' token-fetch call now has an explicit, non-infinite connect + read timeout
      (verify via the wired `@Value` properties actually reaching the canonical class's constructor — not merely
      declared and unused).
- [ ] **AC-4.** UTF-8 Basic-auth encoding is inherited from the canonical class (assert this by re-running the
      canonical class's own unit test — `TASK-MONO-501`'s required UTF-8 byte-assertion test — against each
      service's actual `client-id`/`client-secret` values via at least one iam-side integration/slice test that
      exercises the real header bytes, not just "it compiles").
- [ ] **AC-5.** The `internal.invoke` scope request body is unchanged in wire shape (`grant_type=client_credentials&scope=internal.invoke`)
      — verified by a test asserting the outbound request body, since the SAS receiver's `RequiredScopeValidator`
      fails closed on a missing/blank scope (per the existing classes' own javadoc warning).
- [ ] **AC-6.** Existing per-service `IamClientCredentialsTokenProviderTest` classes are updated/consolidated (moved
      to test the canonical class where appropriate) and pass; no test asserting the old local class's FQCN survives
      unless intentionally kept as a thin wrapper test.
- [ ] **AC-7.** `./gradlew :projects:iam-platform:apps:auth-service:test :projects:iam-platform:apps:account-service:test :projects:iam-platform:apps:admin-service:test :projects:iam-platform:apps:security-service:test`
      all GREEN, counts equal to baseline (measured at pickup time) — no silent test-population loss.
- [ ] **AC-8.** No behavior change to any of the 9 callers beyond the provider swap and the new timeouts (diff
      confined to imports, constructor wiring, and the deleted provider files + any new/updated timeout properties).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus `rules/domains/saas.md` and `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D6, § 6 item 1
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the split-origin tracking task
- `tasks/ready/TASK-MONO-501-adr058-d6-iam-client-credentials-token-provider-promotion.md` — **prerequisite (선행)**,
  must land (merge) before this task can be implemented
- `platform/shared-library-policy.md`
- `projects/iam-platform/specs/services/{auth-service,account-service,admin-service,security-service}/architecture.md`

---

# Related Contracts

- None directly — this is an outbound OAuth2 `client_credentials` token acquisition (a call this repo's SAS itself
  serves), not an inbound HTTP/event contract. The `/internal/**` receiver contracts (`internal.invoke` scope
  requirement) are unaffected as long as AC-5's wire-shape check passes.

---

# Target Service

- `auth-service`, `account-service`, `admin-service`, `security-service` (iam-platform) — one task covering all 4,
  per this repo's PR-bundling freedom (`CLAUDE.md` "PR 묶음 케이스별 자유" — task-to-PR is not forced 1:1, and these 4
  changes are small, mechanical, and share one prerequisite).

---

# Architecture

Follow each service's own `architecture.md`:

- `projects/iam-platform/specs/services/auth-service/architecture.md`
- `projects/iam-platform/specs/services/account-service/architecture.md`
- `projects/iam-platform/specs/services/admin-service/architecture.md`
- `projects/iam-platform/specs/services/security-service/architecture.md`

---

# Implementation Notes

- All 4 existing classes carry the identical javadoc rationale for *not* using `spring-boot-starter-oauth2-client`
  (it would perturb each service's own Spring Security chain) — the canonical `libs/java-security` class must
  preserve that "plain `RestClient` + Jackson, no new autoconfiguration" posture; if `TASK-MONO-501` changed that
  posture, re-verify it doesn't introduce OAuth2-client autoconfiguration into any of these 4 services before wiring
  it in.
- `security-service` intentionally runs **no Spring Security web chain at all** (its own class's javadoc:
  "security-service intentionally has no Spring Security web chain, TASK-BE-317 옵션 b") — double-check the canonical
  class introduces no `spring-security-web` transitive dependency that would change that.
- Each service's default `client-id` differs (`auth-service-client`/`admin-service-client`/`account-service-client`/
  `security-service-client`) — these map to the `oauth_clients` rows seeded in auth-service's Flyway migrations; do
  not change any of these literal values.
- The `REFRESH_SKEW = Duration.ofSeconds(60)` cache-refresh-before-expiry constant is identical across all 4 copies —
  confirm the canonical class preserves this value (or an equivalent), since a shorter/longer skew changes token
  refresh frequency under load.

---

# Edge Cases

- If `TASK-MONO-501` promotes a shape that does not support a per-instance parameterized `scope` (e.g. it hardcodes
  a different scope literal from `fan-platform/community-service`'s original), iam's `internal.invoke` scope
  requirement cannot be satisfied without a follow-up change to the shared class — do not work around this by
  keeping a local wrapper that re-implements scope injection (that reintroduces the duplication this task exists to
  remove).
- `AccountServiceOrgNodeClient` (admin-service) already deliberately uses a **short** downstream read timeout (3s,
  not the 10s sibling default) for its own outbound call — unrelated to the token-provider's own timeout, but verify
  the two timeout configs (token-fetch vs. downstream-call) are not accidentally conflated when wiring the new
  provider's timeout properties into admin-service's `application.yml`.
- Test-context reuse: several of the 9 caller classes' unit tests likely construct `IamClientCredentialsTokenProvider`
  directly (or a mock of it) — a type change from a service-local class to a shared-library class may require
  updating mock/stub imports across multiple unrelated test files; budget for this in the diff, but it must remain
  mechanical (no assertion changes).

---

# Failure Scenarios

- **Promoting-class shape mismatch.** If the canonical class's constructor signature doesn't accommodate one of
  iam's 4 required `client-id` defaults or the `internal.invoke` scope, do not hardcode a workaround in iam — file
  the gap back against the promotion and wait, per this task's AC-1 stop condition.
- **Silently keeping the zero-timeout behavior.** If the canonical class is adopted without also wiring explicit
  timeout properties through iam's `application.yml`, the specific live defect this task exists to close (zero
  timeout on the SAS token endpoint call) survives under a different class name — AC-3 exists specifically to catch
  this.
- **Scope body drift.** If the adopted call's `grant_type=client_credentials&scope=internal.invoke` body shape
  changes even slightly (extra param, different encoding), the SAS's `RequiredScopeValidator` on the receiving side
  fails closed (401) for every `/internal/**` call in the fleet simultaneously — AC-5 is the safeguard.

---

# Test Requirements

- Reuse/update the 4 existing `IamClientCredentialsTokenProviderTest` classes (one per service) — retarget them at
  the canonical class with each service's real `client-id`/`scope`/`tokenUri` values, preserving the existing
  assertions (token caching, refresh-before-expiry, Basic-auth header presence) and adding the UTF-8 byte-level
  assertion inherited from `TASK-MONO-501`'s required test.
- Full per-service test suites (`:test` at minimum, `:integrationTest` where the service has Testcontainers-backed
  IT that exercises real `/internal/**` calls) must stay GREEN at baseline counts.

---

# Definition of Done

- [ ] `TASK-MONO-501` merged to `main` (prerequisite confirmed, not just assumed)
- [ ] All 4 local `IamClientCredentialsTokenProvider` classes removed, 9 callers repointed
- [ ] Explicit connect/read timeouts wired for all 4 services' token-fetch calls
- [ ] UTF-8 Basic-auth encoding verified per-service
- [ ] `internal.invoke` scope wire-shape unchanged (verified by test)
- [ ] All 4 services' test suites GREEN at baseline counts
- [ ] Task moved to `done`, referencing `TASK-MONO-501` and `TASK-MONO-495`

---

# Provenance

Filed while splitting `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` into per-project adoption
tasks for iam-platform, per that task's own Acceptance Criteria. The ADR's § 1.1 audit table (2026-07-29) also lists
D1 (actor/JWT-claim cluster) and D2 (error envelope) as applicable to iam — **both were investigated by direct code
read (2026-07-31) and found not to warrant a task**:

- **D1**: no `ActorContext`/`ActorContextResolver`/`ActorContextJwtAuthenticationConverter`/`@CurrentActor` pattern
  exists anywhere in iam-platform's 5 services. iam-platform is the JWT *issuer* (Spring Authorization Server), not a
  claims-consuming resource server in the shape the other 5 projects duplicated — its services instead use bespoke
  `OperatorAuthenticationFilter`+`JwtVerifier` (admin-service's own operator-JWT system) or a GAP
  `client_credentials`-JWT-only `/internal/**` gate (`account`/`auth`/`security`-service), and the reactive
  `gateway-service` extracts claims inline (explicitly out of D1's scope per the ADR's own reactive/servlet
  boundary). No task filed.
- **D2**: all 4 servlet services' exception handlers (`account-service.GlobalExceptionHandler`,
  `auth-service.AuthExceptionHandler`, `admin-service.AdminExceptionHandler`, `security-service.QueryExceptionHandler`)
  already `extends com.example.web.exception.CommonGlobalExceptionHandler` and return
  `com.example.web.dto.ErrorResponse` — i.e. D2 is **already fully adopted** in iam-platform (no local
  `ApiErrorBody`/duplicate envelope type exists, no `details`-field or status-code conflict found). No task filed.

분석=Sonnet 5 / 구현 권장=Sonnet 5 (mechanical class-swap + timeout wiring across 4 services, blocked on `TASK-MONO-501`).
