# Task ID

TASK-FAN-BE-044

# Title

ADR-MONO-058 D4 (fan-platform only) — adopt the shared security-chain assembly builder
(`libs/java-security-servlet`) in place of each service's `ServiceLevelOAuth2Config` +
generic `SecurityConfig` tail

# Status

review

# Owner

backend

# Task Tags

- code
- authz
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

# Goal

Close fan-platform's share of `ADR-MONO-058 § D4` (ACCEPTED 2026-07-30) — one of the two highest-risk items
in the whole ADR (`§ 4`: "D1 and D4 both touch authentication/authorization-adjacent code across every
servlet service in the fleet — the highest-risk category of change in this repo").

`§ D4`: "The `NimbusJwtDecoder` + `AllowedIssuersValidator`/`TenantClaimValidator` chain-assembly wiring (not
the validators themselves — already shared per `ADR-MONO-049`) is near-byte-identical across every servlet
service examined. Promote the assembly as a builder/factory the service configures with its own property
keys and exempt-path predicate — **not** as an auto-configuration that installs itself unconditionally…
this must remain an opt-in call, not a component-scanned bean."

**Prerequisite (선행): `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` is
**ready but NOT YET LANDED** as of this task's filing.** Do not start this task until `TASK-MONO-500`'s
Status reads `done` (verify by reading the file directly — an ADR/task status can go stale). This task only
covers **adoption** of the builder `TASK-MONO-500` lands in `libs/java-security-servlet` — it does not
design or implement the builder itself.

---

## Deliberate deviation from `ADR-MONO-058 § 6`'s suggested D1+D4 bundling — recorded, not silent

`§ 6` item 7 suggests: "D1 (actor/JWT-claim cluster) and D4 (security-chain assembly) … do them as **one
task per project** (not one task per service) so a project's own `ActorContext` role-set policy is threaded
through consistently in one pass rather than five separate PRs that could each thread it slightly
differently."

fan-platform has **already** implemented D1 as its own separate, completed task
(`TASK-FAN-BE-040-adr058-d1-actor-jwt-claim-cluster.md`, done) — filed and merged *before* D4's promotion
task (`TASK-MONO-500`) existed. `TASK-FAN-BE-040`'s own Goal section explicitly noted this project had
already cleared its D2/D5 sequencing precondition and proceeded with D1 standalone. Re-bundling D4 with an
already-shipped D1 would mean re-opening D1's authentication-converter wiring inside `SecurityConfig` a
second time for no reason — the risk `§ 6`'s bundling advice exists to reduce (threading role-set policy
consistently through one combined pass) does not apply here because there is no unthreaded D1 work left to
combine with. **This task is filed standalone.** `TASK-FAN-BE-040`'s own Out of Scope section already
anticipated this: "`ADR-MONO-058 § D4`… Separate, later task. This task touches exactly one line inside each
`SecurityConfig` — the converter construction," confirming D1's landed shape leaves D4's assembly work
untouched and ready to build on independently.

---

## Measured against the tree — what is actually duplicated (not the ADR's paraphrase)

Grep confirms all **four** fan-platform servlet services carry both files:

| Service | `ServiceLevelOAuth2Config.java` | `SecurityConfig.java` |
|---|---|---|
| `community-service` | `infrastructure/security/` | `infrastructure/security/` |
| `artist-service` | `config/` | `config/` |
| `membership-service` | `infrastructure/security/` | `infrastructure/security/` |
| `notification-service` | `infrastructure/security/` | `infrastructure/security/` |

`gateway-service` is reactive (Spring Cloud Gateway) and carries neither file — out of scope, matching
`TASK-FAN-BE-040`'s and `TASK-FAN-BE-039`'s precedent of excluding it for the same reason.

`membership-service` additionally carries a **second** security filter chain (Order 1, `/internal/**`,
`WorkloadIdentityAuthoritiesConverter` — a different converter, D1-adjacent but not D1, already out of scope
per `TASK-FAN-BE-040`). This task's builder adoption must not disturb that second chain's ordering or its
own converter.

---

# Scope

## In Scope

- Before writing any adoption code, read `TASK-MONO-500`'s **actual landed** builder API (constructor/
  builder parameters, method names) — do not assume the shape described in `TASK-MONO-500`'s own ready-state
  task file is exactly what shipped; that task explicitly reserves the right to adjust its design against
  what the real 4-project duplicate-code comparison shows.
- For each of the four services (`community`, `artist`, `membership`, `notification`), replace the
  `NimbusJwtDecoder` construction + `AllowedIssuersValidator`/`TenantClaimValidator` chain-assembly wiring +
  the generic (non-domain) tail of `SecurityConfig` (stateless session policy, CSRF-disabled-for-API
  posture, public-vs-authenticated path routing) with an explicit, opt-in call to the shared builder from
  `libs/java-security-servlet`, configured with:
  - the service's own issuer allow-list (existing property keys, unchanged),
  - the service's own tenant-claim policy parameters (existing property keys, unchanged),
  - the service's own `PublicPathSet` instance (already promoted, `TASK-FAN-BE-039`/D5 — consumed here as
    an input, not re-built),
  - the service's own `ActorContextJwtAuthenticationConverter` construction (already promoted,
    `TASK-FAN-BE-040`/D1 — composed with, not re-implemented).
- **Before touching each service's session/CSRF posture, verify by reading the current code that all four
  actually share the same posture** (`TASK-MONO-500`'s own Implementation Notes flags this: "verify each
  existing copy actually shares this posture before assuming it — do not silently change any adopting
  service's session/CSRF behavior"). If any of the four has quietly diverged since the ADR's 2026-07-29
  audit, do not force it into the shared shape — document the divergence and either keep that one service on
  a documented exception or flag it as a pre-adoption defect to fix first.
- `membership-service`: the shared builder wires only the primary (Order 2, `/api/fan/**`) chain. The second
  (Order 1, `/internal/**`, `WorkloadIdentityAuthoritiesConverter`) chain stays hand-assembled, unchanged —
  verify the ordering between the two chains is preserved exactly (the builder's chain must not
  accidentally become the default/catch-all in a way that changes which chain matches `/internal/**`).
- One atomic PR across all four services (`CLAUDE.md § Cross-Project Changes` — this task's changes are all
  within fan-platform, but touch the highest-risk shared surface in the project, so keep the review unit at
  "project," matching `TASK-FAN-BE-040`'s precedent for D1).

## Out of Scope

- `TASK-MONO-500` itself (the promotion) — must be `done` before this task starts.
- **Every other project.** scm/erp/wms carry the same D4 pattern per `§ 1.1`'s audit table; their adoption
  is separate future work in their own projects' `tasks/ready/`.
- **`ADR-MONO-058 § D1`** — already done (`TASK-FAN-BE-040`). This task composes with the landed
  `ActorContextJwtAuthenticationConverter`, it does not re-touch its construction beyond passing it into the
  new builder call.
- **`AllowedIssuersValidator`/`TenantClaimValidator` themselves** — already shared, `ADR-MONO-049`, untouched
  by this task.
- **`membership-service`'s `/internal/**` chain and `WorkloadIdentityAuthoritiesConverter`** — a separate,
  intentionally-hand-assembled chain; not part of D4's scope per `TASK-FAN-BE-040`'s own precedent.
- Any change to the 401/403 error-envelope writers or `extractOAuth2Error` — those are adjacent duplication
  inside the same `SecurityConfig` files but explicitly not part of D4's promoted surface (D2's territory,
  already done per `TASK-FAN-BE-038`, or genuinely untouched scaffolding outside any D-item).
- `gateway-service` — reactive; `libs:java-security-servlet` must never reach a reactive classpath.

---

# Acceptance Criteria

- [x] `TASK-MONO-500`'s Status confirmed `done` before this task's implementation starts (verify by reading
      the file, not by inference).
      → Read `tasks/done/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` directly: `# Status`
      reads `done`. The landed API was then read in full from source
      (`libs/java-security-servlet/.../ResourceServerChainAssembler.java`) rather than from that task's
      prose — it exposes `jwtDecoder(jwkSetUri) → JwtDecoderBuilder` and
      `statelessJwtChain(http) → FilterChainBuilder`.
- [x] All four services' `SecurityConfig`/`ServiceLevelOAuth2Config` chain-assembly code (decoder
      construction, validator chain, generic filter-chain tail) is replaced by an explicit call to the
      shared builder — **not** a component-scanned/auto-configured bean. Repo-wide grep confirms no
      `@Configuration` class in `libs/java-security-servlet` self-registers a security filter chain bean —
      the call site is always in the service's own `@Configuration`.
      → `grep -rnE "@(AutoConfiguration|Configuration|Component|Bean|EnableWebSecurity)"
      libs/java-security-servlet/src/main/java` returns **Javadoc/comment matches only** — zero real
      annotations; `libs/java-security-servlet/src/main/resources/META-INF/spring/` does not exist, so
      there is no `…AutoConfiguration.imports` entry either. Post-adoption grep for the hand-rolled
      idioms (`withJwkSetUri|parseCsv|DelegatingOAuth2TokenValidator|SessionCreationPolicy|
      AbstractHttpConfigurer`) across the four services' `src/main/java` leaves **only** the
      intentionally out-of-scope `membership-service` `/internal/**` chain + its `internalJwtDecoder`
      (plus three Javadoc mentions).
- [x] Each service supplies its own issuer allow-list, tenant-claim parameters, `PublicPathSet`, and
      `ActorContextJwtAuthenticationConverter` via the builder's parameters — no service policy value is
      hardcoded into the shared builder (verified by reading `TASK-MONO-500`'s landed class for absence of
      any fan-platform-specific literal, which should already be guaranteed by that task's own AC, but
      re-verify from the consuming side).
      → Re-verified from the consuming side: the assembler holds no path, property key, tenant id, issuer
      or role literal, and every one of those arrives as a call argument at each service's own wiring
      site. Property keys are **unchanged** in all four (`spring.security.oauth2.resourceserver.jwt.
      jwk-set-uri`, `fanplatform.oauth2.allowed-issuers`, `fanplatform.oauth2.required-tenant-id`, plus
      membership's `fanplatform.internal.jwt.*`) — no `application.yml` was edited in this task.
- [x] Session/CSRF posture (stateless session, CSRF disabled for the API surface) verified **unchanged**
      per service, by a test asserting the same behavior before/after — this is the specific "do not
      silently change any adopting service's security posture" guarantee the promotion task's own Failure
      Scenarios calls out.
      → **Measured, not assumed.** All four were read before any edit and all four were already identical:
      CSRF disabled + `SessionCreationPolicy.STATELESS` + `anyRequest().denyAll()`. **No divergence found**
      — so nothing had to be forced or excepted. The new per-service `SecurityChainAssemblySliceTest`
      asserts both axes against the **production** chain and was run GREEN **before** the refactor and
      again after, unchanged: CSRF via a state-changing request with no CSRF token reaching the dispatcher
      (405/422/400 — statuses `CsrfFilter` cannot produce), session via `getSession(false) == null` +
      no `JSESSIONID` `Set-Cookie`.
      ⚠️ **Disclosed limit**: the usual mutation-calibration (weaken the chain, watch the probe go red) was
      **not performed** — the auto-mode classifier hard-blocked authoring a weakened chain both in
      production code and in a test-only opposite-posture `@TestConfiguration`. That boundary was
      respected rather than worked around. Two of the three probes carry an internal control instead
      (documented in each test's Javadoc): the public-path test shows this same chain answers an
      admitted-but-unmapped request **404**, so the **403** on an unlisted path is an authorization
      decision and not an unmapped-path artefact; and `CsrfFilter` can only ever emit 403, never
      405/422/400. The session probe has **no** internal control and is a pure characterization pin.
- [x] `membership-service`'s second (`/internal/**`) filter chain is verified unchanged: its own tests
      (`InternalAuthIntegrationTest`) pass unmodified, and chain ordering (which chain matches
      `/internal/**` vs `/api/fan/**`) is confirmed unchanged by an explicit test, not assumed from
      "it still compiles."
      → The `@Order(1)` chain, `WorkloadIdentityAuthoritiesConverter` and `internalJwtDecoder` are
      **byte-unchanged** (`git diff` on `SecurityConfig.java` touches only the `@Order(2)` method body,
      the imports and the class Javadoc). `InternalAuthIntegrationTest` is **unmodified** (Testcontainers
      lane; CI authoritative). New explicit ordering test: `SecurityChainAssemblySliceTest.Ordering`, 4
      cases, which identifies the answering chain **by message, not by status** — the two chains write
      different 401/403 bodies (`"Missing or invalid internal credentials"` vs `"Authentication
      required"`; `"Workload identity required for /internal/**"` vs `"Access denied"`), so a chain swap
      could not pass silently the way a status-only assertion would. Includes the **positive** half (a
      real workload token clears `ROLE_INTERNAL` and reaches the dispatcher), so the internal chain is
      shown reachable and not merely refusing everything.
- [x] For each of the four services, at the real-filter-chain integration level (real `NimbusJwtDecoder`,
      real RSA-signed JWT — the `SliceTestSecurityConfig` pattern `TASK-FAN-BE-040` established): no bearer
      → 401; cross-tenant token → 403 `TENANT_FORBIDDEN`; disallowed issuer → 401; public path (from
      `PublicPathSet`) → reachable without a token. All byte-identical to pre-adoption behavior.
      → New `SecurityChainAssemblySliceTest` per service (community 9, artist 12, membership 14,
      notification 9 cases), each written and run **GREEN against the pre-refactor tree first**, then
      re-run unchanged after — which is what makes "byte-identical" a measurement rather than a claim.
      **Deliberately not** built on `SliceTestSecurityConfig`: that fixture declares its *own* chain
      resembling production's, so a D4 refactor could have changed `SecurityConfig` arbitrarily and every
      test built on it would still pass. These import the **real** `SecurityConfig` bean and the **real**
      `ServiceLevelOAuth2Config` validator chain; the only substitution is the signature-verification
      source (local RSA test keypair instead of a live JWKS endpoint). Real RSA-signed JWTs throughout;
      no hand-built `Jwt`/`Authentication`.
- [x] If any of the four services is found to have diverged in session/CSRF posture from the others since
      the 2026-07-29 audit, that divergence is explicitly documented in the PR body (per `TASK-MONO-500`'s
      Edge Cases guidance) rather than silently forced into conformity or silently ignored.
      → **No session/CSRF divergence exists** — measured, all four identical (see above). Two *other*
      pre-existing divergences were found and **preserved rather than tidied**, and are stated in the PR
      body: (1) `artist-service`'s access-denied code is `FORBIDDEN` where its three siblings write
      `PERMISSION_DENIED`; (2) `community`/`artist` resolve a single `JwtDecoder` bean from the context
      while `membership`/`notification` pin theirs explicitly — so `.jwtDecoder(...)` is called only in
      the latter two, exactly as before.
- [x] No wire-visible change: no new/changed HTTP status, error code, envelope field, or claim name.
      → No contract file touched. Every status/code/message asserted by the new tests was first observed
      on the pre-refactor tree. No `application.yml`, no `build.gradle`, no controller, no DTO changed.
- [x] Test-count parity recorded per service (before/after); no test lost or weakened.
      → community 140 → 149 (+9) · artist 137 → 149 (+12) · membership 139 → 153 (+14) ·
      notification 111 → 120 (+9). **0 failures / 0 errors / 0 skipped on both sides.** Purely additive:
      no test deleted, renamed or weakened. Four existing test files were touched additively only —
      each service's `JwtTestHelper` gained a `signForeignIssuer(...)` helper (new `FOREIGN_ISSUER`
      constant; existing signatures preserved by delegation), and each `FanTenantGatePolicyTest`'s
      `wiredConfig()` gained **one line** setting the JWKS-URI field. That one line is a genuine finding,
      recorded rather than buried: the shared builder is entered through `jwtDecoder(jwkSetUri)` and
      rejects a null URI, so the *validator* chain can no longer be built from a config that left that
      field unset — whereas the hand-written chain never read it. Production is unaffected (the `@Value`
      has no default; an unset property already fails the context). No assertion in those suites reads
      the field, and none was changed.
- [x] `./gradlew :community-service:check :artist-service:check :membership-service:check
      :notification-service:check` GREEN. CI `Integration (fan-platform, Testcontainers)` lane GREEN —
      authoritative (local Windows Docker is not, `project_testcontainers_docker_desktop_blocker`).
      → All four `:check` GREEN locally (Gradle paths are `:projects:fan-platform:apps:<service>:check`).
      CI `Integration (fan-platform, Testcontainers)` is **pending on the PR** and remains the
      authoritative signal — local Windows Docker is not, so `integrationTest` (incl.
      `InternalAuthIntegrationTest`) was deliberately not treated as locally decisive.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D4, § 4, § 6 item 7
  (ACCEPTED 2026-07-30)
- `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` — **prerequisite, must be
  `done` before this task starts.** Read its Scope/Implementation Notes for the exact builder shape and the
  session/CSRF-posture-verification instruction it hands to the adopting task (this task).
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the tracking task this splits from)
- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` (precedent:
  `AllowedIssuersValidator`/`TenantClaimValidator`, which the promoted builder composes with, unchanged)
- `platform/shared-library-policy.md` § Decision Rule, § Dependency Rule, § Ownership Rule,
  § No context-wide annotations
- `platform/security-rules.md`
- `platform/contracts/jwt-standard-claims.md`
- `projects/fan-platform/specs/services/{community,artist,membership,notification}-service/architecture.md`
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/fan-platform/tasks/done/TASK-FAN-BE-040-adr058-d1-actor-jwt-claim-cluster.md` — **prior art,
  read before starting.** Established this project's `SliceTestSecurityConfig` real-filter-chain test
  pattern this task's AC reuses, and is the reason this task is filed standalone rather than bundled with
  D1 (see the Goal section's deviation note).
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`,
  `…/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md` — prior art for this project's
  `ADR-MONO-058` adoption-task governance shape (before/after test-count table, guard mutation-check,
  explicit statement of observable-behaviour deltas)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-029-membership-workload-identity-positive-discriminator.md`
  — the `/internal/**` chain's own security history; this task must not regress it

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md`, `artist-api.md`, `membership-api.md` — all
  **read-only inputs**. D4 is an internal chain-assembly promotion with no wire-visible surface. If
  implementation finds it cannot preserve a documented status/shape (401 vs 403 boundaries, public-path
  reachability), that is a genuine contract question — stop and update the contract first per `CLAUDE.md`.

---

# Target Service

- `community-service`, `artist-service`, `membership-service`, `notification-service` (fan-platform)
- Consumes `libs/java-security-servlet` (already landed by `TASK-MONO-500`; not modified by this task)

---

# Architecture

Follow each target service's own `architecture.md`. `SecurityConfig`/`ServiceLevelOAuth2Config` stay in
their current packages and class names in every service (`community`/`membership`/`notification`:
`infrastructure.security`; `artist`: `config` — the four never agreed on this and this task does not change
that either, matching `TASK-FAN-BE-040`'s precedent for the same non-uniformity). Only the internal
chain-assembly implementation changes from hand-wired to a call into the shared builder.

---

# Implementation Notes

- Order of work that keeps the diff reviewable and the risk observable, mirroring `TASK-FAN-BE-040`'s
  proven sequencing: (1) read the shared builder's actual landed API; (2) **one** service end-to-end
  (`notification-service` — smallest, single chain, no `/internal/**` complication) including its
  session/CSRF-posture regression test, to prove the builder slots in without behavior drift; (3) replicate
  to `community` → `artist`; (4) `membership-service` last, specifically because of its second chain — extra
  care needed to confirm chain ordering survives.
- Reuse `TASK-FAN-BE-040`'s `SliceTestSecurityConfig` + `JwtTestHelper` real-filter-chain test
  infrastructure for the AC's integration-level verification — do not hand-build a `Jwt`/`Authentication` to
  prove chain-assembly behavior; that would prove the test fixture, not the real chain.
- If the promoted builder's constructor requires the `ActorContextJwtAuthenticationConverter` as a
  parameter (composing D1 and D4, per `TASK-MONO-500`'s own Scope: "The service supplies… its own
  `ActorContextFactory`-composed converter"), confirm the exact composition point against what
  `TASK-FAN-BE-040` actually landed (`new ActorContextJwtAuthenticationConverter<>(ActorContext::new)`) —
  this is the one place D1 and D4's landed shapes must interlock correctly even though they were
  implemented in separate tasks.

---

# Edge Cases

- **Session/CSRF posture divergence, if found.** Per `TASK-MONO-500`'s own Edge Cases: "If the 4 confirmed-
  duplicate projects have already diverged meaningfully since the 2026-07-29 audit… do not force a single
  shape — document the divergence." This applies equally on the adoption side: if fan-platform's own four
  services have quietly diverged from each other (not just from the other projects), the same rule applies
  within this task.
- **`membership-service`'s dual-chain ordering.** Spring Security evaluates `SecurityFilterChain` beans by
  `@Order`; the builder must not introduce a chain whose implicit precedence collides with the existing
  Order 1 `/internal/**` chain. Verify the `@Order` annotation (or bean-registration order) on the adopted
  primary chain is unchanged.
- **`notification-service` declared `event-consumer` but exposing a REST inbox.** Its HTTP auth surface is
  in scope exactly like the three `rest-api` services (same call `TASK-FAN-BE-038`/`039`/`040` already
  made for this service).
- **`gateway-service` must stay absent from this promotion entirely** — verify its `build.gradle` gains no
  new dependency and its reactive security config is untouched.

---

# Failure Scenarios

- **Silently changing session/CSRF posture for one service.** If the shared builder hardcodes one posture
  and any of the four services actually needs a different one (undetected because assumed rather than
  verified), this task would ship an unannounced security-behavior change across the fleet's
  highest-risk surface. The AC's explicit before/after posture test exists to catch this.
- **Component-scanned auto-configuration.** Would violate `shared-library-policy.md § No context-wide
  annotations` and silently change every consumer's security posture on a version bump, across projects far
  beyond fan-platform. Hard Stop if the adopted call site is anything other than an explicit invocation from
  each service's own `@Configuration` class.
- **Breaking `membership-service`'s `/internal/**` chain ordering.** A subtle `@Order` collision could cause
  the wrong chain to match `/internal/**` requests — potentially either locking out legitimate workload-
  identity calls (denial of service to `community-service`'s S2S call, regressing `TASK-FAN-BE-010`/`029`/
  `030`) or, worse, letting the primary chain's less-restrictive matching accidentally cover `/internal/**`
  (an authorization bypass). This is the single highest-severity risk in this task; the explicit chain-
  ordering test is not optional.
- **Scope creep into `§ D2`'s territory.** The 401/403 error-envelope writers and `extractOAuth2Error` live
  in the same files this task edits but are D2's (already done, `TASK-FAN-BE-038`) or genuinely separate
  scaffolding — touch only the chain-assembly/decoder/validator/session/CSRF surface named in D4.
- **Assuming, not verifying, that D1's landed converter composes cleanly.** `TASK-FAN-BE-040` and
  `TASK-MONO-500` were implemented independently, months apart in this task-filing sequence, by different
  work. Their interlock point (the converter passed into the new builder) must be verified by an actual
  compiling, passing integration test — not assumed from reading both tasks' prose.

---

# Test Requirements

- **Integration-level (per service, ×4)** — reusing `TASK-FAN-BE-040`'s real-filter-chain
  `SliceTestSecurityConfig` pattern: no bearer → 401; disallowed issuer → 401; cross-tenant → 403; public
  path (via `PublicPathSet`) reachable without a token; session is stateless (no `JSESSIONID` cookie set);
  CSRF is disabled for the API surface (a state-changing request without a CSRF token still succeeds, as
  today).
- **`membership-service` dual-chain regression** — `InternalAuthIntegrationTest` passes unmodified;
  additional explicit assertion that `/internal/**` still resolves to the workload-identity chain and
  `/api/fan/**` still resolves to the adopted primary chain.
- **Regression net** — every existing slice/integration test involving authentication passes unmodified;
  the only files edited for chain assembly are the four `SecurityConfig`/`ServiceLevelOAuth2Config` pairs
  (plus any new posture-regression test files).
- Before/after test-count table for all four services, 0 failures/errors/skipped both sides.
- `./gradlew :community-service:check :artist-service:check :membership-service:check
  :notification-service:check` GREEN. CI `Integration (fan-platform, Testcontainers)` GREEN authoritative.

---

# Definition of Done

- [x] `TASK-MONO-500` confirmed `done` before starting
- [x] All four services' chain-assembly wiring replaced by explicit, opt-in calls to the shared builder
- [x] Session/CSRF posture verified unchanged per service (or divergence explicitly documented, not forced)
      — no divergence found; mutation-calibration blocked by the classifier and disclosed, not silently
      skipped
- [x] `membership-service`'s dual-chain ordering verified unchanged by explicit test (identified by
      message, not status; includes the positive workload-token path)
- [x] Real-filter-chain integration verification per service (401/403/public-path), byte-identical to
      pre-adoption behavior — each new suite run GREEN on the pre-refactor tree first
- [x] No wire-visible change stated in the PR body
- [x] Test-count parity recorded; four services' `:check` GREEN — CI Integration lane pending on the PR
      (authoritative)
- [x] Ready for review

---

# Implementation Record (2026-07-31)

## What changed, per service

| Service | `ServiceLevelOAuth2Config` | `SecurityConfig` | `PublicPaths` |
|---|---|---|---|
| `community` | `jwtDecoder()` + `jwtTokenValidator()` → private `decoderAssembly()` on the shared builder; `parseCsv` deleted | `filterChain(...)` → `statelessJwtChain(...)`; **no** `.jwtDecoder(...)` (single decoder bean, resolved from context — unchanged) | `MECHANISM` → public `AS_SET` |
| `artist` | same shape as community | `filterChain(...)` → `statelessJwtChain(...)`; all 11 role/method rules moved verbatim into `.authorizeRules(...)` in **original registration order** | `MECHANISM` → public `AS_SET` |
| `membership` | `endUserJwtDecoder()` + `endUserTokenValidator()` → private `endUserDecoderAssembly()`; **`internalJwtDecoder()` untouched** | `endUserFilterChain(...)` (`@Order(2)`) → `statelessJwtChain(...)` with `.jwtDecoder(endUserJwtDecoder)`; **`internalFilterChain(...)` (`@Order(1)`) byte-unchanged** | `MECHANISM` → public `AS_SET` |
| `notification` | `endUserJwtDecoder()` + `endUserTokenValidator()` → private `endUserDecoderAssembly()`; `parseCsv` deleted | `endUserFilterChain(...)` → `statelessJwtChain(...)` with `.jwtDecoder(endUserJwtDecoder)` | `MECHANISM` → public `AS_SET` |

`TenantClaimEnforcer` beans (D5/ADR-MONO-049) are untouched in all four — including the
`trustEntitledDomains()` switch that stays **off**, and membership's `/internal/**` exemption.

## D1 interlock (`TASK-FAN-BE-040`) — reused, not duplicated

Each chain's `new ActorContextJwtAuthenticationConverter<>(ActorContext::new)` expression was **moved
unchanged** from the old `.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(…)))`
call into the builder's `.jwtAuthenticationConverter(…)` parameter. No second converter was
constructed, no converter class was added, and nothing in the `…servlet.actor` package was touched.
The interlock is proved by a **passing** test, not by reading both tasks' prose: every `ActorContext`
assertion in the four pre-existing `ActorContextAuthPathSliceTest`s passes unmodified, and the new
suites drive the same converter through the *production* chain.

## `anyRequest()` tail — measured per service, not assumed

The ADR reports 14/19 `denyAll()` vs 5/19 `authenticated()` fleet-wide, so all four were read before
editing: **all four end `denyAll()`**. Each is now stated out loud via `.anyRequestDenied()` rather
than inherited from the builder's default, and each is pinned by a test asserting that a **valid**
token on an unlisted path is still 403 (under `authenticated()` the same request would be 404).

## Out of scope, confirmed untouched

`gateway-service` (reactive — `git diff` empty, no new dependency), `libs/` (`git status` clean —
`TASK-MONO-500` already landed it), every other project, the D2 error-envelope writers and
`extractOAuth2Error`, and the `AllowedIssuersValidator`/`TenantClaimValidator` classes themselves.
