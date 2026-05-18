# Task ID

TASK-BE-297

# Title

OAuthClientMapper default-typing fails on array-valued ClientSettings (V0011/V0012 post-logout-redirect-uris)

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

# Goal

Fix issue discovered during the review of **TASK-BE-296** (PR #568 CI): `JpaRegisteredClientRepository.findByClientId("fan-platform-user-flow-client")` throws `OAuthClientMappingException` caused by Jackson `InvalidTypeIdException: Could not resolve type id 'http://localhost:3000/'`.

First **determine severity** (is this a live production break for fan-platform / ecommerce SAS `authorization_code` login via these clients, or a test-harness mapper-config artifact?), then apply the minimal correct fix without regressing any live OAuth client.

Root-cause hypothesis (from BE-296 diagnosis, recorded in `PlatformConsoleOidcClientSeedIntegrationTest` Javadoc): `OAuthClientMapper.buildSasMapper()` enables `SecurityJackson2Modules.enableDefaultTyping(...)`; default typing deserializes a JSON array as a `[typeId, value]` tuple. `V0011__seed_fan_platform_oidc_clients.sql` (and `V0012` ecommerce clients) seed `client_settings` with a plain JSON array `"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://fan-platform.local/"]` (no `@class`/type wrapper), so element 0 (`http://localhost:3000/`) is parsed as a Java type id → exception. Clients without an array-valued custom ClientSettings entry (demo-spa/test-internal V0008, wms V0010, scm V0013, platform-console-web V0015) deserialize cleanly.

# Scope

## In Scope

- Investigate whether the production `JpaRegisteredClientRepository` path uses the same `OAuthClientMapper`/`SecurityJackson2Modules.enableDefaultTyping` config (i.e. whether fan-platform-user-flow-client / ecommerce SAS `authorization_code` login is actually broken in production, or whether prior tests/flows simply never load these clients via this mapper).
- Confirm the full set of affected seeded clients (V0011 fan + V0012 ecommerce — any client whose `client_settings` carries an inline JSON array such as `post-logout-redirect-uris`).
- Apply the minimal correct fix, choosing between (decide with evidence; record rationale):
  - (a) corrective forward Flyway migration re-serializing the affected `client_settings` rows into the SAS/`SecurityJackson2Modules`-compatible typed form, or
  - (b) adjust `OAuthClientMapper` (de)serialization to round-trip array-valued ClientSettings under default typing, or
  - (c) prove non-issue (production path differs) and only harden tests/seed format.
- Regression coverage: an integration test that loads each affected client via `findByClientId` and asserts a correct `RegisteredClient` (incl. post-logout redirect URIs) — i.e. the assertion BE-296's narrowed regression test deliberately avoided.

## Out of Scope

- TASK-BE-296 functionality (platform-console OIDC client + registry — already merged/under review; do not re-touch V0015 or the registry).
- Any change to wms/scm/demo-spa/platform-console-web clients (proven unaffected).
- platform-console project.

# Acceptance Criteria

- [ ] Severity determined and documented (production break vs test-only artifact) with evidence.
- [ ] `findByClientId` for every affected client (fan-platform-user-flow-client + ecommerce V0012 clients) returns a correct `RegisteredClient` including post-logout redirect URIs — no `OAuthClientMappingException`.
- [ ] Chosen fix (a/b/c) applied minimally; rationale recorded; no behavior change to already-clean clients.
- [ ] Regression integration test added covering the previously-failing deserialization path.
- [ ] No regression to existing GAP auth/OAuth suites (full `:auth-service:integrationTest` green on CI Linux).
- [ ] If a data migration is used, it is forward-only and idempotent-safe.

# Related Specs

> Follow `platform/entrypoint.md` Step 0 — read `projects/global-account-platform/PROJECT.md`, load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`.

- `projects/global-account-platform/specs/services/auth-service/architecture.md`
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md`, `ADR-003-public-client-refresh-token-revoke-converter.md`
- (origin) `projects/global-account-platform/tasks/review/TASK-BE-296-platform-console-oidc-client-and-product-registry.md`

# Related Skills

- `.claude/skills/backend/...` — OIDC client persistence, Flyway corrective migration, Testcontainers integration testing

---

# Related Contracts

- None new. (Behavioral fix to OAuth client persistence; no HTTP/event contract change expected. If serialization form is contract-relevant, update the owning spec first.)

---

# Target Service

- `auth-service` (`OAuthClientMapper` / `JpaRegisteredClientRepository` / Flyway migrations V0011/V0012 lineage)

---

# Architecture

Follow `projects/global-account-platform/specs/services/auth-service/architecture.md` (OIDC Authorization Server, SAS JPA-backed registered client persistence).

---

# Implementation Notes

- Discovered during TASK-BE-296 review; full diagnosis in `apps/auth-service/src/test/java/com/example/auth/integration/PlatformConsoleOidcClientSeedIntegrationTest.java` Javadoc (`<h3>Discovered pre-existing defect</h3>`).
- Pre-existing (predates BE-296). V0015 has **zero** causal role — do not modify V0015.
- Treat as latent production risk until severity is proven; do not assume test-only.
- Must not regress live fan-platform / ecommerce OAuth — corrective migration (if chosen) must round-trip to the exact same effective ClientSettings.

---

# Edge Cases

- A client with multiple array-valued custom settings, not only `post-logout-redirect-uris`.
- Single-element vs multi-element arrays under default typing.
- Mixed estate: some rows already in typed form, some in plain-array form (migration must be conditional/idempotent).

---

# Failure Scenarios

- Corrective migration mis-serializes → live fan-platform/ecommerce login breaks (must be covered by the new regression IT before rollout).
- Mapper change alters serialization of currently-clean clients (demo-spa/wms/scm/platform-console-web) → broad OAuth regression.
- Severity mis-judged as test-only while production is actually broken (or vice-versa, causing unnecessary risky migration).

---

# Test Requirements

- Integration test (Testcontainers) loading each affected client via `findByClientId` asserting full `RegisteredClient` incl. post-logout redirect URIs.
- Regression: demo-spa / wms / scm / platform-console-web still resolve unchanged.
- Full `:auth-service:integrationTest` green on CI Linux.

---

# Definition of Done

- [ ] Severity documented with evidence
- [ ] Fix applied + rationale recorded; spec updated first if serialization is contract-relevant
- [ ] Affected clients deserialize correctly; regression IT added
- [ ] No regression to clean clients / existing suites (CI Linux green)
- [ ] Ready for review
