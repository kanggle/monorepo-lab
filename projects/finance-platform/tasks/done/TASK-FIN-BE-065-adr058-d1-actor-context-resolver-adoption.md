# Task ID

TASK-FIN-BE-065

# Title

ADR-MONO-058 D1 — adopt `libs/java-security-servlet`'s shared actor/JWT-claim cluster in `account-service` + `ledger-service` (retire local `ActorContextResolver` + `ActorContextJwtAuthenticationConverter` claim-lifting)

# Status

done

# Owner

backend

# Task Tags

- code
- security
- test
- adr

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

`docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 **D1** (ACCEPTED
2026-07-30) found the actor/JWT-claim extraction cluster (`ActorContextResolver`,
`ActorContextJwtAuthenticationConverter`, the `@CurrentActor` mechanism where present) duplicated
~15+ times across 5 projects, finance-platform among them, and directs promoting the **mechanism**
(claim-lifting, `ROLE_`-prefixing, the resolver/converter classes) to `libs/java-security-servlet`
while leaving each service's own authorization **policy** (role predicates, role-set literals)
per-service (`shared-library-policy.md § Ownership Rule`).

That promotion is **already done** — `libs/java-security-servlet/src/main/java/com/example/security/servlet/actor/`
ships `ActorClaims` (claim lifting + `ROLE_` authorities), `ActorContextFactory<A>` (the
service-supplied actor-construction seam), `ActorContextJwtAuthenticationConverter<A>` (wires the
two together), `ActorAuthenticationToken` (the principal-carrying token), and
`ActorContextResolver.currentOrThrow(Class<A>)` (reads the actor back off `SecurityContext`) — via
`fan-platform`'s `TASK-FAN-BE-040`. **This task is the finance-platform adoption**, not a
promotion — no new shared-library code is authorized or expected here.

`account-service` and `ledger-service` each hand-roll a **byte-identical mechanism**
(`infrastructure/security/ActorContextResolver.java`, `infrastructure/security/ActorContextJwtAuthenticationConverter.java`)
around their own `ActorContext` record. Adopting the shared mechanism removes that duplication while
preserving 100% of finance's existing authorization behavior (entitlement-trust `ROLE_FINANCE_VIEWER`
synthesis, super-admin wildcard `ROLE_FINANCE_SUPERADMIN_READ` synthesis, `SCOPE_*` authority lifting,
role predicates like `isOperator()`), all of which are finance-owned policy and stay in-service per
the Ownership Rule.

finance-platform does **not** declare D4 (security-chain assembly — `ServiceLevelOAuth2Config` +
generic `SecurityConfig` tail is not in the ADR's § 1.1 confirmed-project list for finance), so this
task is filed **standalone**, not bundled with any security-chain-assembly work.

---

# Scope

## In Scope

- `account-service` — replace `infrastructure/security/ActorContextResolver.java` (static
  `currentOrThrow()`) with the shared `com.example.security.servlet.actor.ActorContextResolver.currentOrThrow(ActorContext.class)`
  at every call site. Delete the local file once the last caller is migrated.
- `account-service` — replace `infrastructure/security/ActorContextJwtAuthenticationConverter.java`'s
  claim-lifting body (the `sub`/`tenant_id`/`roles`-or-`role` extraction and base `ROLE_*` authority
  construction) with a call into the shared `ActorClaims.from(jwt)` / `ActorContextJwtAuthenticationConverter<A>`
  machinery, while **keeping** — unchanged, in-service — every piece of finance-specific policy layered
  on top: `ENTITLEMENT_DOMAIN`/`VIEWER_ROLE`/`SUPERADMIN_READ_ROLE` constants, `SCOPE_*` authority
  lifting from the `scope`/`scp` claim, entitlement-trust `ROLE_FINANCE_VIEWER` synthesis
  (`TenantClaimValidator.isEntitled`), and super-admin wildcard `ROLE_FINANCE_SUPERADMIN_READ`
  synthesis (`TenantClaimValidator.WILDCARD_TENANT`). See Implementation Notes for why this cannot be
  a bare `implements` swap — finance's converter does strictly more than the shared mechanism's scope
  (mechanism-only claim lifting), and the extra authorities cannot be attached post-construction to
  the shared `ActorAuthenticationToken` (its authority collection is set once at construction, per
  `AbstractAuthenticationToken`).
- `ledger-service` — the same two replacements, mirrored (ledger's `ActorContext` uses `subject`
  instead of `accountId` as the first component name, and its converter is otherwise
  near-byte-identical to account's, including the same entitlement/wildcard synthesis — verify current
  code before assuming full parity, since the ADR's own snapshot can go stale).
- **Not touched**: `ActorContext` record itself (`application/ActorContext.java` in both services) —
  its convenience methods (`hasRole`, `isOperator`, `actorType()` in account; `hasRole`, `identity()`
  in ledger) and role-set literals (`OPERATOR`/`ADMIN`/`SUPER_ADMIN`/`FINANCE_OPERATOR`) are
  finance-owned authorization policy per D1's explicit Ownership-Rule carve-out — these stay
  per-service, parameterized into `ActorContextFactory<ActorContext>` as `(accountId, tenantId,
  roles) -> new ActorContext(accountId, tenantId, roles)` / the ledger equivalent with `subject`.
- `account-service` / `ledger-service` `build.gradle` — no dependency change required; both already
  declare `implementation project(':libs:java-security-servlet')` (verified — ledger via
  `ADR-MONO-049 § D5-3`'s `TenantClaimEnforcer` adoption, account the same).
- Existing `ActorContextJwtAuthenticationConverterTest` suites (both services) — must continue to pass
  unchanged in assertions (they assert on the resulting `ActorContext`/authorities, not on which class
  performs the lifting), proving the adoption is behavior-preserving.

## Out of Scope

- Any change to `ActorContext`'s shape, methods, or role-set literals — those are finance's own
  authorization policy and are explicitly carved out of D1 (ADR § 2 D1 bullet 2).
- D4 (security-chain assembly / `ServiceLevelOAuth2Config`) — not declared for finance-platform per
  the ADR's § 1.1 table; do not bundle it in here.
- `@CurrentActor` / `CurrentActorArgumentResolver` request-scoping — finance currently calls
  `ActorContextResolver.currentOrThrow()` directly at the point of use rather than through an
  argument-resolver annotation (verified: no `@CurrentActor` usage found anywhere in
  `projects/finance-platform/apps`). Adopting the annotation-based mechanism is a separate,
  optional follow-up, not required for D1 adoption — the resolver-based call is already the shared
  library's supported shape.
- Any change to the JWT wire contract, GAP token issuance, or `ServiceLevelOAuth2Config`'s validator
  chain (`AllowedIssuersValidator`/`TenantClaimValidator`/`JwtTimestampValidator`) — untouched by this
  task.
- D2 (error envelope) and D3 (pagination) — separate tasks (`TASK-FIN-BE-066`, `TASK-FIN-BE-067`).

---

# Acceptance Criteria

- [ ] Neither service contains its own `infrastructure/security/ActorContextResolver.java` any more;
      every former call site resolves through `com.example.security.servlet.actor.ActorContextResolver.currentOrThrow(ActorContext.class)`.
- [ ] Neither service's `ActorContextJwtAuthenticationConverter` re-implements claim-lifting
      (`sub`/`tenant_id` extraction, `roles`-or-`role` normalization, base `ROLE_*` authority
      construction) — that logic is delegated to the shared `ActorClaims`/`ActorContextJwtAuthenticationConverter<A>`.
- [ ] Every finance-specific authority (`SCOPE_*` lifting, `ROLE_FINANCE_VIEWER` entitlement-trust
      synthesis, `ROLE_FINANCE_SUPERADMIN_READ` wildcard synthesis) still fires identically —
      proven by the existing `ActorContextJwtAuthenticationConverterTest` suites passing unchanged
      (assertion text/expected-authority-set unmodified) in both services.
- [ ] `ActorContext`'s public shape (record components, `hasRole`/`isOperator`/`actorType()` in
      account; `hasRole`/`identity()` in ledger) is byte-unchanged.
- [ ] No new `libs/` code is added by this task (the shared mechanism already exists) — if
      implementation discovers the shared mechanism cannot actually cover one of finance's
      authorities without a library change, STOP and report rather than modifying `libs/` under this
      task's scope (a `libs/` change requires its own shared-library-policy review, per
      `platform/shared-library-policy.md § Change Rule`).
- [ ] `./gradlew :projects:finance-platform:apps:account-service:check :projects:finance-platform:apps:ledger-service:check`
      is GREEN, with before/after test counts recorded (no test silently lost).
- [ ] `./gradlew :projects:finance-platform:apps:account-service:integrationTest :projects:finance-platform:apps:ledger-service:integrationTest`
      GREEN (Testcontainers — CI-authoritative; local Windows Docker runs are non-authoritative per
      `project_testcontainers_docker_desktop_blocker`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/fintech.md` and `rules/traits/transactional.md` / `rules/traits/regulated.md` / `rules/traits/audit-heavy.md` (finance-platform's declared classification). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D1, § 6 item 7
  (the fleet decision this task adopts)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the root split origin — this
  task is one of the per-decision/per-project tasks it required)
- `platform/shared-library-policy.md` § Ownership Rule (the mechanism-vs-policy boundary this task
  must respect), § Dependency Rule
- `specs/services/account-service/architecture.md` § Boundary rules, § Allowed dependencies
- `specs/services/ledger-service/architecture.md` § Boundary rules, § Allowed dependencies
- `specs/integration/iam-integration.md` (GAP JWT claim shapes finance consumes)

---

# Related Contracts

- None — this is an internal security-mechanism refactor. No HTTP/event wire-format change; the
  `ActorContext` principal's shape and the authorities it carries are unchanged, so no client-visible
  contract moves.

---

# Target Service

- `account-service`
- `ledger-service`

---

# Architecture

- `account-service`: Hexagonal (per `specs/services/account-service/architecture.md`). The shared
  `ActorContextResolver`/`ActorContextJwtAuthenticationConverter` calls live in
  `infrastructure/security/`, same layer as today — this is a same-layer swap, not a layering change.
  `application/ActorContext` stays a framework-free value object.
- `ledger-service`: Hexagonal + DDD (per `specs/services/ledger-service/architecture.md`). Same
  layering note applies.

---

# Implementation Notes

- **Why this is not a bare `implements` swap.** The shared
  `ActorContextJwtAuthenticationConverter<A>` (`libs/java-security-servlet`) only lifts `sub`/`tenant_id`/`roles`
  and builds `ROLE_*` authorities via `ActorClaims` — by design, per its own javadoc ("mechanism, not
  policy... contains no role-name literal and no role predicate of any kind"). Finance's converters do
  strictly more: they additionally lift the OAuth2 `scope`/`scp` claim into `SCOPE_*` authorities and
  synthesize two finance-specific READ authorities (`ROLE_FINANCE_VIEWER` from entitlement-trust,
  `ROLE_FINANCE_SUPERADMIN_READ` from the tenant wildcard). The shared `ActorAuthenticationToken`'s
  authority collection is fixed at construction (`AbstractAuthenticationToken` contract), so a
  subclass cannot append authorities to an already-built shared token post-hoc.
  Two adoption shapes are viable — choose one and record the choice in the PR:
  1. A thin finance-owned `Converter<Jwt, AbstractAuthenticationToken>` that calls
     `ActorClaims.from(jwt)` for the mechanism (claim lifting + base `ROLE_*` authorities), builds its
     own `Collection<GrantedAuthority>` starting from `claims.authorities()` and appending the
     finance-specific ones, then constructs `ActorAuthenticationToken` (public, non-final, reusable
     directly from `libs/java-security-servlet`) with the combined set.
  2. A finance-owned `ActorContextFactory<ActorContext>` supplied to
     `new ActorContextJwtAuthenticationConverter<>(factory)`, plus a **separate** narrow
     `AuthenticationSuccessHandler`-adjacent or `GrantedAuthoritiesMapper`-based Spring Security hook
     that adds the extra authorities post-authentication — evaluate whether this fits the existing
     `SecurityConfig` chain shape before choosing it; it is more indirect than option 1 and may not be
     worth the added complexity for a same-behavior swap.
  Prefer whichever keeps the resulting code closest to today's readable single-`convert()`-method
  shape — option 1 is likely simpler and is the pattern the shared library's own javadoc implicitly
  invites (`ActorContextFactory` is documented as "the only seam", but nothing prevents a service from
  using `ActorClaims` directly instead of the full converter wrapper when it needs to layer more
  authorities on).
- **`ActorContext` record field naming differs between services** — account uses `accountId` as the
  first component, ledger uses `subject`. Both map 1:1 to `ActorClaims.accountId()` (the JWT `sub`
  claim); `ActorContextFactory<ActorContext>` is exactly the seam that already accommodates this
  naming difference without forcing either service to rename its own field.
- **Read both services' current code before implementing**, not this task's summary — this task's
  investigation (2026-07-31) found the two services' converters near-byte-identical (same
  `ENTITLEMENT_DOMAIN`/`VIEWER_ROLE`/`SUPERADMIN_READ_ROLE` constants, same synthesis logic,
  same javadoc content down to the ADR/task-ID citations), but a later change could have drifted
  them — verify parity before assuming it, per `feedback_recount_population_dont_inherit_scope`.

---

# Edge Cases

- **Principal type check.** The shared `ActorContextResolver.currentOrThrow(Class<A>)` throws
  `IllegalStateException` with message `"Unexpected principal type: " + ...` — verify this exact
  wording matches (or is an acceptable change from) the local copies' identical message, since both
  services map `IllegalStateException` to a documented `422 ILLEGAL_STATE` and their tests may assert
  on message content.
- **`null` principal / unauthenticated.** Both the shared and local resolvers throw
  `IllegalStateException("No authenticated actor in SecurityContext")` identically — confirm this
  stays true after adoption (it should, since the shared class is the promoted copy of this exact
  logic).
- **Entitlement-trust and wildcard synthesis order** — finance's converters add `SCOPE_*` authorities,
  then the entitlement-trust `ROLE_FINANCE_VIEWER`, then the wildcard `ROLE_FINANCE_SUPERADMIN_READ`,
  after the base `ROLE_*` authorities. If adoption shape 1 (Implementation Notes) is chosen, preserve
  this ordering only if any existing test or downstream logic depends on authority collection order
  (Spring Security authority checks are normally order-independent — verify, don't assume).

---

# Failure Scenarios

- Silently dropping the `SCOPE_*` lift, the entitlement-trust `ROLE_FINANCE_VIEWER` synthesis, or the
  wildcard `ROLE_FINANCE_SUPERADMIN_READ` synthesis during adoption would reintroduce the exact
  authorization gaps `TASK-FIN-BE-046`/`047`/`048`/`049` closed (entitled-but-scopeless operators and
  platform super-admins losing READ access) — this is a **regression risk specific to this adoption**,
  not a generic dedup risk, because the shared mechanism does not know about these authorities at all
  and adoption must re-attach them explicitly.
- Adopting the shared `ActorContextResolver.currentOrThrow(Class<A>)` without updating every call site
  (leaving a stray import of the deleted local class) fails the build loudly (safe direction) — the
  dangerous direction is a surviving unused local copy that still compiles, half-closing the
  duplication; grep for zero remaining references to the local `ActorContextResolver`/
  `ActorContextJwtAuthenticationConverter` classes before considering this done.
- Treating this as license to also touch `ActorContext`'s convenience methods or role-set literals
  would violate the Ownership Rule and drift finance's authorization policy as an unannounced side
  effect of a "just a dedup" PR — keep the diff scoped to the mechanism only.

---

# Test Requirements

- Both services' existing `ActorContextJwtAuthenticationConverterTest` suites must pass with
  **unchanged assertions** — this is the behavior-preservation proof for the adoption. Do not weaken
  or delete assertions to make the swap pass; if an assertion cannot pass unchanged, the adoption
  changed behavior and that is a Failure Scenario, not a test update.
- No new unit tests are required by this task specifically (the shared library's own test suite
  already covers `ActorClaims`/`ActorContextJwtAuthenticationConverter<A>`/`ActorContextResolver`
  mechanism behavior) — finance's suites cover the composition (mechanism + finance policy layered on
  top).
- Both services' Testcontainers integration suites (JWKS-backed, real signed JWTs) must stay GREEN —
  CI's `Integration (finance-platform, Testcontainers)` lane is authoritative.

---

# Definition of Done

- [ ] Implementation completed in both `account-service` and `ledger-service`
- [ ] Existing tests pass unchanged (assertion content), before/after counts recorded
- [ ] Integration tests (Testcontainers, CI lane) GREEN
- [ ] No `libs/` change made under this task
- [ ] Contracts unaffected (verified, not just assumed — see Related Contracts)
- [ ] Specs updated if the package tree in either `architecture.md` names the deleted local classes
- [ ] Ready for review
