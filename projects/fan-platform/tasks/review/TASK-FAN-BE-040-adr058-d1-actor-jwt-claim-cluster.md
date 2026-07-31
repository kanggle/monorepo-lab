# Task ID

TASK-FAN-BE-040

# Title

ADR-MONO-058 D1 (fan-platform only) — promote the actor/JWT-claim extraction cluster
(`ActorContextJwtAuthenticationConverter`, `ActorContextResolver`, `@CurrentActor` +
argument-resolver plumbing) to `libs/java-security-servlet`, keeping each service's
`ActorContext` role policy local

# Status

review

# Owner

backend

# Task Tags

- code
- api
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

Close fan-platform's share of `ADR-MONO-058 § D1` (ACCEPTED 2026-07-30).

`§ 6` rates D1 as **the highest-risk item in the whole ADR** ("D1 and D4 both touch
authentication/authorization-adjacent code across every servlet service in the fleet — the highest-risk
category of change in this repo", § 4) and prescribes both its timing and its shape:

> "D1 … highest risk, do these last per project once the team has practice from D3/D5/D6/D7/D8, and do
> them as **one task per project** (not one task per service) so a project's own `ActorContext` role-set
> policy is threaded through consistently in one pass rather than five separate PRs that could each
> thread it slightly differently."

fan-platform has since completed D2 (`TASK-FAN-BE-038`, done) and D5 (`TASK-FAN-BE-039`, done), so the
ADR's own sequencing precondition is satisfied. This task is **fan-platform only** — the same cluster
exists in finance / erp / scm / iam per the ADR's audit table (§ 1.1) and their adoption is separate
future work (`§ 6` forbids a cross-project mega-PR).

---

## Measured against the tree — what is actually duplicated (not the ADR's paraphrase)

fan-platform's four servlet services (`community-service`, `artist-service`, `membership-service`,
`notification-service`) each carry **four** actor classes. `gateway-service` is reactive and carries
none.

### The mechanism — verified byte-identical across all four, therefore promotable

| Class | Copies | Verified identical by |
|---|---|---|
| `ActorContextJwtAuthenticationConverter` (+ nested `ActorContextJwtAuthenticationToken`) | 4 | `git diff --no-index` over all three adjacent pairs → **only** the `package` line, the `ActorContext` import line, and javadoc prose differ. Zero difference in any statement. |
| `ActorContextResolver` (`currentOrThrow()`) | 4 | same — package/import/javadoc only |
| `CurrentActorArgumentResolver` | 4 | same — package/import only |
| `CurrentActor` (marker annotation) | 4 | same — package + the `{@link}` target in javadoc only |

The claim-lifting body is, in all four copies, character-for-character:

- `jwt.getSubject()`; blank/null → `IllegalStateException("sub claim is missing on the JWT")`
- `jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID)`; blank/null →
  `IllegalStateException("tenant_id claim is missing on the JWT")`
- roles: claim `roles`, else claim `role`, else `Collections.emptySet()`;
  a `Collection` → `String.valueOf(v)` per element; a `String` → `split("[,\\s]+")` with blank parts
  dropped; **any other claim type → empty set** (silently, no throw)
- authorities: one `SimpleGrantedAuthority("ROLE_" + role)` per extracted role
- token: `JwtAuthenticationToken` subclass, `super(jwt, authorities, actor.accountId())`,
  `setAuthenticated(true)`, `getPrincipal()` overridden to return the actor

**Finding: there is no behavioural divergence between the four services' mechanism code.** The ADR
allowed for one and this task was required to Hard-Stop on it; the diff evidence above says the shared
implementation is a faithful single version of all four, not a choice between them.

### The policy — stays per service, untouched

`ActorContext` is a **`record (String accountId, String tenantId, Set<String> roles)`** in all four,
and the four differ **only** in the convenience methods layered on top — i.e. exactly the
project-specific authorization policy `§ D1` says must not move:

| Service | Package | Convenience methods on `ActorContext` |
|---|---|---|
| `community-service` | `…community.application` | `hasRole`, `isOperator()` = `OPERATOR │ ADMIN │ SUPER_ADMIN │ FAN_OPERATOR`, `owns(authorAccountId)` |
| `artist-service` | `…artist.application` | `hasRole`, `isAdmin()` = `ADMIN │ SUPER_ADMIN │ OPERATOR │ FAN_OPERATOR` |
| `membership-service` | `…membership.application` | **none** (bare record) |
| `notification-service` | `…notification.application` | `hasRole` |

Related policy that also stays local: `artist-service`'s `SecurityConfig.ADMIN_ROLES` literal and its
`ActorGuard.requireAdmin`, `community-service`'s authorship/`ARTIST`-role gates, `membership-service`'s
`WorkloadIdentityAuthoritiesConverter` (`/internal/**`, a *different* converter — out of scope).

---

## Design decision — how the shared mechanism carries a per-service actor type

`ActorContext` is a Java **record**, and records are implicitly `final`: a shared record cannot be
subclassed to add `isOperator()`/`owns()`. Three shapes were considered:

| Shape | Verdict |
|---|---|
| **A. Shared `ActorClaims` value + a service-supplied factory (`ActorContextFactory<A>`), converter/resolver/arg-resolver generic in `A`** | **Chosen.** Each service's `ActorContext` record stays **byte-unchanged in its own `application` package**, keeps its own convenience methods, and gains **no** dependency on `libs/`. The shared side carries `Set<String> roles` generically and never learns a role name. |
| B. Shared `ActorPrincipal` interface that each service's record `implements` | Rejected — forces a `libs:java-security-servlet` (servlet/Spring-Security-bound) import into every service's **application layer**, which each `architecture.md` explicitly keeps framework-free ("keeps Spring Security types out of the application layer"). Buys nothing shape A does not. |
| C. Shared record with the fields, service composes it | Rejected — every use-case signature (`execute(ActorContext, …)`, `SubscribeCommand(actor, …)`) would have to change, turning a mechanism promotion into a fleet-wide call-site migration D1 did not ask for. |

Shape A means the **only** thing crossing the boundary is
`(accountId, tenantId, roles) -> ActorContext::new`.

**Deliberate non-hardening (recorded because the safe-looking choice is the wrong one).** The extracted
role set is exposed as `Collections.unmodifiableSet(hashSet)`, **not** `Set.copyOf(...)`.
`Set.copyOf(...).contains(null)` throws `NullPointerException`, whereas the `HashSet` every current copy
produces returns `false` — and `ActorContext.hasRole(role)` calls `roles.contains(role)` directly. Using
`Set.copyOf` would convert a would-be `false` into a thrown exception **on the auth path**. The
unmodifiable wrapper keeps `contains(null) == false` while removing the mutable-leak. `roles()` has zero
call sites today (`grep '\.roles()'` over `projects/fan-platform/apps` → 0), so the wrapper is
observationally inert.

---

# Scope

## In Scope

### 1. `libs/java-security-servlet` — new package `com.example.security.servlet.actor` (additive only)

- `ActorClaims` — `record (String accountId, String tenantId, Set<String> roles)`:
  - `public static ActorClaims from(Jwt jwt)` — the promoted claim-lifting mechanism, byte-equivalent to
    the four copies (including both `IllegalStateException` message strings verbatim, the
    `roles`-then-`role` precedence, the array **and** delimited-string forms, and the
    unrecognised-claim-type → empty-set fallback).
  - `public Collection<GrantedAuthority> authorities()` — the `ROLE_`-prefixing mechanism.
- `ActorContextFactory<A>` — `@FunctionalInterface`, `A create(String accountId, String tenantId,
  Set<String> roles)`. The single seam through which a service's own actor type is constructed.
- `ActorAuthenticationToken` — `final class extends JwtAuthenticationToken`, principal = the
  service-supplied actor object, `setAuthenticated(true)` in the ctor (`final` for the same
  `[this-escape]` reason the four copies document).
- `ActorContextJwtAuthenticationConverter<A> implements Converter<Jwt, AbstractAuthenticationToken>` —
  ctor takes the factory.
- `ActorContextResolver` — `public static <A> A currentOrThrow(Class<A> actorType)`; both
  `IllegalStateException` messages preserved verbatim (`"No authenticated actor in SecurityContext"`,
  `"Unexpected principal type: " + …`) because they are what the services' 422 `ILLEGAL_STATE` mapping
  keys on.
- `CurrentActor` — the promoted marker annotation (`PARAMETER`, `RUNTIME`, `@Documented`).
- `AbstractCurrentActorArgumentResolver<A> implements HandlerMethodArgumentResolver, WebMvcConfigurer` —
  ctor takes `Class<A>`; `supportsParameter` / `resolveArgument` / `addArgumentResolvers` bodies
  identical to the four copies. **No `@Component`** — `platform/shared-library-policy.md § No
  context-wide annotations`; each service opts in by declaring its own annotated subclass.
- New lib tests (below).

### 2. Each of the four services

- **Delete** its `ActorContextJwtAuthenticationConverter.java`, `ActorContextResolver.java`,
  `CurrentActor.java` (12 files).
- **Reduce** its `CurrentActorArgumentResolver.java` to a `@Component` subclass of the shared base
  binding its own `ActorContext.class` — same class name, same package, still a `@Component`
  `WebMvcConfigurer`, so `@WebMvcTest` slices keep registering it exactly as today.
- `SecurityConfig` — import the shared converter,
  `new ActorContextJwtAuthenticationConverter<>(ActorContext::new)`.
- Controllers — `@CurrentActor` import re-pointed to `com.example.security.servlet.actor.CurrentActor`
  (12 controller files; import line only, no signature change).
- `testsupport/SliceTestSecurityConfig` — same converter construction change (4 test files).
- **`ActorContext.java` is NOT touched in any of the four services.**
- No `build.gradle` change is needed: all four already declare
  `implementation project(':libs:java-security-servlet')` (ADR-MONO-049 § D5-6). The existing comment
  above that line is extended to name D1 as a second reason.

### 3. Specs (reconciliation, same PR)

- `artist-service/architecture.md` § Package Layout — its `adapter/in/web/security/` line lists
  `ActorContextJwtAuthenticationConverter` + `ActorContextResolver`, both of which this task deletes.
- All four `architecture.md` § Dependencies shared-libs lines — they omit `libs:java-security-servlet`
  although every one of the four `build.gradle` files has declared it since ADR-MONO-049. Pre-existing
  drift that this task's new, substantive use of the module makes actively misleading.

### 4. One atomic PR

Shared-path change + all four adaptations in **one** commit/PR (`CLAUDE.md § Cross-Project Changes`),
commit scope `refactor(lib)` — the governance shape `TASK-FAN-BE-038`/`039` established for this ADR in
this project.

## Out of Scope

- **Every other project.** finance / erp / scm / iam carry the same cluster (`ADR-MONO-058 § 1.1`);
  their D1 adoption is separate future work. `§ 6`: one project, one PR.
- **`ActorContext` itself and every role-set literal** — `isOperator()`, `isAdmin()`, `owns()`,
  `hasRole`, `FAN_OPERATOR`/`ADMIN`/`SUPER_ADMIN`/`OPERATOR`, `SecurityConfig.ADMIN_ROLES`,
  `ActorGuard.requireAdmin`. `§ D1` names these as the part that must stay per-service.
- **`WorkloadIdentityAuthoritiesConverter`** (membership `/internal/**`). A different converter with a
  different discriminator (`platform/security-rules.md § A verified token proves authentication, not
  authorization`, and `TASK-FAN-BE-029`'s positive-discriminator fix). Not part of D1; untouched.
- **`ADR-MONO-058 § D4`** (security-chain assembly: `ServiceLevelOAuth2Config` + the generic
  `SecurityConfig` tail, the 401/403 envelope writers, `extractOAuth2Error`). Separate, later task. This
  task touches exactly one line inside each `SecurityConfig` — the converter construction.
- `gateway-service` — reactive; `libs:java-security-servlet` must never reach a reactive classpath
  (its own `assertClasspathNeutrality` guard). It has zero actor classes. Untouched.
- Any wire-visible change: no new/changed HTTP status, error code, envelope field, or claim name.

---

# Acceptance Criteria

- [x] **AC-1 (mechanism promoted, duplicates deleted)** — `libs/java-security-servlet` gains the six
      classes above in `com.example.security.servlet.actor`, and a repo-wide grep shows
      `class ActorContextJwtAuthenticationConverter`, `class ActorContextResolver`, and
      `@interface CurrentActor` appear **zero** times under `projects/fan-platform/apps/*/src/main`.
      The `"ROLE_" +` prefixing literal and the `split("[,\\s]+")` role-normalisation literal each
      appear exactly **once** in the repo's fan-platform + lib surface (in the lib).
- [x] **AC-2 (policy did NOT move — the load-bearing § D1 boundary)** —
      `git diff -- "projects/fan-platform/apps/*/src/main/java/**/application/ActorContext.java"` is
      **empty**: all four `ActorContext` records are byte-unchanged, including every convenience method
      and every role literal. The shared package contains **no** role-name string
      (grep for `OPERATOR`, `ADMIN`, `FAN_`, `ARTIST` under
      `libs/java-security-servlet/src/main/java/com/example/security/servlet/actor` → 0 hits) and no
      import from any `projects/` module.
- [x] **AC-3 (claim-lifting parity, both claim forms, at integration level)** — for **each** of the four
      services, a test drives a request through the **real Spring Security filter chain** with a
      **really RSA-signed JWT** (the existing `SliceTestSecurityConfig` + `JwtTestHelper`, i.e. a real
      `NimbusJwtDecoder` + `AllowedIssuersValidator` + `TenantClaimValidator`) and asserts, for
      **array-form** `roles: ["…"]` **and** for **delimited-string-form** `role: "A B"` /
      `role: "A,B"` with no `roles` claim, that the granted authorities read off the live
      `SecurityContext` inside the controller call are exactly `ROLE_<each role>` and that
      `Authentication.getName()` equals the `sub`. A hand-constructed `ActorContext` or a hand-built
      `Jwt` does not satisfy this AC.
- [x] **AC-4 (`@CurrentActor` still binds the right actor, per service)** — the same per-service test
      captures the `ActorContext` the controller method actually received and asserts
      `accountId` == `sub`, `tenantId` == `fan-platform`, `roles` == the expected set — for both claim
      forms. This is the reachability proof for the argument-resolver plumbing: a subclass that failed
      to register would fail here, not compile-fail.
- [x] **AC-5 (insufficient credentials still rejected, per service)** — per service, through the same
      real chain: no bearer → **401**, and the service's real role/credential gate still rejects.
      fan-platform has **zero `@PreAuthorize`** (verified by grep over
      `projects/fan-platform/apps`), so the equivalent per service is:
      `artist-service` → `POST /api/artists` with a `FAN`-role token → **403 `FORBIDDEN`** (the
      `hasAnyRole(ADMIN_ROLES)` chain rule, i.e. a gate that consumes exactly the `ROLE_`-prefixed
      authorities this task moves); `membership-service` → `/internal/**` with an end-user token →
      **403** (`ROLE_INTERNAL` chain); `community-service` / `notification-service` → cross-tenant token
      → **403 `TENANT_FORBIDDEN`**. Existing tests already covering these must pass **unmodified**.
- [x] **AC-6 (mechanism unit-tested directly in the lib)** — `ActorClaims` / converter / resolver /
      argument-resolver each get their own lib test: `roles` array; `role` comma-delimited;
      `role` space-delimited; mixed separators; blank parts dropped; non-string collection elements via
      `String.valueOf`; `roles` takes precedence over `role`; neither claim → empty set; an
      unsupported claim type (e.g. a number) → empty set, no throw; missing/blank `sub` → the exact
      message; missing/blank `tenant_id` → the exact message; `ROLE_` prefixing; token principal /
      name / `isAuthenticated()`; resolver's two failure messages; `supportsParameter` true/false
      matrix; `addArgumentResolvers` self-registration; and
      **`roles.contains(null)` returns `false` rather than throwing** (the `Set.copyOf` trap recorded in
      the design decision).
- [x] **AC-7 (baseline parity — no test lost, no test weakened)** — before/after test counts recorded
      per module. Pre-change baseline, captured on this branch before any edit:
      community **130**, artist **129**, membership **130**, notification **104**,
      `libs:java-security-servlet` **35** — all with 0 failures / 0 errors / 0 skipped. No test may
      disappear or lose an assertion. All four `:check` tasks and `:libs:java-security-servlet:check`
      GREEN; CI's `Integration (fan-platform, Testcontainers)` lane GREEN is authoritative (local
      Windows Docker is not — `project_testcontainers_docker_desktop_blocker`).
- [x] **AC-8 (no other consumer of the module broken)** — `libs:java-security-servlet`'s existing
      classes (`TenantClaimEnforcer`, `PublicPathSet`) are **not modified**; the change is purely
      additive. The module's other consumers outside fan-platform (`scm-platform`, `erp-platform`,
      `finance-platform`) are verified by compiling a representative service of each, and
      `assertClasspathNeutrality` stays GREEN unmodified (no new dependency added — the new package
      uses only Spring Security / Spring Web types already on the module's classpath).
- [x] **AC-9 (guard mutation-check)** — at least one new per-service assertion is proven to **bite**:
      temporarily break the mechanism (e.g. drop the `ROLE_` prefix, or return the `role` claim without
      splitting) and record which tests go RED, then revert. A test that cannot be shown to fail is not
      evidence.
- [x] **AC-10 (no contract or wire change)** — `specs/contracts/http/*.md` need **no** edit; the PR body
      states explicitly that there is **no** observable behaviour delta (unlike D2, which had two). If
      implementation finds any wire-visible delta, stop — that is a contract change, not a promotion.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D1, § 4, § 5, § 6
  (ACCEPTED 2026-07-30)
- `docs/adr/ADR-MONO-049` — the precedent this extends (`TenantClaimValidator`/`TenantClaimEnforcer` →
  `libs/java-security` + `libs/java-security-servlet`), and the reactive/servlet split D1 must not cross
- `platform/shared-library-policy.md` § Decision Rule, § Dependency Rule, § Ownership Rule,
  § Forbidden → **No context-wide annotations**, § Review smell: imperative language toward consumers
- `platform/security-rules.md` § Authorization, § A verified token proves authentication, not
  authorization
- `platform/naming-conventions.md` § Java
- `platform/service-types/rest-api.md`
- `projects/fan-platform/specs/services/{community,artist,membership,notification}-service/architecture.md`
- `projects/fan-platform/specs/integration/iam-integration.md` (the claim shapes this converter lifts)
- `platform/contracts/jwt-standard-claims.md`
- `libs/java-security-servlet/build.gradle` — the module header stating the Dependency Rule this task
  must not violate
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`
  and `…/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md` — **prior art, read before
  starting.** They set this project's governance shape for an `ADR-MONO-058` sub-task: one atomic PR
  (lib + all four fan services), `refactor(lib)` scope, before/after test-count table, guard
  mutation-check, explicit statement of what did and did not change.
- `projects/fan-platform/tasks/done/TASK-FAN-BE-025-current-actor-authorship-channel-dedup.md` — the
  task that created `@CurrentActor` + `CurrentActorArgumentResolver` in all four services, and the
  source of the "failure path must stay byte-identical → same 422 `ILLEGAL_STATE`" constraint.

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/contracts/http/artist-api.md`
- `projects/fan-platform/specs/contracts/http/membership-api.md`

All **read-only inputs**. D1 is an internal mechanism promotion with no wire-visible surface. If
implementation finds it cannot preserve a documented status/shape, that is a genuine contract change:
stop, update the contract first per `CLAUDE.md`, and flag it in the PR body.

---

# Target Service

- `community-service`, `artist-service`, `membership-service`, `notification-service` (fan-platform)
- `libs/java-security-servlet` (shared — atomic, same PR)

---

# Architecture

Follow each target service's own `architecture.md`. Nothing moves between layers:

- `ActorContext` stays in each service's `application` package (Layered) / `application` package
  (artist, Hexagonal) — unchanged.
- `CurrentActorArgumentResolver` stays in its current package
  (`community`: `infrastructure.security`; `artist`: `adapter.in.web.security`;
  `membership`/`notification`: `presentation.security` — the four never agreed on this and this task
  does not change that either).
- `SecurityConfig` stays where it is.
- The shared classes sit in `com.example.security.servlet.actor`, a new sub-package of the module's
  existing `com.example.security.servlet` (`TenantClaimEnforcer`, `PublicPathSet` stay put).

---

# Implementation Notes

- Order of work that keeps the diff reviewable and the risk observable: (1) the six lib classes + their
  own tests, GREEN in isolation; (2) **one** service end-to-end (`notification-service` — smallest,
  bare-`hasRole` policy, single controller) including its auth-path test, to prove the argument-resolver
  subclass really registers under `@WebMvcTest` and the existing slice tests pass unmodified;
  (3) replicate to `community` → `artist` → `membership` (artist third because it is the only one with
  an HTTP-level role gate; membership last because of its second `/internal/**` chain); (4) specs.
- **The per-service auth-path assertion must read the live `SecurityContext`, not a fixture.** Stub the
  target use case with a Mockito `Answer` that, at invocation time (i.e. inside the filter chain),
  captures both the `ActorContext` argument and
  `SecurityContextHolder.getContext().getAuthentication()`. That single hook yields AC-3 (authorities +
  name) and AC-4 (bound actor) per service without inventing a test-only controller.
- Endpoints already suitable for that capture (all take `@CurrentActor` and pass it to a mockable use
  case): community `GET /api/community/feed` → `GetFeedUseCase.execute(actor, …)`; artist
  `GET /api/artists` → `SearchArtistDirectoryUseCase.search(query(actor, …))`; membership
  `GET /api/fan/memberships` → `ListMembershipsUseCase.execute(actor)`; notification
  `GET /api/fan/notifications` → `ListNotificationsUseCase.list(actor, …)`.
- The string-form `role` token needs no new fixture: community/artist `JwtTestHelper.sign(subject, role,
  tenantId, ttl, extraClaims)` already sets a bare `role` string claim, and
  membership/notification `JwtTestHelper.signEndUser(subject, tenantId, extraClaims)` accepts
  `Map.of("role", "FAN ARTIST")` directly. Do **not** widen a helper if an existing entry point works.
- `@WebMvcTest` includes `@Component` beans that implement `WebMvcConfigurer` /
  `HandlerMethodArgumentResolver` via its type-exclude filter — which is exactly why the per-service
  `CurrentActorArgumentResolver` must remain an annotated `@Component` class and must **not** become a
  `@Bean` in a `@Configuration` (a `@Configuration` is not picked up by the slice, and every slice test
  would silently lose `@CurrentActor` binding).
- Keep both `IllegalStateException` messages verbatim. `TASK-FAN-BE-025` pinned the failure path to
  those exact types/messages because `AbstractDomainExceptionHandler` maps `IllegalStateException` to
  **422 `ILLEGAL_STATE`**; a reworded message is harmless but a changed exception type is a status
  change.
- Do not add `equals`/`hashCode` to `ActorClaims` beyond the record default, and do not add any getter
  the four services do not call.

---

# Edge Cases

- **`roles.contains(null)`.** `ActorContext.hasRole(role)` delegates to `roles.contains(role)`. The
  extracted set must keep `HashSet`/unmodifiable-view semantics (`false`), **not** `Set.copyOf`
  semantics (`NullPointerException`). Pinned by AC-6.
- **`roles` present but not a collection or string** (e.g. a JSON number or object). All four copies
  silently yield an empty role set — no throw, no log. Preserve exactly; do not "improve" it into a
  rejection, which would turn a degraded-authorisation case into a 500 on the auth path.
- **`roles` present and empty (`[]`)** → empty set, zero authorities, `Authentication` still
  authenticated. Preserve.
- **`role` string with mixed separators** (`"FAN, ARTIST"`) → `split("[,\\s]+")` yields both, blank
  parts dropped. Preserve.
- **Both `roles` and `role` present** — `roles` wins (`raw == null` check only falls through when
  `roles` is absent). `JwtTestHelper.signFanToken` emits **both** in community/artist, so this
  precedence is already load-bearing in the existing suite.
- **`membership-service`'s second filter chain.** `/internal/**` (Order 1) uses
  `WorkloadIdentityAuthoritiesConverter` and a plain JWT principal — `ActorContextResolver` must keep
  throwing `"Unexpected principal type: …"` there, not silently return null, if an `@CurrentActor`
  parameter were ever reached from that chain.
- **`notification-service` is declared `event-consumer`** but exposes a REST inbox; its HTTP auth
  surface is in scope exactly like the three `rest-api` services (same call made by
  `TASK-FAN-BE-038`/`039`).
- **`gateway-service`** is reactive and must not gain this module. Verify it stays absent from its
  `build.gradle`.

---

# Failure Scenarios

- **Silently changing who is an operator.** Folding any of `isOperator()`/`isAdmin()`/`owns()` or a role
  literal into the shared type would move fan-platform's authorization policy into a library four other
  projects consume, and a later edit "for consistency" would then change fan-platform's authz without a
  fan-platform diff. `§ D1` forbids it; AC-2 is the mechanical check.
- **Green-wash by unit test.** Testing `ActorClaims.from(hand-built Jwt)` proves the extractor, not the
  chain. A converter that is correct but never wired, or an argument-resolver subclass that never
  registers, passes every unit test and 401s/500s in production. AC-3/AC-4 require the real filter chain
  and a really-signed token; AC-9 requires the assertions be shown to bite.
- **`Set.copyOf` "hardening".** Reviewer-plausible, and it converts `hasRole(null)` from `false` into a
  thrown `NullPointerException` on the auth path. Recorded in the design decision precisely because it
  looks like an improvement.
- **`@Component` on the shared argument resolver.** Would put a context-wide, self-installing bean into
  a shared library (`platform/shared-library-policy.md`) and register a resolver in every consumer of
  the module across four projects, including ones with no actor concept.
- **Scope creep into § D4.** `SecurityConfig` is full of adjacent duplication (401/403 writers,
  `extractOAuth2Error`, the whole chain assembly). It is D4's, not D1's. Touch exactly the converter
  construction line.
- **Assuming the four copies are identical.** They are — proven by `git diff --no-index` on every
  adjacent pair, recorded in the Goal section. Had any statement differed, this task's instruction was
  to stop and report rather than pick one.
- **Breaking a non-fan consumer of `libs:java-security-servlet`.** scm / erp / finance services consume
  the module. The change is additive (new package, no existing class touched), but AC-8 requires that be
  verified by compiling, not asserted from the diff.

---

# Test Requirements

- **Unit (lib)** — `ActorClaimsTest`, `ActorContextJwtAuthenticationConverterTest`,
  `ActorContextResolverTest`, `AbstractCurrentActorArgumentResolverTest`, covering the AC-6 matrix.
- **Integration-level (per service, ×4)** — a new auth-path slice test per service driving the **real**
  `SliceTestSecurityConfig` filter chain (real `NimbusJwtDecoder`, real issuer/tenant validators, real
  RSA-signed token) and asserting AC-3 + AC-4 + AC-5.
- **Regression net** — every existing slice test, `FanTenantGatePolicyTest`, `PublicPathsTest`,
  `GlobalExceptionHandler*Test`, `ActorContextTest` (community/artist) passes **unmodified**. The only
  test files edited are the four `SliceTestSecurityConfig` (converter construction) — no assertion in
  any existing test is changed.
- `./gradlew :libs:java-security-servlet:check` + the four fan `:check` tasks GREEN. CI
  `Integration (fan-platform, Testcontainers)` GREEN is authoritative.

---

# Verification Record

## Test counts (local, Docker-free `:check` / `:test`)

| module | before | after | delta |
|---|---|---|---|
| `community-service` | 130 | 138 | +8 |
| `artist-service` | 129 | 137 | +8 |
| `membership-service` | 130 | 139 | +9 |
| `notification-service` | 104 | 111 | +7 |
| `libs:java-security-servlet` | 35 | 77 | +42 |

0 failures / 0 errors / 0 skipped in every module, before and after, confirmed by re-aggregating the
JUnit XML (`tests=`/`skipped=`/`failures=`/`errors=` summed over `build/test-results/test/*.xml`) and by
a final `--rerun-tasks` pass over all five suites. Baseline matches `TASK-FAN-BE-039`'s recorded "after"
counts exactly, confirming the tree was in the expected post-D5 state before D1 started.

**No test was removed, renamed, or weakened.** The delta is entirely new files: one
`ActorContextAuthPathSliceTest` per service (8/8/9/7 cases) plus four new lib test classes
(`ActorClaimsTest`, `ActorContextJwtAuthenticationConverterTest`, `ActorContextResolverTest`,
`AbstractCurrentActorArgumentResolverTest`). The only pre-existing test files edited are the four
`testsupport/SliceTestSecurityConfig` — two lines each (import + converter construction), zero
assertions touched.

## AC-1 — mechanism promoted, duplicates deleted

`grep -E 'class ActorContextJwtAuthenticationConverter|class ActorContextResolver|@interface CurrentActor'`
over `projects/fan-platform/apps` → **0 hits**. 12 files deleted (3 per service). The `"ROLE_"` prefix
literal and the `[,\s]+` role-split literal now each exist in exactly one place, `ActorClaims`.

## AC-2 — policy did NOT move (the load-bearing § D1 boundary)

- `git diff HEAD --stat -- "projects/fan-platform/apps/*/src/main/java/**/application/ActorContext.java"`
  → **empty**. All four `ActorContext` records are byte-unchanged: community's `isOperator()`/`owns()`,
  artist's `isAdmin()`, notification's `hasRole`, membership's bare record, and every role literal
  (`OPERATOR`/`ADMIN`/`SUPER_ADMIN`/`FAN_OPERATOR`) stay exactly where they were.
- `grep -E 'FAN|ARTIST|ADMIN|OPERATOR|SUPER_|com\.example\.fanplatform'` over
  `libs/java-security-servlet/src/main/java/com/example/security/servlet/actor` → **0 hits**. The first
  draft of the javadoc used `"FAN"`/`"ARTIST"` as claim-shape examples and named
  `isAdmin()`/`isOperator()` in prose; both were rewritten to synthetic placeholders so the shared
  package names no project's roles even in a comment.
- `artist-service`'s `SecurityConfig.ADMIN_ROLES`, `ActorGuard.requireAdmin`, and
  `membership-service`'s `WorkloadIdentityAuthoritiesConverter` are untouched.

## AC-3 / AC-4 / AC-5 — integration-level auth verification, per service

Each service gained an `ActorContextAuthPathSliceTest` driving its **real** Resource Server filter chain
(the existing `SliceTestSecurityConfig`: a real `NimbusJwtDecoder` over a locally-generated RSA keypair,
the real `AllowedIssuersValidator`, the real `TenantClaimValidator`, and — notification — the real
`TenantClaimEnforcer` taken from the production `ServiceLevelOAuth2Config`) with a **really RSA-signed**
JWT. Nothing is hand-constructed: no hand-built `Jwt`, no hand-built `ActorContext`.

The authorities are read off the **live `SecurityContext` at controller-invocation time** (a Mockito
`Answer` on the target use case captures both `SecurityContextHolder.getContext().getAuthentication()`
and the `ActorContext` the `@CurrentActor` parameter actually received), so the assertion is about the
running chain rather than about a fixture.

| service | endpoint driven | claim forms asserted | rejection paths asserted |
|---|---|---|---|
| `community-service` | `GET /api/community/feed` | `roles:[…]`, `role:"A B"`, `role:"A,B"`, no claim | no token → 401 `UNAUTHORIZED`; cross-tenant → 403 `TENANT_FORBIDDEN` |
| `artist-service` | `GET /api/artists` + `POST /api/artists` | same four | **insufficient role → 403 `FORBIDDEN`** (`hasAnyRole(ADMIN_ROLES)`); ADMIN token passes the gate; no token → 401; cross-tenant → 403 |
| `membership-service` | `GET /api/fan/memberships` + `/internal/**` | same four | end-user token on `/internal/**` → 403; no token on `/internal/**` → 401; no token → 401; cross-tenant → 403 |
| `notification-service` | `GET /api/fan/notifications` | same four | no token → 401; cross-tenant → 403 |

Each also asserts `Authentication.getName() == sub`, `accountId`/`tenantId`/`roles` on the bound actor,
and that the bound actor is the **service's own** `ActorContext` type. community and artist additionally
assert their own policy still reads off it (`isOperator()`/`owns()` and `isAdmin()` respectively,
including the `FAN_OPERATOR` case) — the § D1 Ownership-Rule boundary, exercised rather than asserted in
prose.

**`@PreAuthorize` — measured, not assumed.** `grep PreAuthorize` over `projects/fan-platform/apps`
returns **0 hits**: fan-platform has none. The AC's intent (a role-gated endpoint still rejects an
under-privileged caller) is satisfied by each service's actual gate, listed above. artist-service's
`hasAnyRole(ADMIN_ROLES)` chain rule is the one that literally consumes the `ROLE_`-prefixed authorities
this task moved.

## AC-9 — guard mutation-check (the new assertions were verified to bite)

Two independent mutations of the promoted mechanism, each reverted after measuring:

1. `ROLE_AUTHORITY_PREFIX` `"ROLE_"` → `"MUTATED_"`:
   **artist-service 14 failed / 137** (the 4 new auth-path cases *plus* 10 pre-existing
   `ArtistControllerSliceTest`/`ArtistGroupControllerSliceTest`/`FandomControllerSliceTest` admin-route
   cases), **community-service 4 failed / 138**.
2. delimited-string role splitting removed (`role: "A B"` kept as one opaque role):
   **lib 5 failed / 77**, **notification 2 failed / 111**, **membership 2 failed / 139**.

Reverted both; full suites back to GREEN. The guards bite a real regression in the promoted mechanism,
and mutation 1 shows the pre-existing artist suite was already a live net for the `ROLE_` prefix.

## Divergence finding (the Hard-Stop question the task was required to answer)

**None.** All four services' mechanism copies are byte-identical modulo package, import and javadoc
prose — verified with `git diff --no-index` over every adjacent pair before any edit:

- community ↔ artist converter: **6 lines** (3 `package`/`import`, 3 javadoc).
- community ↔ membership converter: package + import + two javadoc paragraphs.
- membership ↔ notification converter: package + import + one javadoc sentence.
- community ↔ notification argument resolver: package + import only.
- resolvers and `@CurrentActor` annotations: package + import + `{@link}` target only.

Zero statements differ in claim lifting, role normalisation, authority prefixing, token construction, or
the two `IllegalStateException` messages. The shared implementation is a faithful single version of all
four, not a choice between them — so no Hard Stop was raised and nothing was papered over.

## Design hazards found and handled during implementation

- **`Set.copyOf` would have changed auth behaviour.** `ActorContext.hasRole(role)` calls
  `roles.contains(role)`; `Set.copyOf(...).contains(null)` throws `NullPointerException` where the
  `HashSet` every copy produced returns `false`. The shared extractor therefore returns
  `Collections.unmodifiableSet(hashSet)` — immutable to callers, still null-tolerant. Pinned by
  `ActorClaimsTest.containsNullIsFalseNotThrow` and `roleSetIsUnmodifiable`.
- **Shared `@Component` would have violated the shared-library policy.**
  `AbstractCurrentActorArgumentResolver` carries no Spring stereotype; each service keeps its own
  four-line `@Component` subclass. That is also what keeps it visible inside `@WebMvcTest` (which
  registers `WebMvcConfigurer`/`HandlerMethodArgumentResolver` components but not plain
  `@Configuration` beans) — a `@Bean` would have silently dropped `@CurrentActor` binding in every
  slice test.
- **Test-context cache collision — measured, not theorised.** The first version of the notification
  auth-path test declared the same `@WebMvcTest` configuration as
  `NotificationInboxControllerSliceTest`, so both shared one cached `ApplicationContext` whose
  `JwtDecoder` was built from whichever class's `JwtTestHelper` keypair happened to be in the static
  field first — **turning 4 pre-existing green tests red**. Fixed with a distinct
  `@TestPropertySource` cache key in all four new test classes, with the reason recorded in each file.

## Cross-project (shared-lib) blast radius

- `libs/java-security-servlet` change is **purely additive**: one new package
  (`com.example.security.servlet.actor`, 6 classes) + 4 new test classes. `TenantClaimEnforcer` and
  `PublicPathSet` are **not modified**; no dependency was added to the module's `build.gradle`.
- Every other consumer, enumerated from the tree
  (`git grep -l 'libs:java-security-servlet' -- '*/build.gradle'`) rather than assumed: `erp-platform`
  (approval / masterdata / notification / read-model), `finance-platform` (account / ledger),
  `scm-platform` (demand-planning / inventory-visibility / logistics / procurement) — **all 10
  `compileTestJava` GREEN**. `libs:java-gateway` and `libs:java-security` mention the module only in
  comments/assertions, not as dependencies; `libs:java-gateway:compileTestJava` is GREEN.
- `assertClasspathNeutrality` (java-security-servlet, 50 artefacts, none reactive),
  `assertClasspathNeutrality` (java-security, 23 artefacts) and `assertNoServletOnReactiveEdge`
  (java-gateway, 94 artefacts, none servlet-bound) all OK, unmodified.
- `gateway-service` (reactive) untouched — it has no actor classes and does not depend on the module.

## Observable behaviour deltas

**None.** No status code, error code, envelope field, claim name, exception type or exception message
changed. Claim lifting, role normalisation, `ROLE_` prefixing, the token's principal/name/authenticated
state and both `IllegalStateException` messages are byte-equivalent to the deleted copies. Unlike
`TASK-FAN-BE-038` (D2), this task ships **zero** intentional behaviour changes — which is why the
verification leans on mutation-checking rather than on new-behaviour assertions.

## CI

`Integration (fan-platform, Testcontainers)` on the PR is authoritative for the integration lane; local
Windows Docker is not (`project_testcontainers_docker_desktop_blocker`).

---

# Definition of Done

- [x] Implementation completed (6 lib classes + 4 service adoptions + 12 deletions, one atomic PR)
- [x] Tests passing; per-module before/after counts recorded; no test lost or weakened
- [x] All four `ActorContext` records verified byte-unchanged (diff, not assertion)
- [x] Shared package verified free of role literals and of `projects/` imports
- [x] Auth-path verification done at integration level (real chain, real JWT), both claim forms, per
      service; guard mutation-check recorded
- [x] Other `libs:java-security-servlet` consumers (scm / erp / finance) verified compiling
- [x] Contracts unchanged (verified); no observable behaviour delta stated in the PR body
- [x] Specs reconciled (`artist` package layout + 4 × shared-libs line)
- [x] Ready for review
