# Task ID

TASK-MONO-500

# Title

ADR-MONO-058 D4 — promote security-chain assembly (`ServiceLevelOAuth2Config` + generic `SecurityConfig` tail) to `libs/java-security-servlet`

# Status

done

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D4 found the `NimbusJwtDecoder` + `AllowedIssuersValidator`/`TenantClaimValidator` chain-assembly wiring near-byte-identical across every servlet service examined (scm, erp, wms, fan — ~17 copies). Promote the assembly mechanism — not the per-service exempt-path data or property keys — into `libs/java-security-servlet` as a builder/factory the service configures, so per-project adoption tasks (filed separately, referencing this task) have a canonical class to adopt against.

---

# Scope

## In Scope

- A builder/factory type in `libs/java-security-servlet` (package `com.example.security.servlet`, alongside the existing `PublicPathSet`/`actor` package landed by `TASK-FAN-BE-039`/`040`) that assembles: `NimbusJwtDecoder` construction, the `AllowedIssuersValidator`/`TenantClaimValidator` validator chain (both already shared per `ADR-MONO-049`), and the generic (non-domain) tail of a servlet `SecurityConfig` — filter chain wiring for public-vs-authenticated paths, stateless session policy, CSRF-disabled-for-API posture (verify each existing copy actually shares this posture before assuming it — do not silently change any adopting service's session/CSRF behavior).
- The service supplies: its own issuer allow-list, its own tenant-claim policy parameters, its own `PublicPathSet` instance (from D5), and its own property keys (`application.yml` binding stays per-service).
- Must be **opt-in** — a builder the service explicitly invokes from its own `@Configuration` class, never a component-scanned/auto-configured bean (`platform/shared-library-policy.md § No context-wide annotations`, and the ADR's own explicit constraint in § 2 D4).
- A unit test suite for the builder itself (chain assembly correctness, not a specific project's policy).

## Out of Scope

- Per-project adoption (erp/scm/wms/fan each get their own task in their own `tasks/ready/`, filed alongside this one, each referencing this task's ID as a prerequisite/선행).
- Any change to `AllowedIssuersValidator`/`TenantClaimValidator` themselves (already shared, `ADR-MONO-049`).
- D1 (actor/JWT-claim cluster) — already promoted (`TASK-FAN-BE-040`) and lives in `libs/java-security-servlet/.../actor/`; this task's builder should compose with it but not re-implement it.
- D5 (`PublicPathSet`) — already promoted (`TASK-FAN-BE-039` or `038`); consumed here as an input, not re-built.

---

# Acceptance Criteria

- [x] New builder/factory class exists in `libs/java-security-servlet`, unit-tested, framework-neutral wording (no project names in class/method names or Javadoc — `HARDSTOP-03`).
- [x] Builder is documented (Javadoc or a short guide) as opt-in — explicitly states it is NOT an auto-configuration.
- [x] `libs/java-security-servlet`'s own test suite passes (`./gradlew :libs:java-security-servlet:test`).
- [x] No existing service is modified by this task — this is a promotion-only task; adoption is separate.
- [ ] This task's own `tasks/ready → done` move happens only after being picked up and merged; until then it stays `ready`.
      **Deviation, recorded rather than silently satisfied.** The root lifecycle
      (`tasks/INDEX.md` § PR Separation Rule) requires the **impl PR** to carry the file
      `ready/ → in-progress/ → review/`, and only the post-merge **close chore** to carry
      `review/ → done/`. This AC, read literally, asks for a `ready → done` move that the root
      lifecycle does not have. The AC's intent — *do not close this before it is merged* — is
      honoured: the file is in `review/`, not `done/`. The box stays unchecked until the close
      chore actually moves it.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (N/A for this shared-library-only task; treat `libs/` per `platform/shared-library-policy.md`'s Decision/Ownership Rule), then `rules/common.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D4, § 6 item 7
- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` (precedent: `AllowedIssuersValidator`/`TenantClaimValidator` consolidation this builder composes with)
- `platform/shared-library-policy.md` (Decision Rule, Ownership Rule, § No context-wide annotations)
- `tasks/done/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the tracking task this splits from)

---

# Related Contracts

None — internal library API, no wire-format or event contract.

---

# Target Service

- `libs/java-security-servlet` (shared library, servlet-only per `ADR-MONO-048 § D1`'s reactive/servlet split — do not let this leak into `libs/java-gateway`).

---

# Architecture

N/A — library module, no service architecture declaration.

---

# Implementation Notes

- Before designing the builder's shape, read the actual current `SecurityConfig`/`ServiceLevelOAuth2Config` in at least 2-3 of the 4 confirmed-duplicate projects (scm, erp, wms, fan) to verify the "near-byte-identical" claim still holds (the audit is from 2026-07-29; code may have moved) and to avoid designing an API that doesn't fit the real variance (e.g. some services may have picked up extra filters since the audit).
- `ADR-MONO-058 § 4` flags D4 as one of the two highest-risk decisions (auth-path, every servlet service in the fleet) — the promotion itself is lower-risk (no service adopts it yet), but design the builder's test coverage as if a wrong default here will be inherited by every adopter.

---

# Edge Cases

- If the 4 confirmed-duplicate projects have already diverged meaningfully since the 2026-07-29 audit (e.g. one added a service-specific filter), do not force a single shape — document the divergence and either widen the builder's configuration surface or note in this task's completion notes which project(s) may need a follow-up.

---

# Failure Scenarios

- Making the builder a component-scanned auto-configuration would violate `shared-library-policy.md § No context-wide annotations` and silently change every adopting service's security posture on a version bump — Hard Stop if attempted.
- Baking one project's exempt-path list or property-key naming into the builder as a default would violate the Ownership Rule (policy vs mechanism) — keep those as constructor/builder parameters supplied by the adopting service.

---

# Test Requirements

- Unit tests for the builder covering: JWT decoder construction, validator chain assembly, public-vs-authenticated path routing given an injected `PublicPathSet`, and that no bean is auto-registered without explicit invocation.

---

# Verification Record

## What landed

One new class, `com.example.security.servlet.ResourceServerChainAssembler` (final, private constructor,
zero annotations), in `libs/java-security-servlet` — the same package as the already-landed
`PublicPathSet` (D5) and `TenantClaimEnforcer`, and a sibling of the `…servlet.actor` package (D1).
It carries two nested builders, reached through two static factories:

| entry point | replaces | the service still supplies |
|---|---|---|
| `jwtDecoder(jwkSetUri)` → `JwtDecoderBuilder` | the `jwtDecoder()` / `jwtTokenValidator()` `@Bean` pair | the JWKS URI, the issuer allow-list (`allowedIssuers(Collection)` or `allowedIssuersCsv(String)`), and every extra validator via `validator(...)` — including its own already-built tenant-claim policy object |
| `statelessJwtChain(http)` → `FilterChainBuilder` | the generic `SecurityConfig` tail | `publicPaths(PublicPathSet)`, `authenticated(String...)`, `authorizeRules(Customizer)`, `securityMatcher(String...)`, `jwtDecoder(JwtDecoder)`, `jwtAuthenticationConverter(...)`, `authenticationEntryPoint(...)`, `accessDeniedHandler(...)`, `httpCustomizer(Customizer)`, and the `anyRequest()` tail |

`JwtDecoderBuilder` terminates in `buildValidator()` (the validator chain alone, because several copies
expose it as its own bean) and `build()` (a `NimbusJwtDecoder` with that chain installed).
`FilterChainBuilder` terminates in `build()` → `SecurityFilterChain`.

No `build.gradle` change: the class uses only types already on the module's classpath.
`assertClasspathNeutrality` is unchanged and GREEN at the same **50 artefacts, none reactive** that
`TASK-FAN-BE-040` recorded.

## Two things are unconditional; one is an explicit switch — measured, not assumed

The task required verifying the posture in the existing copies before assuming it. Counted across the
tree, over the servlet resource-server chains in the four named projects plus `finance-platform`:

- **CSRF disabled + `SessionCreationPolicy.STATELESS`: unanimous, 19/19.** Applied unconditionally, and
  named in the factory (`statelessJwtChain`) so it cannot be acquired without reading it. The one
  fleet chain that keeps CSRF and an `IF_REQUIRED` session is a browser **login form**, not a resource
  server, and is out of this builder's scope — noted in the Javadoc.
- **The `anyRequest()` tail: split, 14 `denyAll()` vs 5 `authenticated()`.** A split axis gets an
  explicit switch, and the switch defaults to the **closed** answer (`anyRequestDenied()`), not the
  majority one — the same rule `TenantClaimEnforcer` records for its own switches.

## Divergence finding — the Edge Case's Hard question, answered

**The ADR's "near-byte-identical" holds for the decoder half and does NOT hold for the chain half.**
Measured against current code, not carried forward from the 2026-07-29 audit:

**Decoder + validator chain — 15 copies, genuinely near-identical.** `erp`(4), `scm`(4),
`finance`(2), `wms`(5). All fifteen build `NimbusJwtDecoder.withJwkSetUri(uri).build()` +
`setJwtValidator(new DelegatingOAuth2TokenValidator<>([JwtTimestampValidator, AllowedIssuersValidator,
TenantClaimValidator, JwtValidators.createDefault()]))`, and all fifteen carry a private, identical
`parseCsv`. The only differences are property-key names, the two policy switches, and
`@ConditionalOnMissingBean(JwtDecoder.class)` (present in 15, absent in `fan`'s four, which declare
named non-`@Primary` decoders instead). `fan`'s four add a *second* decoder shape —
`JwtValidators.createDefaultWithIssuer(issuer)` with no tenant pin — which the builder covers as a
one-issuer allow-list with no supplied validators.

**`SecurityConfig` tail — one project is a different shape.** `erp`/`scm`/`fan`/`finance` share the
tail nearly verbatim (`PublicPaths.EXACT`/`PREFIXES` → `permitAll`, one `.authenticated()` pattern,
`.anyRequest().denyAll()`). **`wms` does not**, and this is post-audit reality rather than drift the
ADR predicted:

| what `wms` does differently | consequence for this builder |
|---|---|
| public paths are a literal `String[]`, no `PublicPaths` class | its D4 adoption needs D5 (`PublicPathSet`) first — sequencing note for its adoption task |
| `.anyRequest().authenticated()` | covered by `anyRequestAuthenticated()` |
| extra `.cors/.httpBasic/.formLogin/.logout` disables | covered by `httpCustomizer(...)` |
| `@EnableMethodSecurity`, `@ConditionalOnWebApplication(SERVLET)`, injected `ObjectMapper` | class-level and stay at the wiring site; the builder never sees them |
| a plain `JwtAuthenticationConverter`, not D1's `ActorContextJwtAuthenticationConverter` | the converter is a parameter; the builder has no opinion |
| **no `TenantClaimEnforcer` bean at all** | out of D4's scope, but its adoption task should not assume one exists |

Two further shapes the four projects hold that a single-shape builder would have broken:
`fan/membership` runs **two ordered chains** with a `securityMatcher("/internal/**")` scope and a
per-chain decoder (→ `securityMatcher`, `jwtDecoder`), and `fan/artist` registers **eight
method-scoped role-gated matchers before** its authenticated rules (→ `authorizeRules`, whose fixed
position in the sequence is what makes that reachable).

Per the Edge Case's instruction the configuration surface was **widened** rather than one shape forced,
and the two follow-ups above are recorded for the per-project adoption tasks rather than solved here.

## Rule order is fixed, and that is a behaviour decision

`authorizeHttpRequests` is first-match-wins, so the builder fixes the sequence
**publicPaths → authorizeRules → authenticated → anyRequest** (narrowest first). Letting callers
interleave would let a blanket `.authenticated("/api/**")` shadow a narrower role gate — which is
exactly the shape `fan/artist` has. `RuleOrder.roleGateWinsOverTheBroaderAuthenticatedRule` is the
assertion that pins it, and the mutation check below shows it goes red when the order is swapped.

Likewise the **validator** order is fixed (`timestamp → issuer → supplied → defaults`), including the
**duplicate timestamp check** every copy carries. `DelegatingOAuth2TokenValidator` accumulates errors
instead of short-circuiting, and each copy's `extractOAuth2Error` picks the *first* non-`invalid_token`
error to decide 401-vs-403 — so re-ordering or "tidying" this list silently re-labels responses.

## Test counts (`./gradlew :libs:java-security-servlet:test`)

| module | before | after | delta |
|---|---|---|---|
| `libs:java-security-servlet` | 77 | 113 | +36 |

0 failures / 0 errors / **0 skipped**, before and after, re-aggregated from
`build/test-results/test/*.xml` (`tests=`/`skipped=`/`failures=`/`errors=` summed over all 26 files),
and confirmed by a final `:check --rerun-tasks` pass. The 77 baseline matches `TASK-FAN-BE-040`'s
recorded "after" count exactly, confirming the module was in the expected post-D1 state before D4
started. **No existing test was touched** — the delta is entirely three new files.

| new test class | cases | covers |
|---|---|---|
| `ResourceServerChainAssemblerJwtDecoderTest` | 15 | chain membership + order (timestamp first, issuer second, supplied validators in call order, defaults last, duplicate timestamp preserved); CSV split/trim/drop-blanks; the closed-by-default construction contract (missing allow-list → `IllegalStateException`, empty collection / blank CSV → `IllegalArgumentException`, blank & null `jwkSetUri`, null arguments); `NimbusJwtDecoder` built lazily and per-call |
| `ResourceServerChainAssemblerFilterChainTest` | 16 | **routing through a real Spring Security filter chain** (`@SpringBootTest` + `MockMvc`, two ordered chains, a stub `JwtDecoder`): public exact + public prefix anonymous → 200; authenticated path anonymous / undecodable token → 401 via the supplied entry point; valid token → 200; role gate ahead of the blanket rule → 403 via the supplied handler; `denyAll` tail rejects even a valid token (403) where an `authenticated` tail would admit it; `anyRequestAuthenticated()` chain admits/rejects; `securityMatcher` really scopes; CSRF disabled (POST with no token succeeds); STATELESS (no session created) |
| `ResourceServerChainAssemblerOptInTest` | 5 | the class and both builders carry **zero** `org.springframework` annotations; no `@Bean` factory method anywhere; the module ships neither `META-INF/spring/…AutoConfiguration.imports` nor `META-INF/spring.factories` — with a **positive control** first, asserting the classpath query does find the `.imports` file in a dependency jar, so the zero-finding is evidence rather than a broken lookup |

## Guard mutation-check — the new assertions were shown to bite

Three independent mutations of the promoted mechanism, each reverted after measuring:

| mutation | result |
|---|---|
| `authorizeRules` moved to run **after** the blanket `authenticated(...)` patterns, **and** the `anyRequest()` default flipped to `authenticated()` | **3 failed / 113** — `roleGateWinsOverTheBroaderAuthenticatedRule` (403 → 200), `unlistedPathDeniesAValidToken` (403 → 200), `authenticatedTailAdmitsAnyValidToken` |
| explicit `JwtTimestampValidator` dropped from the chain | **3 failed / 113** — `timestampRunsFirst`, `defaultsRunLastAndTimestampIsDuplicated`, `chainWithoutSuppliedValidators` |

Reverted; full suite back to GREEN. Both mutations are the exact "reviewer-plausible tidy-up" shape —
one reorders rules that look order-independent, the other removes a validator that looks duplicated —
which is why the guards for them had to be shown failing rather than assumed.

## `HARDSTOP-03` / Ownership Rule — verified, not asserted

`grep -irE 'scm|erp|wms|fan|iam|ecommerce|finance|console|com\.example\.(scmplatform|erp|fanplatform|finance)|com\.wms|com\.kanggle'` over the new main + test files returns **only** the substring
`MatcherPatterns` (a false positive on `erp` inside `securityMatcherPatterns`). The class contains no
path string, no property key, no tenant id, no issuer, no role name and no `projects/` import; every
fixture path, role and token in the tests is synthetic. Both policy switches the fleet split on
(`allowSuperAdminWildcard` / `trustEntitledDomains`) stay in `TenantClaimValidator` at the wiring site
and are never named here — the builder only takes the already-built validator object.

## Opt-in posture — enforced, not documented

`shared-library-policy.md` § *No context-wide annotations* and § *Review smell: imperative language
toward consumers* both bear on this. The class installs **nothing**: no stereotype, no
`@AutoConfiguration`, no registration file, so there is no "every consumer MUST …" obligation to
offload in the first place. Its preconditions (issuer allow-list required, `jwkSetUri` non-blank) are
the legitimate kind under that section's asymmetry test — they attach to an explicit opt-in call and
are **enforced at that call**, not left as a sentence for a consumer to remember.

## Blast radius

Purely additive: one new class + three new test classes in `libs/java-security-servlet`.
`PublicPathSet`, `TenantClaimEnforcer` and the whole `…servlet.actor` package are **not modified**; no
existing test file is touched; `build.gradle` is unchanged. `git diff --stat` for `projects/` is
**empty** — no service adopts the builder in this PR, per the task's promotion-only scope.

---

# Definition of Done

- [x] Builder/factory landed in `libs/java-security-servlet` with passing unit tests
- [x] Javadoc states opt-in posture explicitly (and an `OptInTest` enforces it)
- [x] No adopting service touched by this PR
- [ ] Task moved to `done`, referencing the per-project adoption tasks it unblocks — close chore, after merge
