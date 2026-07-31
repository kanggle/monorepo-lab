# Task ID

TASK-BE-569

# Title

ADR-MONO-058 D4 (wms-platform) — adopt `libs/java-security-servlet`'s security-chain-assembly
builder (once `TASK-MONO-500` lands) in the 5 wms servlet services' `OAuth2ResourceServerConfig` +
`SecurityConfig` generic tail

# Status

review

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

- [x] **AC-0 (prerequisite confirmed, not assumed)** — `libs/java-security-servlet/src/main/java/com/example/
      security/servlet/ResourceServerChainAssembler.java` read in full before any edit. Landed API shape:
      two static factories — `jwtDecoder(String jwkSetUri) → JwtDecoderBuilder`
      (`.allowedIssuers(Collection)`/`.allowedIssuersCsv(String)`/`.validator(OAuth2TokenValidator<Jwt>)` →
      `.buildValidator()` | `.build()`) and `statelessJwtChain(HttpSecurity) → FilterChainBuilder`
      (`.securityMatcher(String...)`/`.publicPaths(PublicPathSet)`/`.authorizeRules(Customizer)`/
      `.authenticated(String...)`/`.anyRequestDenied()` (default) | `.anyRequestAuthenticated()`/
      `.jwtDecoder(JwtDecoder)`/`.jwtAuthenticationConverter(Converter)`/`.authenticationEntryPoint(...)`/
      `.accessDeniedHandler(...)`/`.httpCustomizer(Customizer<HttpSecurity>)` → `.build()`).
      **Measured API gap** (recorded, worked around, no `libs/` change): `httpCustomizer` takes a
      `Customizer<HttpSecurity>`, whose `customize` declares no checked exception, but
      `HttpSecurity.cors/httpBasic/formLogin/logout(Customizer)` all declare `throws Exception`
      (verified by `javap` against `spring-security-config-6.4.2.jar`) — so wms's four `disable()` calls
      **cannot** go through that hook. They are applied to `http` in the service's own
      `securityFilterChain(...) throws Exception` immediately before handing it to the assembler;
      `AbstractHttpConfigurer.disable()` only removes the configurer from the builder, so this is
      order-equivalent to the pre-D4 single fluent chain.
- [x] **AC-1 (adoption, not duplication)** — all 5 services' `OAuth2ResourceServerConfig` and
      `SecurityConfig` generic tails invoke the shared builder instead of hand-assembling
      `NimbusJwtDecoder`/the validator chain/the generic `HttpSecurity` wiring. Repo-wide grep for
      `NimbusJwtDecoder.withJwkSetUri` and `new DelegatingOAuth2TokenValidator` under
      `apps/{master,inventory,outbound,inbound,admin}-service/src/main` shows **zero** hits outside the
      builder's own invocation site (which may itself live in `libs/`, not in the service).
      **Measured**: repo-wide grep over `projects/wms-platform/apps/*/src/main/**/*.java` for
      `NimbusJwtDecoder|new DelegatingOAuth2TokenValidator|SessionCreationPolicy\.STATELESS|csrf\(`
      returns **5 hits, all of them Javadoc prose** (the `{@code NimbusJwtDecoder.withJwkSetUri(...)}`
      sentence in each of the 5 rewritten `OAuth2ResourceServerConfig` headers) — **zero executable
      occurrences**. `gateway-service` (reactive) has no hits either: it never used the servlet
      `NimbusJwtDecoder`/`HttpSecurity` shapes and is untouched by this task.
      Each service now also drops the private `parseCsv` helper (the builder's `allowedIssuersCsv(...)`
      owns it) — 5 copies of the same three lines removed.
- [x] **AC-2 (super-admin-wildcard refusal preserved)** — `WmsTenantGatePolicyTest` in all 5 services
      still asserts a SUPER_ADMIN wildcard (`*`) token is **rejected**, unchanged from before this task
      (`ADR-MONO-048 § D5`). This is the single highest-risk regression this task could introduce — if the
      builder's default differs from wms's current explicit non-call of `.allowSuperAdminWildcard()`,
      that must be caught here, not discovered later.
      **Evidence**: all 5 `WmsTenantGatePolicyTest` suites pass with **every assertion unchanged**,
      including `TheWildcardIsRefused#superAdminWildcardIsRejected`. The suites build their subject from
      the *production* `jwtTokenValidator()` bean method, so they now exercise the builder-assembled
      chain. The only edit to those files is **additive test wiring, not an assertion change**: a
      `JWK_SET_URI` constant + one `ReflectionTestUtils.setField(config, "jwkSetUri", …)` line, because
      the assembler's `jwtDecoder(jwkSetUri)` is also the entry point for `buildValidator()` and rejects
      a null URI at the wiring site (nothing fetches it — `buildValidator()` never constructs the
      decoder). `IssuerAxis#emptyAllowlistFailsAtStartup` still throws `IllegalArgumentException`,
      now raised one step earlier by `allowedIssuersCsv("")` instead of by `new AllowedIssuersValidator`.
      No service calls `.allowSuperAdminWildcard()`; the builder has no such switch to inherit.
- [x] **AC-3 (entitlement-trust dual-accept preserved, `admin-service` only)** — `admin-service`'s
      `TenantClaimValidator.isEntitled(...)`-based `ROLE_WMS_VIEWER` synthesis (in
      `jwtAuthenticationConverter()`, untouched by this task per Out of Scope) continues to function
      correctly wired into the builder-assembled chain — proven by `admin-service`'s existing entitlement-
      trust tests passing unmodified.
      **Evidence**: `SecurityConfigConverterTest` (5 tests) passes byte-unmodified. Additionally, AC-3 was
      previously only covered at converter-unit level — the new
      `api/dashboard/SecurityChainAssemblyParityTest#entitlementOnlyToken_reachesViewerDashboard` drives a
      real `Authorization: Bearer` request through the assembled chain with a mocked `JwtDecoder`
      returning `entitled_domains=[wms]` and no role claim, and asserts the
      `@PreAuthorize("hasRole('WMS_VIEWER')")` dashboard answers 200. (A
      `SecurityMockMvcRequestPostProcessors.jwt()` post-processor cannot prove this — it builds the
      `Authentication` directly and never runs the chain's converter.)
- [x] **AC-4 (auth behavior byte-preserved for the happy/failure paths)** — for each service: valid-token
      200, wrong-issuer 401, wrong-tenant 403 `TENANT_FORBIDDEN`, missing-token 401 `UNAUTHORIZED`,
      insufficient-role 403 `FORBIDDEN` — all unchanged, proven by each service's existing security
      integration test suite passing without weakening any assertion.
      **Evidence**: zero existing assertions weakened (the only test-file edits in the diff are the 5
      additive `jwkSetUri` wiring lines from AC-2). On top of that, a new per-service
      `SecurityChainAssemblyParityTest` (`@WebMvcTest` + real `SecurityConfig` bean → **real filter
      chain**, not a unit test of the builder call) pins the whole 401/403 boundary in all 5 services:
      missing-token → 401 `UNAUTHORIZED`; `tenant_mismatch` `JwtValidationException` → 403
      `TENANT_FORBIDDEN`; `invalid_issuer` → 401 `UNAUTHORIZED` (does not leak into 403); authorized
      token → reaches the controller; insufficient role → 403 `FORBIDDEN`; public path → bypasses auth.
      **The two tests that would catch a wrong builder default**: (a) unmapped path + valid token →
      404 not 403, which is the only assertion that distinguishes wms's `anyRequest().authenticated()`
      tail from the assembler's closed-by-default `denyAll()`; (b) `POST /logout` → 401 not 302, which
      is the only assertion that catches a dropped `.logout(disable)` (the assembler does not disable
      `LogoutFilter`, `HttpSecurityConfiguration` installs it by default).
      **Before/after equivalence measured, not argued**: each new parity test was additionally run with
      that service's `SecurityConfig.java` reverted to its pre-D4 form via `git stash push -- <path>` —
      **all 5 pass identically against both the hand-written and the assembled chain** (inventory run
      first as a single-service pilot, then master/outbound/inbound/admin as a batch; both stash runs
      `BUILD SUCCESSFUL`).
- [x] **AC-5 (opt-in posture, no auto-configuration)** — confirmed. `ResourceServerChainAssembler` is a
      `final` class with a private constructor and only static factories: no `@AutoConfiguration`,
      `@Configuration`, `@Component` or `@Bean` on it, and `libs/java-security-servlet` ships no
      `AutoConfiguration.imports` entry. Each of the 5 services still declares its own
      `@Bean JwtDecoder` / `@Bean OAuth2TokenValidator<Jwt>` / `@Bean SecurityFilterChain` inside its own
      `@Configuration` class and *calls* the builder from the body — no new bean, no new component scan,
      no new module on any classpath (all 5 already declared `implementation project(':libs:java-security-
      servlet')` via `TASK-BE-570`; **no `build.gradle` changed by this task**). `inventory`/`outbound`/
      `inbound`'s `@ConditionalOnWebApplication(type = SERVLET)` guard and all 5 services'
      `@ConditionalOnMissingBean(JwtDecoder.class)` are preserved on the same declarations.
- [x] **AC-6 (baseline parity)** — recorded, measured with `./gradlew … :test` before and after:
      | service | tests before | tests after | classes before → after |
      |---|---|---|---|
      | `master-service`    | 736 | 745 | 109 → 110 |
      | `inventory-service` | 230 | 239 |  39 → 40 |
      | `outbound-service`  | 252 | 263 |  45 → 46 |
      | `inbound-service`   | 225 | 235 |  34 → 35 |
      | `admin-service`     | 274 | 285 |  52 → 53 |
      **No test disappeared** (+50 net, each service +1 class = its new `SecurityChainAssemblyParityTest`).
      All 5 `:test` tasks green locally. wms CI `Integration`/`E2E` lanes (Testcontainers, incl.
      `GatewayRoutingAuthIntegrationTest`/`GatewayMasterE2ETest`) are the authoritative signal for this
      auth-path change and are **left to CI on the PR** — local Windows Docker is not authoritative
      (`project_testcontainers_docker_desktop_blocker`). Reviewer must confirm those lanes green before merge.

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

# Implementation Findings (measured 2026-07-31, not inherited from this task's own narrative)

**The 5 services are NOT uniform.** Each service's current posture was read and measured before any
edit, and every divergence below was preserved rather than normalised:

| axis | `master` | `inventory` | `outbound` | `inbound` | `admin` |
|---|---|---|---|---|---|
| `anyRequest()` tail | `authenticated()` | `authenticated()` | `authenticated()` | `authenticated()` | `authenticated()` |
| method security | `@EnableMethodSecurity` | `@EnableMethodSecurity` | `@EnableMethodSecurity` | `@EnableMethodSecurity` | `@EnableMethodSecurity(prePostEnabled = true)` |
| `@ConditionalOnWebApplication(SERVLET)` on `SecurityConfig` | **absent** | present | present | present | **absent** |
| URL-level role gates in the chain | none | none | **5 (`GET`/`POST`/`PATCH`/`PUT`/`DELETE` `/api/**`)** | none | none |
| `RoleHierarchy` bean | no | no | no | no | **yes** |
| error envelope | `{error:{code}}` | `{code}` | `{code}` | `{code}` | `{error:{code}}` |
| `PublicPaths` location | `config/` | `config/` | `config/` | `config/` | **`infra/security/`** |
| extra public path | — | — | `/webhooks/erp/order` | `/webhooks/erp/asn` | — |
| `TenantClaimEnforcer` bean | **no** | **no** | **no** | **no** | **no** |

Three findings worth calling out:

1. **`anyRequest()` tail is uniformly `authenticated()` across all 5** — i.e. wms sits entirely on the
   *open-er* of the two answers ADR-MONO-058 § D4 measured, and the assembler defaults to the *closed*
   one. Every one of the 5 adoptions therefore has an explicit `.anyRequestAuthenticated()` call, and a
   dedicated test (unmapped path + valid token → 404, not 403) that goes red if it is ever dropped. This
   is the single highest-risk line in the diff: forgetting it would silently 403 every unlisted path.
2. **No wms service has a `TenantClaimEnforcer` bean — not one, not "at least one"** (repo-wide grep over
   `projects/wms-platform`: 0 hits). wms enforces the tenant claim in the **JWT validator chain**
   (`TenantClaimValidator` inside `jwtTokenValidator()`), not with the shared servlet filter. Nothing was
   added; the assembler does not install one.
3. **`admin-service`'s `DefaultHttpSecurityExpressionHandler` was already dead before this task.** It is
   constructed from the `RoleHierarchy` bean and then **never attached to anything** (the
   `AuthorizationFilter` import next to it is unused too). URL-level hierarchical authorization has
   therefore never applied — and could not matter today anyway, because that chain's tail is
   `anyRequest().authenticated()` with no URL-level role expression for a hierarchy to widen. It is
   **carried over verbatim with a comment recording the measurement**, because D4 is a mechanism swap
   that must not change behaviour; wiring it up or deleting it is an authorization-policy decision for a
   follow-up task, not part of this adoption. The role hierarchy that *is* load-bearing — the
   method-level one, picked up from the bean by `@EnableMethodSecurity` — is now pinned by
   `SecurityChainAssemblyParityTest#roleHierarchy_adminSatisfiesTheViewerGate` (a `WMS_ADMIN` token
   passing a `hasRole('WMS_VIEWER')` gate), which had no coverage before.

**Files touched, per service** (15 modified + 5 added; no `libs/`, no `build.gradle`, no other project):

- ×5 `…/PublicPaths.java` — added `asSet()` returning the existing private `PublicPathSet` instance, so
  the chain and `isPublic(...)` cannot drift apart. `asAntPatterns()` kept (still used by
  `PublicPathsTest`/`PublicPathsFilterChainParityTest` from `TASK-BE-570`).
- ×5 `…/OAuth2ResourceServerConfig.java` — `jwtDecoder()`/`jwtTokenValidator()` now built via
  `ResourceServerChainAssembler.jwtDecoder(...)`; private `parseCsv` deleted; tenant policy extracted to
  a private `tenantGate()` (still no `.allowSuperAdminWildcard()`, still `.trustEntitledDomains()`).
- ×5 `…/SecurityConfig.java` — generic tail via `statelessJwtChain(...)`; `outbound` uses
  `.authorizeRules(...)` for its 5 role gates; `admin` keeps its `RoleHierarchy` bean and the dead
  expression handler. `jwtAuthenticationConverter()` (D1 territory) untouched in all 5.
- ×5 `…/WmsTenantGatePolicyTest.java` — additive `jwkSetUri` wiring only, no assertion changed.
- ×5 **new** `…/SecurityChainAssemblyParityTest.java` — real-filter-chain behaviour parity per service.

---

# Definition of Done

- [x] `TASK-MONO-500` confirmed landed (builder exists in `libs/java-security-servlet`) before
      implementation started — source file read in full first; API shape recorded under AC-0
- [x] Implementation completed (all 5 wms servlet services adopt the builder)
- [x] `WmsTenantGatePolicyTest` (super-admin-wildcard refusal) green in all 5 services, unweakened —
      only additive `jwkSetUri` test wiring; zero assertion edits
- [x] `admin-service`'s `RoleHierarchy`/entitlement-trust behavior confirmed unregressed — plus new
      real-chain coverage for both (see AC-3 and Finding 3)
- [x] Tests passing; per-service before/after counts recorded (AC-6 table); no test lost (+50, −0)
- [x] `gateway-service` confirmed untouched (reactive, out of scope) — `git status` shows no
      `gateway-service` path in the diff
- [x] Ready for review — **CI `Integration`/`E2E` (Testcontainers) lanes are the authoritative signal
      for this auth-path change and must be green before merge; local Windows Docker is not authoritative**
