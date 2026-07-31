# Task ID

TASK-MONO-500

# Title

ADR-MONO-058 D4 — promote security-chain assembly (`ServiceLevelOAuth2Config` + generic `SecurityConfig` tail) to `libs/java-security-servlet`

# Status

ready

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

- [ ] New builder/factory class exists in `libs/java-security-servlet`, unit-tested, framework-neutral wording (no project names in class/method names or Javadoc — `HARDSTOP-03`).
- [ ] Builder is documented (Javadoc or a short guide) as opt-in — explicitly states it is NOT an auto-configuration.
- [ ] `libs/java-security-servlet`'s own test suite passes (`./gradlew :libs:java-security-servlet:test`).
- [ ] No existing service is modified by this task — this is a promotion-only task; adoption is separate.
- [ ] This task's own `tasks/ready → done` move happens only after being picked up and merged; until then it stays `ready`.

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

# Definition of Done

- [ ] Builder/factory landed in `libs/java-security-servlet` with passing unit tests
- [ ] Javadoc states opt-in posture explicitly
- [ ] No adopting service touched by this PR
- [ ] Task moved to `done`, referencing the per-project adoption tasks it unblocks
