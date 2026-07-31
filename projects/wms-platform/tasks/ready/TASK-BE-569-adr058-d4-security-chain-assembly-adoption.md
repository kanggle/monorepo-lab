# Task ID

TASK-BE-569

# Title

ADR-MONO-058 D4 (wms-platform) — adopt `libs/java-security-servlet`'s security-chain-assembly
builder (once `TASK-MONO-500` lands) in the 5 wms servlet services' `OAuth2ResourceServerConfig` +
`SecurityConfig` generic tail

# Status

ready

# Owner

backend

# Task Tags

- code
- api
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

- **선행 (blocking): `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md`** — the
  root task that promotes the builder/factory itself into `libs/java-security-servlet`. `TASK-MONO-500`
  is filed and `ready` but **NOT YET LANDED** as of this task's filing. This task cannot start
  implementation until `TASK-MONO-500` merges and the builder class exists — verify by reading
  `libs/java-security-servlet`'s source tree directly (not by inference from `TASK-MONO-500`'s own
  Status field, which can go stale) before beginning.
- **관련 (비차단)**: `TASK-BE-570` (D5, filed alongside this task) promotes wms's per-service `PublicPaths`
  value type — this task's builder consumes a `PublicPathSet` instance per service (per
  `TASK-MONO-500`'s own Scope: "the service supplies... its own `PublicPathSet` instance (from D5)").
  **Not a hard blocker** — this task can proceed with wms's current inline `PUBLIC_PATHS` array wired
  directly into the builder if `TASK-BE-570` has not yet landed when this task starts, but landing D5
  first makes this task's implementation cleaner (one less inline-array-to-`PublicPathSet` conversion
  happening inside this task). Recommended order: `TASK-BE-570` before this task, not required.
- wms-platform is **not** listed in `ADR-MONO-058 § 1.1`'s D1 (actor/JWT-claim extraction) row — no
  `ActorContext`/`ActorContextResolver`/`@CurrentActor` pattern exists in wms's services (confirmed: wms's
  `SecurityConfig.jwtAuthenticationConverter()` is a static role-claim-lifting method, not a resolver/
  `@CurrentActor` mechanism). This task is filed standalone, not bundled with a D1 adoption, matching the
  ADR's own instruction that a project with only D4 (not D1+D4 together) gets its own single-decision task.

---

# Goal

Close wms-platform's share of `ADR-MONO-058 § D4` (ACCEPTED 2026-07-30) — adopt the security-chain
assembly builder `TASK-MONO-500` promotes to `libs/java-security-servlet`, replacing the near-identical
`NimbusJwtDecoder` + `AllowedIssuersValidator`/`TenantClaimValidator` chain-assembly wiring currently
hand-copied across wms's 5 servlet services.

**Measured against the tree** (not the ADR's cross-project paraphrase, which counted wms among
scm/erp/fan for "~17 copies"): wms-platform carries this pattern in **two** files per service —
`config/security/OAuth2ResourceServerConfig.java` (or `config/OAuth2ResourceServerConfig.java` for
`admin-service`) and the top of `config/SecurityConfig.java` — across all 5 servlet services
(`master`, `inventory`, `outbound`, `inbound`, `admin`; `gateway-service` is reactive, out of scope).
Confirmed byte-near-identical `OAuth2ResourceServerConfig` in `inventory-service` (read in full): a
`@Value`-injected `jwk-set-uri`/`wms.oauth2.allowed-issuers`/`wms.oauth2.required-tenant-id` triplet, a
`JwtDecoder` bean building `NimbusJwtDecoder.withJwkSetUri(...)`, and a `DelegatingOAuth2TokenValidator`
chain of `JwtTimestampValidator` + `AllowedIssuersValidator` + `TenantClaimValidator.forTenant(...)`
(**wms is the one platform that does NOT call `.allowSuperAdminWildcard()`** — `ADR-MONO-048 § D5`,
pinned by `WmsTenantGatePolicyTest` in every one of the 5 services — the builder's default posture must
not silently add this back) + `JwtValidators.createDefault()`.

`SecurityConfig`'s generic tail (CSRF-disabled, CORS-disabled, stateless session, `.authorizeHttpRequests`
public-vs-authenticated split, `.oauth2ResourceServer(...)` wiring, the `TENANT_FORBIDDEN`-vs-401
`authenticationEntryPoint`, the `FORBIDDEN` `accessDeniedHandler`) is also near-identical across all 5 —
`admin-service`'s only differs by adding a `RoleHierarchy` bean and passing it to a
`DefaultHttpSecurityExpressionHandler`.

---

# Scope

## In Scope

- **Wait for `TASK-MONO-500` to land** before starting; then read the actual promoted builder API (its
  shape is not yet fixed as of this task's filing — `TASK-MONO-500`'s own Implementation Notes explicitly
  say to re-verify the "near-byte-identical" claim against the live tree, including wms, before finalizing
  the builder's shape) and design each service's adoption against the real API, not an assumed one.
- Each of `master-service`, `inventory-service`, `outbound-service`, `inbound-service`, `admin-service`:
  - Replace `OAuth2ResourceServerConfig`'s hand-assembled `NimbusJwtDecoder` + validator chain with an
    invocation of the promoted builder, supplying wms's own property keys
    (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, `wms.oauth2.allowed-issuers`,
    `wms.oauth2.required-tenant-id`) and **explicitly not** calling any super-admin-wildcard-allowing
    option the builder exposes (preserve `ADR-MONO-048 § D5`'s wms-specific refusal).
  - Replace `SecurityConfig`'s generic tail (CSRF/CORS/session/`.oauth2ResourceServer` wiring) with the
    builder's equivalent, supplying wms's own `PublicPathSet`/`PUBLIC_PATHS` data and the two
    wms-specific handlers (`TENANT_FORBIDDEN`-vs-401 entry point, `FORBIDDEN` access-denied handler) as
    builder parameters/callbacks if the builder's API supports injecting them, or keep them service-local
    if it does not (do not force a shared handler shape the builder doesn't offer — the builder is
    mechanism, not every possible policy hook; verify against the actual API landed by `TASK-MONO-500`).
  - `admin-service`'s `RoleHierarchy`/`DefaultHttpSecurityExpressionHandler` addition stays service-local
    — it is `admin-service`-specific policy (role hierarchy is not shared with the other 4 services), not
    part of the generic assembly the builder promotes.
  - Each service's `jwtAuthenticationConverter()` (the `role`/`roles` claim-lifting logic) is **not**
    touched by this task — that is D1 territory (actor/JWT-claim extraction), which wms is not scoped for
    per the Dependency Markers above; the converter stays exactly as each service already has it, wired
    into the builder as an input if the builder's API takes one, or left as a separate bean otherwise.
  - Each service's `build.gradle` confirmed to still declare `implementation project(':libs:java-security-
    servlet')` (all 5 already do, transitively via `com.example.security.oauth2.*` imports — re-verify at
    implementation time) — no new dependency expected, this is a within-existing-dependency adoption.
- `WmsTenantGatePolicyTest` (present in all 5 services) re-run against the builder-assembled chain and
  confirmed to still pass — this is the load-bearing test that the super-admin-wildcard refusal (and the
  entitlement-trust dual-accept in `admin-service`) still holds after the swap.

## Out of Scope

- **`TASK-MONO-500` itself** — the builder's promotion into `libs/java-security-servlet` is a separate,
  already-filed root task. This task only adopts it once landed.
- **`gateway-service`** — reactive (Spring Cloud Gateway); it has its own
  `config/OAuth2ResourceServerConfig.java` using WebFlux-native types, not the servlet
  `NimbusJwtDecoder`/`HttpSecurity` shapes this builder assembles. `libs:java-security-servlet` must never
  reach a reactive classpath.
- **D1 (actor/JWT-claim extraction)** — wms has no `ActorContext`/`@CurrentActor` mechanism to promote;
  each service's `jwtAuthenticationConverter()` stays as-is.
- **D5 (`PublicPathSet`)** — a separate task (`TASK-BE-570`); this task consumes whatever wms's
  `PublicPaths`/inline `PUBLIC_PATHS` shape is at the time it runs (see Dependency Markers — either form
  works as a builder input).
- **`admin-service`'s `RoleHierarchy`** — service-specific authorization policy, not generic assembly.
- **`AllowedIssuersValidator`/`TenantClaimValidator` themselves** — already shared per `ADR-MONO-049`, not
  touched by this task, only their *assembly* changes.
- Any change to wms's actual issuer allow-list, required tenant id, or the super-admin-wildcard-refusal
  policy — this is a mechanism swap, not a policy change; `WmsTenantGatePolicyTest` is the regression net.
- ADR-MONO-058 D2 / D3 / D6 / D7 / D8 — separate tasks (`D2`/`D3`/`D5`/`D7` filed alongside this one as
  `TASK-BE-567`/`568`/`570`/`571`).

---

# Acceptance Criteria

- [ ] **AC-0 (prerequisite confirmed, not assumed)** — before implementation starts, confirm
      `TASK-MONO-500`'s builder class actually exists in `libs/java-security-servlet`'s source tree (read
      the file, not the task's Status field) and record its actual API shape here or in the implementation
      PR.
- [ ] **AC-1 (adoption, not duplication)** — all 5 services' `OAuth2ResourceServerConfig` and
      `SecurityConfig` generic tails invoke the shared builder instead of hand-assembling
      `NimbusJwtDecoder`/the validator chain/the generic `HttpSecurity` wiring. Repo-wide grep for
      `NimbusJwtDecoder.withJwkSetUri` and `new DelegatingOAuth2TokenValidator` under
      `apps/{master,inventory,outbound,inbound,admin}-service/src/main` shows **zero** hits outside the
      builder's own invocation site (which may itself live in `libs/`, not in the service).
- [ ] **AC-2 (super-admin-wildcard refusal preserved)** — `WmsTenantGatePolicyTest` in all 5 services
      still asserts a SUPER_ADMIN wildcard (`*`) token is **rejected**, unchanged from before this task
      (`ADR-MONO-048 § D5`). This is the single highest-risk regression this task could introduce — if the
      builder's default differs from wms's current explicit non-call of `.allowSuperAdminWildcard()`,
      that must be caught here, not discovered later.
- [ ] **AC-3 (entitlement-trust dual-accept preserved, `admin-service` only)** — `admin-service`'s
      `TenantClaimValidator.isEntitled(...)`-based `ROLE_WMS_VIEWER` synthesis (in
      `jwtAuthenticationConverter()`, untouched by this task per Out of Scope) continues to function
      correctly wired into the builder-assembled chain — proven by `admin-service`'s existing entitlement-
      trust tests passing unmodified.
- [ ] **AC-4 (auth behavior byte-preserved for the happy/failure paths)** — for each service: valid-token
      200, wrong-issuer 401, wrong-tenant 403 `TENANT_FORBIDDEN`, missing-token 401 `UNAUTHORIZED`,
      insufficient-role 403 `FORBIDDEN` — all unchanged, proven by each service's existing security
      integration test suite passing without weakening any assertion.
- [ ] **AC-5 (opt-in posture, no auto-configuration)** — confirm (per `TASK-MONO-500`'s own constraint)
      that adopting the builder does not introduce a component-scanned/auto-configured bean into any wms
      service — each service still explicitly invokes the builder from its own `@Configuration` class.
- [ ] **AC-6 (baseline parity)** — record each of the 5 services' test count before/after. No test may
      disappear. All 5 `:check`/`:test` tasks green; wms CI `Integration`/`E2E` lanes (Testcontainers,
      including `GatewayRoutingAuthIntegrationTest`/`GatewayMasterE2ETest` which exercise cross-service
      auth against these 5 services) green — this is the authoritative signal for an auth-path change,
      local Windows Docker is not (`project_testcontainers_docker_desktop_blocker`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D4, § 4, § 6 (ACCEPTED)
- `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` — **the prerequisite; read
  its actual landed API before designing this task's implementation, not this task's paraphrase of it.**
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the tracking task this splits from.
- `docs/adr/ADR-MONO-048-...md` § D5 — the wms-specific super-admin-wildcard refusal this task must not
  regress.
- `docs/adr/ADR-MONO-049-...md` — the prior `AllowedIssuersValidator`/`TenantClaimValidator` consolidation
  this builder composes with.
- `platform/shared-library-policy.md` § Decision Rule, § Ownership Rule, § No context-wide annotations
- `specs/services/{master,inventory,outbound,inbound,admin}-service/architecture.md` § Security

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

None — internal security-wiring mechanism, no HTTP/event wire-format change. If any auth behavior is
found to require a wire-visible change during implementation, stop and treat that as a contract question,
not a mechanical adoption detail.

---

# Target Service

- `master-service`, `inventory-service`, `outbound-service`, `inbound-service`, `admin-service`
  (wms-platform)
- Consumes `libs/java-security-servlet` (already a dependency; no new module added by this task)

---

# Architecture

Follow each target service's own `architecture.md § Security`. The builder invocation site stays in each
service's existing `config`/`config/security` package (4 of 5 Hexagonal, `admin-service` Layered per
`PROJECT.md § Overrides`) — no layer or package relocation as part of this task.

---

# Implementation Notes

- **Do not start until `TASK-MONO-500` is actually merged** — this task's Scope was written against
  `TASK-MONO-500`'s *proposed* builder shape (its own task file, still `ready` as of this filing); the
  landed shape may differ once that task's own Implementation Notes ("re-verify the near-byte-identical
  claim... before finalizing the builder's shape") are acted on.
- `ADR-MONO-058 § 4` flags D4 as one of the two highest-risk decisions in the whole ADR (auth-path, every
  servlet service). Treat every AC above as load-bearing, not procedural — a wrong default here is
  inherited by all 5 wms services at once.
- Order of work that keeps the diff reviewable and risk contained: (1) one service end-to-end —
  `inventory-service` (already read in full during this task's investigation, no extra service-specific
  wiring like `admin-service`'s `RoleHierarchy`) — verify `WmsTenantGatePolicyTest` and the full auth
  integration suite pass before touching the rest; (2) `master`/`outbound`/`inbound` (structurally
  identical to `inventory`); (3) `admin-service` last (the one service with the extra `RoleHierarchy`
  wiring, so its adoption diff is the largest and most likely to surface a builder-API gap).
- If the landed builder's API cannot cleanly express `admin-service`'s
  `RoleHierarchy`/`DefaultHttpSecurityExpressionHandler` composition, keep that part service-local and
  only adopt the builder for the parts that do fit (JWT decoder + validator chain + generic filter-chain
  skeleton) — do not force-fit `admin-service`'s policy into the shared mechanism.

---

# Edge Cases

- `admin-service`'s method-level `@PreAuthorize` role hierarchy
  (`WMS_SUPERADMIN > WMS_ADMIN > WMS_OPERATOR > WMS_VIEWER`) must continue to apply correctly after the
  URL-level filter chain is reassembled through the builder — confirm the `RoleHierarchy` bean is still
  wired to both the `DefaultHttpSecurityExpressionHandler` (URL-level) and method security (unchanged
  responsibility, but re-verify the wiring order survives the refactor).
- The `TENANT_FORBIDDEN`-vs-401 `authenticationEntryPoint` distinguishing logic (walking the
  `JwtValidationException`/`InvalidBearerTokenException` cause chain for `TenantClaimValidator`'s
  `tenant_mismatch` error code) is wms-specific behavior (`TASK-MONO-019`) — confirm the builder either
  accepts this as a pluggable entry point or that it stays service-local without conflicting with
  whatever entry point the builder itself wires by default.
- `inventory-service`'s `@ConditionalOnWebApplication(type = SERVLET)` guard on `SecurityConfig` (needed
  so non-web `@SpringBootTest`s, e.g. the Kafka-consumer/DB integration tests, don't fail on a missing
  `HttpSecurity` bean — `TASK-MONO-335`/`TASK-BE-334` precedent) must be preserved on whichever class ends
  up owning the `@Bean SecurityFilterChain` method after adoption.

---

# Failure Scenarios

- **Silent super-admin-wildcard reopening.** If the promoted builder's default differs from wms's current
  explicit refusal and no adopting service overrides it, every one of the 5 services silently reopens a
  gate `ADR-MONO-048 § D5` deliberately closed. AC-2 is the guard; treat any AC-2 failure as a stop, not a
  "fix the test" signal.
- **Auto-configuration creep.** If the builder (or wms's invocation of it) ends up component-scanned
  rather than explicitly invoked, every adopting service's security posture changes on the library's next
  version bump without any code change in the service — Hard Stop per `shared-library-policy.md § No
  context-wide annotations`, verified by AC-5.
- **`admin-service`'s `RoleHierarchy` silently dropped.** If the adoption diff for `admin-service` reuses
  the other 4 services' invocation pattern verbatim without re-wiring the role hierarchy, method-level
  `@PreAuthorize` checks would still work (unaffected) but URL-level hierarchical authorization would
  silently stop applying — caught by `admin-service`'s existing security tests if they cover URL-level
  hierarchy, otherwise a coverage gap to close as part of this task.
- **Starting before `TASK-MONO-500` lands.** Implementing against a not-yet-merged, still-mutable builder
  API produces rework at best and a divergent local copy at worst — AC-0 exists to force this check.
- **Scope leak into the other affected projects.** `ADR-MONO-058 § 6` forbids a cross-project mega-PR;
  scm/erp/fan D4 adoption are separate tasks.

---

# Test Requirements

- All 5 services' existing `WmsTenantGatePolicyTest`, security integration tests, and controller-slice
  auth tests pass unmodified in assertion content (rewritten only where necessarily coupled to the old
  assembly's internal structure, never weakened).
- `admin-service`: URL-level `RoleHierarchy` coverage confirmed present (add if missing — see Edge Cases)
  before/after comparison.
- 5 services' `:check`/`:test` green. wms CI `Integration`/`E2E` (Testcontainers) green, authoritative —
  includes `GatewayRoutingAuthIntegrationTest`/`GatewayMasterE2ETest` which exercise these services'
  chains end-to-end through the gateway.

---

# Definition of Done

- [ ] `TASK-MONO-500` confirmed landed (builder exists in `libs/java-security-servlet`) before
      implementation started
- [ ] Implementation completed (all 5 wms servlet services adopt the builder)
- [ ] `WmsTenantGatePolicyTest` (super-admin-wildcard refusal) green in all 5 services, unweakened
- [ ] `admin-service`'s `RoleHierarchy`/entitlement-trust behavior confirmed unregressed
- [ ] Tests passing; per-service before/after counts recorded; no test lost
- [ ] `gateway-service` confirmed untouched (reactive, out of scope)
- [ ] Ready for review
