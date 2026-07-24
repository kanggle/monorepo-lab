# Task ID

TASK-MONO-479

# Title

Migrate `membership-service` onto `libs/payment` (ADR-MONO-056 Phase 1, consumer 1)

# Status

backlog

# Owner

backend

# Task Tags

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

[ADR-MONO-056](../../docs/adr/ADR-MONO-056-payment-gateway-abstraction.md) Phase 1,
consumer migration. Replace `membership-service`'s private `PortOnePaymentAdapter`
+ its port with a dependency on `libs/payment-core` + `libs/payment-portone`
(created by TASK-MONO-478), wiring the shared adapter by profile/config.

**Blocked on TASK-MONO-478** — promote to `ready/` only when 478 has landed on
`main`. Behavior-preserving refactor: the fan-platform PortOne verify boundary
(ADR-001) and all existing membership payment tests must stay green.

---

# Scope

## In Scope

- Depend on `libs/payment-core` + `libs/payment-portone`; delete the in-service
  duplicate adapter + port.
- Preserve `@Profile("portone")` vs mock-default wiring (keyless CI still uses the
  mock; real PG only under the `portone` profile).
- Preserve the existing test surface (SubscribeUseCaseTest, PortOne adapter tests,
  Testcontainers Integration) — adapt imports only, not assertions.

## Out of Scope

- payment-service migration (TASK-MONO-480).
- Any change to the membership domain / subscribe / upgrade logic.

---

# Acceptance Criteria

- [ ] membership-service uses the shared adapter; no in-service PG adapter remains.
- [ ] `@Profile("portone")` / mock-default behavior preserved (CI keyless = mock).
- [ ] All existing membership tests + Integration (fan-platform) green — assertions
      unchanged.
- [ ] One atomic PR.

---

# Related Specs

- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md`
- `projects/fan-platform/docs/adr/ADR-001-real-pg-portone-verification-boundary.md`

# Related Contracts

- `projects/fan-platform/specs/contracts/http/membership-api.md` (unchanged)

---

# Edge Cases

- The mock-default fallback must remain the CI/keyless path — a migration that made
  `portone` the default would break keyless CI (real PG call with no secret).

# Failure Scenarios

- Losing the fail-closed verification on the moved adapter → unverified payments
  approved. Preserve exactly (money-safety).

---

# Definition of Done

- [ ] Migrated onto libs/payment; duplicate adapter deleted
- [ ] Tests + Integration green, assertions unchanged
- [ ] Ready for review

Analysis + implementation model: **Opus**.
