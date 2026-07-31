# Task ID

TASK-SCM-BE-054

# Title

Adopt ADR-MONO-058 D1 (actor/JWT-claim cluster) + D4 (security-chain assembly) — combined, one task per project per ADR § 6 item 7

# Status

ready

# Owner

backend

# Task Tags

- code

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

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D1 and D4 both touch authentication/authorization-adjacent code and are flagged in § 4 as the two highest-risk decisions in the whole ADR (auth-path, every servlet service in the fleet). § 6 item 7 explicitly directs these two to be bundled into **one task per project, not one per service**, so scm-platform's own role-set/actor policy is threaded through consistently in a single pass instead of four separate PRs that could each thread it slightly differently. This task adopts:

- **D1** — the already-shared `libs/java-security-servlet` actor package (claim-lifting, authority-prefixing, the resolver/converter classes, `@CurrentActor` plumbing) in place of scm-platform's one local copy.
- **D4** — the security-chain-assembly builder promoted by `TASK-MONO-500` (`NimbusJwtDecoder` construction + `AllowedIssuersValidator`/`TenantClaimValidator` chain wiring + the generic filter-chain tail) in place of each servlet service's local `ServiceLevelOAuth2Config` + generic `SecurityConfig` tail.

---

# Scope

## In Scope

**D1 (actor/JWT-claim cluster) — `procurement-service` only.**

Grep across all of `projects/scm-platform/apps/` (2026-07-31) confirms `ActorContextResolver`, `ActorContextJwtAuthenticationConverter`, and the `ActorContext` record exist **only** in `procurement-service`:

- `apps/procurement-service/src/main/java/.../procurement/application/security/ActorContextResolver.java`
- `apps/procurement-service/src/main/java/.../procurement/infrastructure/security/ActorContextJwtAuthenticationConverter.java`
- `apps/procurement-service/src/main/java/.../procurement/application/ActorContext.java`

`logistics-service`, `inventory-visibility-service`, and `demand-planning-service` were grepped for `[Aa]ctor` and have no actor-extraction class of this shape (their JWT handling stops at tenant/authority checks — see `inventory-visibility-service`'s `TenantClaimExtractor`, which is a different, narrower mechanism already outside D1's scope). D1 adoption for scm-platform is therefore a single-service change:

- Replace `ActorContextResolver` + `ActorContextJwtAuthenticationConverter` with `libs/java-security-servlet`'s actor package.
- Re-parameterize the local `ActorContext` record to consume a plain `Set<String> roles` from the shared resolver. Keep **all** of `ActorContext`'s per-service convenience methods and role-set literals local — the ADR's own Ownership Rule (§ 2 D1) explicitly excludes these from promotion (`isAdmin()`-style helpers, any scm-specific role literal).
- `ActorContextJwtAuthenticationConverterTest` and `ActorContextTest` must be adapted 1:1 (same assertions against the new call path), not deleted.

**D4 (security-chain assembly) — `procurement-service`, `logistics-service`, `inventory-visibility-service`, `demand-planning-service`.**

Grep (2026-07-31) confirms all four non-gateway REST services carry their own `ServiceLevelOAuth2Config` + `SecurityConfig`:

- `apps/procurement-service/.../infrastructure/security/{ServiceLevelOAuth2Config,SecurityConfig}.java`
- `apps/logistics-service/.../config/{ServiceLevelOAuth2Config,SecurityConfig}.java`
- `apps/inventory-visibility-service/.../config/{ServiceLevelOAuth2Config,SecurityConfig}.java`
- `apps/demand-planning-service/.../config/{ServiceLevelOAuth2Config,SecurityConfig}.java`

For each: replace the `NimbusJwtDecoder` construction + `AllowedIssuersValidator`/`TenantClaimValidator` chain assembly + the generic (non-domain) filter-chain tail with an explicit, opt-in call into the `TASK-MONO-500` builder from the service's own `@Configuration` class. Each service continues to supply its own issuer allow-list, tenant-claim policy parameters, exempt-path data, and `application.yml` property keys.

**`gateway-service` is excluded from both D1 and D4.** Per `specs/services/gateway-service/architecture.md`, it is `rest-api` (edge gateway role) built on **Spring Cloud Gateway (reactive)** — the ADR's own § 2 D1 explicitly draws the reactive/servlet boundary ("a reactive gateway already gets the equivalent from `libs/java-gateway` — do not cross that boundary, `ADR-MONO-048 § D1`"), and `libs/java-security-servlet` (the D1/D4 target module) is servlet-only.

## Out of Scope

- Any change to `AllowedIssuersValidator`/`TenantClaimValidator` themselves — already shared per `ADR-MONO-049`.
- D2 (error envelope), D3 (pagination), D5 (`PublicPaths`) — each filed as its own separate task (`TASK-SCM-BE-055`, `-056`, `-057`).
- `gateway-service` — reactive, out of scope for both D1 and D4 as established above.
- Any change to scm's actual issuer allow-list values, tenant-claim policy parameters, or exempt-path data — those stay per-service (Ownership Rule); only the assembly mechanism moves.
- `TASK-SCM-BE-052` (destination-addressing seam, currently blocked in `backlog/`) — unrelated, not touched by this task.

---

# Acceptance Criteria

- [ ] **Prerequisite gate**: `TASK-MONO-500` (D4 builder promotion to `libs/java-security-servlet`) is merged before the D4 half of this task starts — verify by reading that task's own `Status` field directly, not by inference. The D1 half has no such gate (the actor package is already promoted and present at `libs/java-security-servlet/src/main/java/com/example/security/servlet/actor/`).
- [ ] D1: `procurement-service`'s `ActorContextResolver.java` and `ActorContextJwtAuthenticationConverter.java` are removed/replaced by the shared `libs/java-security-servlet` actor package; the local `ActorContext` record is re-parameterized to a plain `Set<String> roles` input while retaining every existing convenience method and role literal unchanged in behavior.
- [ ] D1: claim-lifting behavior is unchanged — `sub`/`tenant_id` extraction, `roles`-or-`role` (array or delimited string) normalization into `ROLE_`-prefixed authorities all verified identical before/after via the existing test suite.
- [ ] D1: `ClientCredentialsActorOverflowIntegrationTest` (the `TASK-SCM-BE-050` 37-char client-credentials `sub` regression test) passes unmodified through the new resolution path.
- [ ] D4: all four services (`procurement`, `logistics`, `inventory-visibility`, `demand-planning`) invoke the `TASK-MONO-500` builder explicitly and opt-in from their own `@Configuration` class — never a component-scanned/auto-configured bean.
- [ ] D4: for each of the four services, exempt-path routing, authenticated-vs-public path behavior, stateless session policy, and CSRF-disabled-for-API posture are verified byte-for-byte unchanged before/after (diff actual filter-chain behavior via existing 401/403 tests, not just class presence).
- [ ] `gateway-service` is untouched by this task (no files under `apps/gateway-service/` modified).
- [ ] All pre-existing security-relevant tests across the four touched services (unit, slice, and Testcontainers IT — including `MultiTenantIsolationIntegrationTest`, `AuditLogIntegrationTest`, and each service's own 401/403 slice tests) pass unmodified with an identical test count to the recorded baseline.
- [ ] scm-platform's Build & Test and Integration (Testcontainers) CI lanes are GREEN for all four touched services.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D1, § 2 D4, § 4 (risk framing), § 6 item 7 (bundling directive — this task's own shape)
- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` (precedent: `AllowedIssuersValidator`/`TenantClaimValidator` consolidation the D4 builder composes with)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin — this task is one of its splits)
- `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` (**hard prerequisite for the D4 half** — must be merged first)
- `platform/shared-library-policy.md` (Decision Rule, Ownership Rule, § No context-wide annotations)
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/services/logistics-service/architecture.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md`
- `projects/scm-platform/specs/services/gateway-service/architecture.md` (confirms reactive stack, why it's excluded)
- `projects/scm-platform/specs/integration/iam-integration.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- None directly — this is internal security-chain wiring, not a wire-format or event contract change. The GAP-issued JWT claims consumed (`sub`, `tenant_id`, `roles`) are unchanged; scm-platform's own published HTTP contracts are unaffected.

---

# Target Service

- `procurement-service` (D1 + D4)
- `logistics-service` (D4 only)
- `inventory-visibility-service` (D4 only)
- `demand-planning-service` (D4 only)
- `gateway-service` — explicitly out of scope (reactive)

---

# Architecture

Follow each touched service's own architecture doc:

- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/services/logistics-service/architecture.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md`

---

# Implementation Notes

- Read `TASK-MONO-500`'s actually-landed builder shape before designing the four D4 call-sites — that task's own Edge Cases note it may widen its configuration surface if a service's current chain has diverged since the audit; verify against current code, don't assume the task file's original design survived unchanged.
- D1 evidence (2026-07-31 grep): only `procurement-service` has `ActorContextResolver`/`ActorContextJwtAuthenticationConverter`/`ActorContext`. If a future re-check finds an actor-shaped class under a different name in `logistics`/`inventory-visibility`/`demand-planning`, treat it as newly-discovered scope and extend this task rather than assuming this file is wrong.
- D4 ordering note: `TASK-SCM-BE-057` (D5, `PublicPathSet` adoption) is **not** a hard blocker for this task's D4 half — each service can pass its existing local `PublicPaths` data into the D4 builder's exempt-path parameter as-is today, then separately swap to the shared `PublicPathSet` type in `TASK-SCM-BE-057`. Landing `TASK-SCM-BE-057` first (matching the ADR § 6 suggested order: D5 before D1/D4) avoids threading the exempt-path parameter twice, but is a scheduling preference, not an AC.
- Spring's JWT authentication converter is wired once per service's `SecurityConfig` — verify the new shared actor converter is registered in exactly the same place in the filter chain as the old one (converter registration order can silently change which authorities land on the `Authentication` object).

---

# Edge Cases

- If any of the four services' current filter chain has picked up a service-specific filter since the 2026-07-29 audit that the `TASK-MONO-500` builder doesn't account for, do not force-fit it — document the divergence and file a narrow per-service follow-up instead of dropping the filter silently.
- `procurement-service`'s webhook paths (`/api/procurement/webhooks/`) bypass both authentication and tenant-claim enforcement via a **shared-secret** verification chain, not the JWT/actor path at all (see `WebhookSecurityConfig`/`WebhookSignatureFilter`) — confirm the D4 adoption does not accidentally route webhook requests through the JWT decoder chain.
- The 37-char `scm-platform-internal-services-client` client-credentials `sub` (widened to `VARCHAR(255)` in `TASK-SCM-BE-050`) must still resolve correctly through the new shared D1 resolver — this is exactly the kind of boundary-width regression a resolver swap could reintroduce.

---

# Failure Scenarios

- If the landed `TASK-MONO-500` builder is (or becomes) a component-scanned auto-configuration rather than an explicit opt-in call, adopting it here would violate `shared-library-policy.md § No context-wide annotations` and silently change every one of the four services' security posture on a library version bump — Hard Stop, do not adopt until confirmed opt-in.
- Silently changing CSRF/session/exempt-path posture on any of the four services while replacing `SecurityConfig` would be an undisclosed behavior change for every existing client — verify parity via existing 401/403 tests before declaring done, not by code-reading alone.
- Dropping `ActorContext`'s convenience methods (`isAdmin()`, etc.) or role-set literals during the D1 swap instead of re-parameterizing them on top of the shared record's plain `Set<String> roles` would violate the ADR's Ownership Rule and break every controller/service consuming `ActorContext` in `procurement-service`.
- Treating this as a green light to also change scm's actual issuer allow-list, tenant-claim policy, or exempt-path values "while in there" would conflate a mechanism-promotion adoption with a policy change — scope creep, not authorized by this task.

---

# Test Requirements

- No new test scenarios required — this is a behavior-preserving structural adoption. All existing security-relevant tests (unit, slice, and Testcontainers IT) across the four touched services must pass unmodified with an identical test count to the recorded baseline, including but not limited to: `ActorContextJwtAuthenticationConverterTest`, `ActorContextTest`, `ClientCredentialsActorOverflowIntegrationTest`, `MultiTenantIsolationIntegrationTest`, and each service's own 401/403 slice tests.
- Record the pre-change baseline test count (per service) in the PR body before any edit, per this project's established convention (see `TASK-SCM-BE-051`).

---

# Definition of Done

- [ ] `TASK-MONO-500` confirmed merged before D4 half starts
- [ ] D1 adopted in `procurement-service`; D4 adopted in `procurement-service`, `logistics-service`, `inventory-visibility-service`, `demand-planning-service`
- [ ] `gateway-service` untouched
- [ ] All pre-existing security tests pass unmodified, identical counts per service to recorded baseline
- [ ] scm-platform Build & Test + Integration (Testcontainers) CI lanes GREEN for all four touched services
- [ ] Task moved `ready → done`, referencing `TASK-MONO-495` and `TASK-MONO-500` as origin/prerequisite
