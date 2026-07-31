# Task ID

TASK-ERP-BE-037

# Title

ADR-MONO-058 D1 + D4 (erp-platform, combined) — adopt the already-shared actor/JWT-claim
extraction cluster (`libs/java-security-servlet/.../actor`) in `approval-service` +
`masterdata-service`, and adopt the security-chain-assembly builder (`libs/java-security-servlet`,
landed by `TASK-MONO-500`) in all four servlet services, keeping each service's own
`ActorContext` role policy and `PublicPaths` data local

# Status

ready

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

Close erp-platform's share of `ADR-MONO-058` (ACCEPTED 2026-07-30) **§ D1** and **§ D4** — the two
decisions the ADR itself rates as the highest-risk in the whole record ("D1 and D4 both touch
authentication/authorization-adjacent code across every servlet service in the fleet", § 4) and
explicitly instructs to file as **one task per project, not one per service**, "so a project's own
`ActorContext` role-set policy is threaded through consistently in one pass" (§ 6 item 7).

- **D1** — `ActorContextResolver` / `ActorContextJwtAuthenticationConverter` / the `@CurrentActor`
  mechanism. Already promoted to `libs/java-security-servlet`'s
  `com.example.security.servlet.actor` package (fan-platform's `TASK-FAN-BE-040`) — this task is
  **adoption only**, no new library code.
- **D4** — the `NimbusJwtDecoder` + `AllowedIssuersValidator`/`TenantClaimValidator` chain-assembly
  wiring (`ServiceLevelOAuth2Config` + the generic tail of `SecurityConfig`). **Blocked on
  `TASK-MONO-500`** (`tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md`,
  not yet landed as of this task's filing) — that task promotes the builder this task adopts. Do
  not start D4's half of this task before `TASK-MONO-500` merges; D1's half has no such
  prerequisite and may start independently.

## Measured against the tree — what erp actually has (not the ADR's § 1.1 paraphrase)

erp-platform has five services: `gateway-service` (reactive, Spring Cloud Gateway — out of scope,
see below) and four servlet services — `approval-service`, `masterdata-service`,
`notification-service`, `read-model-service`.

**D1 — only two of the four servlet services carry the cluster.**
`grep -l ActorContextResolver` under `apps/*/src/main` finds it only in `approval-service` and
`masterdata-service` (`application/ActorContext.java` +
`infrastructure/security/{ActorContextResolver,ActorContextJwtAuthenticationConverter}.java` in
each). `notification-service` and `read-model-service` do **not** have this cluster — both
authorize purely off `ReadAuthorizationGate`/entitlement-trust reads directly from the JWT (per
`TASK-ERP-BE-029`'s documented read/write asymmetry finding), with no `ActorContext` principal
type. **D1 adoption in this task therefore touches only `approval-service` and
`masterdata-service`.**

`ActorContext` in both is `record(String actorId, String tenantId, Set<String> roles, Set<String>
dataScopeDepartmentIds, Set<String> entitledDomains)` — five components, not the three-component
`(accountId, tenantId, roles)` shape fan-platform's four services had. The extra two components
(`dataScopeDepartmentIds`, `entitledDomains`) are erp's own `org_scope`/entitlement-trust
authorization policy (`ADR-MONO-019 § D5`, `ADR-MONO-025`) — policy, not mechanism, and per § D1
must stay local. Confirm both records are byte-identical to each other (they appear to be from
inspection; verify with `git diff --no-index` before designing the adoption, per the fan-platform
precedent's own Hard-Stop-if-they-differ instruction) before assuming a single shared-factory shape
fits both.

**D4 — all four servlet services carry `ServiceLevelOAuth2Config`, and all four are verified
byte-identical** (module-level differences are javadoc-only). `git diff --no-index` across all six
adjacent pairs of the four copies shows the `jwtDecoder()` / `jwtTokenValidator()` /
`tenantClaimEnforcer()` bodies are character-for-character identical:
`AllowedIssuersValidator` + `TenantClaimValidator.forTenant(requiredTenantId)
.allowSuperAdminWildcard().trustEntitledDomains().build()` for the decoder chain, and
`TenantClaimEnforcer.forTenant(requiredTenantId).exempt(PublicPaths::isPublic)
.allowSuperAdminWildcard().trustEntitledDomains().build()` for the enforcer — including both
relaxations (`allowSuperAdminWildcard`, `trustEntitledDomains`) in every copy. `notification-service`
and `read-model-service`'s copies carry two extra javadoc paragraphs (documenting the decode-time
vs. filter-time dual-accept ordering) not present in `approval-service`/`masterdata-service`'s —
prose only, zero statement differs. **This is the strongest possible D4 adoption case**: no
per-service divergence to reconcile, unlike the ADR's general warning that some projects may have
drifted since the 2026-07-29 audit.

`gateway-service` is reactive (Spring Cloud Gateway, `OAuth2ResourceServerConfig` +
`GatewayIdentityConfig`) and has neither an `ActorContext` cluster nor a `ServiceLevelOAuth2Config`
— it already gets its equivalent from `libs/java-gateway` per the reactive/servlet split `§ D1`
and `§ D4` both name explicitly. **Out of scope, unchanged.**

---

# Scope

## In Scope

### D1 half — `approval-service` + `masterdata-service`

- Adopt `libs/java-security-servlet`'s `com.example.security.servlet.actor` package
  (`ActorClaims`, `ActorContextFactory<A>`, `ActorAuthenticationToken`,
  `ActorContextJwtAuthenticationConverter<A>`, `ActorContextResolver`, `CurrentActor`,
  `AbstractCurrentActorArgumentResolver<A>`) in both services, following the shape-A design
  (`ActorContextFactory<A>`, generic converter/resolver) `TASK-FAN-BE-040` established: each
  service's own `ActorContext` record (five components, including `dataScopeDepartmentIds` +
  `entitledDomains`) stays **byte-unchanged in its own `application` package**; only the mechanism
  classes (`ActorContextJwtAuthenticationConverter`, `ActorContextResolver`) are deleted and
  replaced with a construction call into the shared generic classes,
  `new ActorContextJwtAuthenticationConverter<>(ActorContext::new)`-shaped but adapted to the
  five-argument constructor (confirm the shared `ActorClaims`/`ActorContextFactory<A>` shape
  actually supports a five-component service record before assuming a direct drop-in — erp's
  `ActorContext` carries two fields fan-platform's never had, so the factory call site may need to
  source `dataScopeDepartmentIds`/`entitledDomains` from additional claims inside the factory
  lambda rather than the shared `ActorClaims` triple; if the shared `ActorClaims` truly cannot
  carry them, that is itself a finding to report, not to route around).
- If erp already has a `@CurrentActor`/`CurrentActorArgumentResolver` mechanism in either service
  matching the fan-platform shape, adopt the shared `AbstractCurrentActorArgumentResolver<A>` too;
  if erp instead resolves the actor exclusively via `ActorContextResolver.currentOrThrow()` inside
  use cases (no controller-parameter annotation), confirm which pattern is actually present before
  assuming the annotation mechanism applies — grep for `@CurrentActor` under
  `apps/{approval,masterdata}-service` first.
- `build.gradle` for both services — verify `implementation project(':libs:java-security-servlet')`
  is already declared (both already import `com.example.security.servlet.TenantClaimEnforcer` per
  `ADR-MONO-049`, so the dependency should already exist; confirm, do not assume).

### D4 half — all four servlet services (`approval`, `masterdata`, `notification`, `read-model`)

- **Prerequisite: `TASK-MONO-500` merged.** Verify by reading the task file's `Status` field
  directly, not by inference — do not start this half before confirming.
- Replace each service's own `ServiceLevelOAuth2Config` (the `jwtDecoder()` +
  `jwtTokenValidator()` + `tenantClaimEnforcer()` triad) with a call into `TASK-MONO-500`'s
  builder/factory, supplying erp's own property keys (`erpplatform.oauth2.allowed-issuers`,
  `erpplatform.oauth2.required-tenant-id`), erp's own `PublicPathSet`/`PublicPaths` instance (from
  this project's D5 task, `TASK-ERP-BE-040`, if that task has landed first — otherwise the
  service's still-local `PublicPaths::isPublic`; D4 and D5 are independent tasks and either order
  is valid, but the exempt-path source must be whichever is actually on the classpath at
  implementation time, verified, not assumed), and both erp relaxations
  (`allowSuperAdminWildcard()`, `trustEntitledDomains()`) as explicit builder calls — do not let
  the builder default them silently, since a service that later needs a *narrower* posture must be
  able to omit them.
- Because all four copies are verified byte-identical (see Goal), this should be a **mechanical,
  low-variance replacement** across all four — no per-service policy reconciliation expected. If
  implementation finds one of the four has drifted since this task was filed, stop and treat that
  as a finding, not something to route around silently.
- One atomic PR — the four service adaptations together (`CLAUDE.md § Cross-Project Changes` does
  not strictly apply here since no shared-path file changes in this task, `TASK-MONO-500` already
  landed it separately — but bundle the four services in one PR per this repo's task-series
  convention for a shared-shape change, avoiding four near-identical review passes).

## Out of Scope

- **`TASK-MONO-500` itself** (the D4 builder's promotion) — separate task, prerequisite, not
  re-decided here.
- **D1 for `notification-service` / `read-model-service`** — they do not have the cluster; nothing
  to adopt. If a future audit finds they should *gain* actor-context handling, that is new scope,
  not part of this ADR-058 adoption.
- **`ActorContext` itself and every role/scope literal** — `isOperator()`, `hasRole`,
  `hasScope`, `isPlatformScope()`, `isEntitledTo()`, `ERP_OPERATOR`/`ERP_ADMIN`/`SUPER_ADMIN`, the
  `dataScopeDepartmentIds`/`entitledDomains` fields and their construction logic. `§ D1` names
  these as the part that must stay per-service; `TASK-ERP-BE-008`/`019`/`029`/`031` built and fixed
  this exact policy and must not be touched here.
- **`gateway-service`** — reactive; `libs:java-security-servlet` must never reach a reactive
  classpath (`ADR-MONO-048 § D1`). Zero actor classes, no `ServiceLevelOAuth2Config`. Untouched.
- **D2 / D3 / D5** — separate tasks (`TASK-ERP-BE-038`, `-039`, `-040`).
- **`PublicPaths` data itself** (the actual exempt-path lists) — D5's job, not D4's; D4 only
  changes how the enforcer/decoder chain is *assembled*, not which paths are exempt.
- Any wire-visible change: no new/changed HTTP status, error code, claim name, or auth
  accept/reject verdict for any existing request shape.

---

# Acceptance Criteria

- [ ] **AC-1 (D1 mechanism promoted, duplicates deleted)** — `approval-service` and
      `masterdata-service` no longer declare their own `class ActorContextJwtAuthenticationConverter`
      or `class ActorContextResolver` (repo-wide grep under
      `projects/erp-platform/apps/{approval,masterdata}-service/src/main` → 0 hits for both class
      declarations); both construct the shared generic classes instead.
- [ ] **AC-2 (D1 policy did NOT move)** — `git diff -- "projects/erp-platform/apps/{approval,masterdata}-service/src/main/java/**/application/ActorContext.java"`
      is empty: both five-component records, every convenience method (`hasRole`, `hasScope`,
      `isPlatformScope`, `isOperator`, `isEntitledTo`) and every role/scope literal are
      byte-unchanged. No `projects/erp-platform` role/scope string appears anywhere under
      `libs/java-security-servlet/.../actor`.
- [ ] **AC-3 (D1 claim-lifting parity, integration level)** — for both services, a test drives a
      request through the real Spring Security filter chain with a really RSA-signed JWT and
      asserts the granted authorities and the resolved `ActorContext` (including
      `dataScopeDepartmentIds`/`entitledDomains`) match today's behavior for both the `roles`-array
      and delimited-`role`-string claim forms. A hand-built `Jwt` or hand-built `ActorContext` does
      not satisfy this AC.
- [ ] **AC-4 (D4 prerequisite gate honored)** — the PR implementing D4's half is not opened/merged
      before `TASK-MONO-500`'s Status reads `done`; this task's own tracking records the date that
      prerequisite was confirmed.
- [ ] **AC-5 (D4 adoption, all four services)** — `approval-service`, `masterdata-service`,
      `notification-service`, `read-model-service` all construct their JWT decoder + tenant-claim
      enforcer chain via `TASK-MONO-500`'s builder; each service's own
      `ServiceLevelOAuth2Config`-equivalent file either shrinks to a thin adapter around the
      builder or is deleted per whatever shape `TASK-MONO-500` actually lands.
- [ ] **AC-6 (D4 no verdict change)** — for each of the four services, existing tests covering: no
      bearer → 401; cross-tenant token → 403 `TENANT_FORBIDDEN`; `SUPER_ADMIN` wildcard-tenant
      READ admit (`TASK-ERP-BE-031`); entitlement-trust dual-accept READ admit — all pass
      **unmodified**. These are the load-bearing regression net for D4; a passing suite with an
      edited assertion does not satisfy this AC.
- [ ] **AC-7 (baseline parity)** — before/after test counts recorded per module (4 services +
      `libs:java-security-servlet`). No test may disappear or lose an assertion. All four `:check`
      tasks GREEN; CI's `Integration (erp-platform, Testcontainers)` lane GREEN is authoritative
      (local Windows Docker is not — `project_testcontainers_docker_desktop_blocker`).
- [ ] **AC-8 (guard mutation-check)** — at least one new D1 assertion and one new D4 assertion are
      each proven to bite (temporarily break the mechanism, e.g. drop the `ROLE_` prefix or drop
      `trustEntitledDomains()`, record which tests go RED, then revert).
- [ ] **AC-9 (no contract change)** — `specs/contracts/http/*.md` need no edit for either half; the
      PR body states explicitly that there is no observable behaviour delta.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`,
> then load `rules/common.md` plus `rules/domains/erp.md` and `rules/traits/{internal-system,
> transactional,audit-heavy}.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D1, § D4, § 4,
  § 5, § 6 (ACCEPTED 2026-07-30)
- `docs/adr/ADR-MONO-049` — precedent this extends (`TenantClaimValidator`/`TenantClaimEnforcer`
  consolidation), and the reactive/servlet split this task must not cross
- `docs/adr/ADR-MONO-019` § D5 (entitlement-trust `entitled_domains` dual-accept — the extra
  `ActorContext` field D1 must keep local) and `ADR-MONO-025` (`org_scope`/`dataScopeDepartmentIds`
  — likewise)
- `platform/shared-library-policy.md` § Decision Rule, § Dependency Rule, § Ownership Rule,
  § No context-wide annotations
- `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` — **prerequisite for
  the D4 half.** Confirm `Status: done` before starting that half.
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the tracking task this splits
  from.
- `projects/fan-platform/tasks/done/TASK-FAN-BE-040-adr058-d1-actor-jwt-claim-cluster.md` —
  **prior art, read before starting.** Sets the governance shape for a D1 adoption (shape-A
  `ActorContextFactory<A>` design, mutation-check discipline, real-chain integration testing) and
  is the closest analog even though erp's `ActorContext` has two extra fields fan-platform's never
  had — this task must resolve that difference explicitly, not silently drop it.
- `projects/erp-platform/tasks/done/TASK-ERP-BE-029-machine-token-data-scope-dead-fallback.md` —
  the `ActorContextJwtAuthenticationConverter`/data-scope wiring this task's mechanism promotion
  must preserve verbatim (the fixed fallback behavior).
- `projects/erp-platform/tasks/done/TASK-ERP-BE-031-superadmin-wildcard-read-authority-parity.md` —
  the `allowSuperAdminWildcard()` READ-visibility behavior AC-6 regression-guards.
- `projects/erp-platform/specs/services/{approval,masterdata,notification,read-model}-service/architecture.md`
  § Security / § Multi-tenancy

---

# Related Contracts

None — this is an internal mechanism/wiring promotion adoption with no wire-format change. If
implementation finds it cannot preserve an existing documented status/verdict, that is a genuine
contract or behavior change: stop and report rather than ship it silently.

---

# Target Service

- `approval-service`, `masterdata-service` (D1 half)
- `approval-service`, `masterdata-service`, `notification-service`, `read-model-service` (D4 half)
- Consumes `libs/java-security-servlet` (both the existing `actor` package for D1, and
  `TASK-MONO-500`'s new builder for D4) — no shared-library code is authored by this task itself.

---

# Architecture

Follow each target service's own `architecture.md` § Security / § Multi-tenancy. No layer boundary
moves: `ActorContext` stays in each service's `application` package; the security-chain assembly
stays in each service's `infrastructure.security` (approval, masterdata) / `config`
(notification, read-model) package, whichever it already occupies.

---

# Implementation Notes

- Do the D1 half and the D4 half as clearly separable commits within the PR(s) — D1 has no
  prerequisite and can start immediately; D4 is gated on `TASK-MONO-500`. If `TASK-MONO-500` is
  still not landed when D1 is ready to ship, ship D1 alone first rather than blocking on D4.
- Before writing any code, run `git diff --no-index` on `approval-service`'s and
  `masterdata-service`'s `ActorContext.java`/`ActorContextResolver.java`/
  `ActorContextJwtAuthenticationConverter.java` against each other (this task's Goal section
  asserts they are identical based on inspection, not a diff — verify before relying on it) and,
  separately, on all four services' `ServiceLevelOAuth2Config.java` (this task's Goal section
  *does* record a diff-based verification for D4 — re-confirm it still holds at implementation
  time, since code may have moved since this task was filed).
- For the D1 factory-construction question (does the shared `ActorClaims`/`ActorContextFactory<A>`
  support a five-component service record, or only the three-component
  `(accountId,tenantId,roles)` fan-platform used) — read the actual current
  `libs/java-security-servlet/src/main/java/com/example/security/servlet/actor/ActorClaims.java`
  and `ActorContextFactory.java` before designing the adoption. If the factory lambda's signature
  is `(String accountId, String tenantId, Set<String> roles) -> A`, erp's `ActorContext` factory
  will need to source `dataScopeDepartmentIds`/`entitledDomains` from the raw `Jwt` inside the
  factory closure (both fields already come off named claims per the existing converter code) —
  this is still policy composition at the call site, not a library change, and stays within scope.
- Order of work that keeps the diff reviewable: (1) D1 in `masterdata-service` first (has the
  richer `ActorContext`, includes both extra fields, forces the hardest design question early);
  (2) replicate to `approval-service`; (3) D4 (gated), applied to all four uniformly since they are
  verified identical.

---

# Edge Cases

- **`ActorContext`'s two extra fields.** `dataScopeDepartmentIds`/`entitledDomains` are erp-specific
  and must never appear, even as an example, inside `libs/java-security-servlet`'s shared package
  (`HARDSTOP-03`). If the shared `ActorContextFactory<A>` signature cannot carry them without a
  library change, report that as a finding and stop rather than force a workaround that leaks
  erp's policy fields into the shared factory contract.
- **`allowSuperAdminWildcard()` / `trustEntitledDomains()` must both survive D4's adoption in every
  one of the four services** — dropping either from any one service silently narrows READ
  visibility that `TASK-ERP-BE-031`/`TASK-ERP-BE-029` already fixed and tested; AC-6 is the guard.
- **`notification-service`/`read-model-service` have no `ActorContext`.** Do not introduce one as a
  side effect of the D4 half — D4 only touches the decoder/enforcer assembly, not the principal
  type each service uses.
- **`gateway-service` must gain neither the D1 actor package nor the D4 builder** on its classpath —
  verify its `build.gradle` stays absent of both after this task.

---

# Failure Scenarios

- **Silently changing who can read what.** Folding `isOperator()`/`isEntitledTo()`/any role or
  scope literal into the shared type, or dropping `allowSuperAdminWildcard()`/
  `trustEntitledDomains()` from any one of the four services' D4 adoption, would change erp's
  authorization surface as a side effect of a "pure" promotion adoption. AC-2/AC-6 are the
  mechanical checks; treat any failure there as a Hard Stop, not something to paper over.
- **Green-wash by unit test.** Testing the extractor against a hand-built `Jwt`, or the builder
  against a hand-built config object, proves the class in isolation, not the wired chain. AC-3 and
  AC-6 require the real filter chain and a really-signed token.
- **Starting D4 before `TASK-MONO-500` lands.** There is no builder to adopt yet; implementing
  against a not-yet-existing or since-changed API wastes the work and risks papering over a design
  question `TASK-MONO-500`'s own implementer should resolve. AC-4 is the gate.
- **Assuming the four D4 copies are still identical without re-verifying.** The Goal section's
  byte-identical finding is dated to this task's filing; code may have moved. If any one of the
  four has drifted, stop and report rather than force a single shape onto a real divergence.
- **Scope creep into D5.** `PublicPaths`'s actual exempt-path *data* is D5's job
  (`TASK-ERP-BE-040`); this task only changes how the decoder/enforcer chain is assembled around
  whichever `PublicPaths`/`PublicPathSet` instance the service already supplies.

---

# Test Requirements

- **Unit/slice (D1, ×2 services)** — auth-path test per service driving the real filter chain with
  a really-signed RSA token, both claim forms, asserting authorities + the five-component
  `ActorContext` (including `dataScopeDepartmentIds`/`entitledDomains`) matches pre-adoption
  behavior.
- **Unit/slice (D4, ×4 services)** — existing 401/403/wildcard-admit/entitlement-admit tests pass
  unmodified; at least one new test per service exercises the builder-constructed chain directly.
- **Regression net** — every existing test in all four services passes unmodified except the
  explicitly-listed construction-site edits (converter/enforcer instantiation lines).
- `./gradlew :projects:erp-platform:apps:{approval,masterdata,notification,read-model}-service:check`
  GREEN. CI `Integration (erp-platform, Testcontainers)` GREEN is authoritative.

---

# Definition of Done

- [ ] D1 adopted in `approval-service` + `masterdata-service`; mechanism deleted, policy
      byte-unchanged, integration-level auth-path tests added and passing
- [ ] D4 adopted in all four servlet services, gated on `TASK-MONO-500` `Status: done`, verdicts
      unchanged (401/403/wildcard/entitlement all regression-net green)
- [ ] Before/after test counts recorded per module; guard mutation-check recorded for both halves
- [ ] No contract change; PR body states explicitly there is no observable behaviour delta
- [ ] `gateway-service` verified unaffected
- [ ] Ready for review
