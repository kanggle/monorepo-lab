# Task ID

TASK-ERP-BE-006

# Title

`masterdata-service` response **contract conformance** — nest the flat `effectiveFrom`/`effectiveTo` into the contract's `effectivePeriod` object (+ emit an `audit` object on detail) across all 5 master Views, so the responses match `masterdata-api.md`. The producer currently emits FLAT `effectiveFrom`/`effectiveTo` while the contract (and the platform-console consumer) require the nested `effectivePeriod: { effectiveFrom, effectiveTo }` — a producer-side contract violation that makes every console ERP 운영 read fail to parse.

# Status

review

# Owner

backend-engineer (masterdata-service presentation/view layer — response shape only; no domain/persistence/auth change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Dependency Markers

- **surfaced by**: the TASK-MONO-170 platform-console per-domain ops live demo (2026-06-02/03). With auth/routing/entitlement all working, the console ERP 운영 page received `200` from all 5 masterdata list endpoints but **every response failed the consumer Zod parse** (`erp_ok` immediately followed by `erp_error` in console-web logs) → the ERP section rendered "degraded". Root cause isolated to the response **shape**: producer emits flat `effectiveFrom`/`effectiveTo`; the contract + consumer require nested `effectivePeriod`.
- **fixes a producer↔contract drift**, NOT the console: `masterdata-api.md` § Common shapes + every master's 200 body specify `effectivePeriod: { effectiveFrom, effectiveTo }` (nested) and (detail) `audit: { createdAt, createdBy, updatedAt, updatedBy }`. The console (`platform-console` `features/erp-ops/api/types.ts`, TASK-PC-FE-010) parses exactly that. The masterdata-service `*View` records (`DepartmentView`/`EmployeeView`/`JobGradeView`/`CostCenterView`/`BusinessPartnerView`) emit flat fields — non-conformant.
- **why undetected until now**: the console ERP client had only mocked unit tests (mocks matched the contract), and the producer's own slice/controller tests assert only `$.data.code`/`$.data.status` (never the effective-dating shape). No test ever exercised the real producer→console JSON, so the drift was latent until the MONO-170 demo made the first live call.
- **no dependency on**: any domain / persistence / auth / Flyway change. The effective-dating data + audit timestamps already exist on the entities; this only changes how the presentation View serialises them.

---

# Goal

Make the masterdata-service 200 responses conform to `masterdata-api.md`: the effective-dating period MUST be the nested `effectivePeriod: { effectiveFrom, effectiveTo }` (both ISO-8601 DATE; `effectiveTo` nullable) on every list row and detail; detail responses additionally carry an `audit` object. This unblocks the platform-console ERP 운영 page (all 5 masters) and removes a latent contract violation across the surface.

# Scope

## In Scope

`projects/erp-platform/apps/masterdata-service` only — presentation/view layer:

1. **New view DTOs** — `EffectivePeriodDto(LocalDate effectiveFrom, LocalDate effectiveTo)` and `AuditDto(Instant createdAt, Instant updatedAt)` in the `application/view` package (the actor `createdBy`/`updatedBy` are NOT tracked on the entities today — emit the available subset; the consumer `audit` schema is all-optional, and the contract `Audit` is a producer-provided detail shape).
2. **5 View records** — `DepartmentView` / `EmployeeView` / `JobGradeView` / `CostCenterView` / `BusinessPartnerView`: replace the flat `effectiveFrom, effectiveTo, createdAt, updatedAt` components with `EffectivePeriodDto effectivePeriod` + `AuditDto audit`; update each `from(...)` factory. (All other fields — `id`/`code`/`name`/`status`/per-master refs/`paymentTerms` — unchanged.)
3. **Tests** — update the two shape-coupled tests (`DepartmentControllerSliceTest` constructor call; `MasterdataLifecycleIntegrationTest` accessor calls `.effectiveFrom()`/`.effectiveTo()` → `.effectivePeriod().effectiveFrom()/…`) + add an assertion that a list/detail 200 body carries `$.data[0].effectivePeriod.effectiveFrom` (regression: pins the nested shape so the drift cannot recur).

## Out of Scope

- Any domain / persistence / Flyway / auth change (the data already exists).
- Adding actor `createdBy`/`updatedBy` tracking (entities don't carry it; a separate audit-enrichment task if ever needed — the contract `Audit` actor fields are emitted as absent, which the consumer tolerates).
- The mutation endpoints' request shapes (unchanged — only response serialisation changes).
- The other surfaced MONO-170 issues (SCM procurement parse / SCM inventory-visibility 422 / WMS alerts seed) — separate per-domain tasks.
- The platform-console consumer (already correct per the contract).

# Acceptance Criteria

- [ ] **AC-1** Every masterdata list 200 body serialises each row with `effectivePeriod: { effectiveFrom, effectiveTo }` (nested) and NO top-level flat `effectiveFrom`/`effectiveTo`.
- [ ] **AC-2** Every detail 200 body additionally carries `audit: { createdAt, updatedAt }`.
- [ ] **AC-3** All 5 masters (department/employee/job-grade/cost-center/business-partner) conform identically.
- [ ] **AC-4** `effectiveTo: null` (open-ended/active row) serialises as JSON `null` (not omitted in a way that breaks the consumer) and parses.
- [ ] **AC-5** `masterdata-service` `./gradlew :check` green (the updated slice/integration tests + the new nested-shape regression assertion).
- [ ] **AC-6** Diff confined to `projects/erp-platform/apps/masterdata-service/src/{main,test}/.../view` + the 2 touched test files (+ task lifecycle). No domain/persistence/auth/contract/ADR change (the contract already specifies the nested shape — the producer is being brought into conformance, the contract is unchanged).
- [ ] **AC-7** (live) Rebuilt masterdata-service in the MONO-170 demo stack → console ERP 운영 (active tenant globex-corp) renders the 5 masters live (no parse error / no degraded).

# Related Specs

- [`specs/contracts/http/masterdata-api.md`](../../specs/contracts/http/masterdata-api.md) § Common shapes (`EffectivePeriod`, `Audit`, `PageMeta`) + each master's 200 body — the authoritative response shape this task conforms to. **Unchanged** (the producer is the non-conformant party).
- [`specs/services/masterdata-service/architecture.md`](../../specs/services/masterdata-service/architecture.md) § E2/E3 effective-dating.
- Consumer reference: `platform-console` `apps/console-web/src/features/erp-ops/api/types.ts` (the Zod schema that requires the nested `effectivePeriod`).

# Related Contracts

- [`masterdata-api.md`](../../specs/contracts/http/masterdata-api.md) — byte-unchanged; this task makes the implementation match it.

# Edge Cases

- `effectiveTo == null` (active/open-ended) → `effectivePeriod.effectiveTo: null` (consumer schema is `.nullable()`).
- Retired row (`effectiveTo` in the past) → still rendered (E2 honesty) — the nested shape carries it identically.
- `audit` actor fields absent (entities don't track them) → emit `{ createdAt, updatedAt }`; consumer `AuditSchema` is `.partial().passthrough()` so the subset parses.

# Failure Scenarios

- Forgetting a master → that master's console section still degrades. Mitigation: AC-3 covers all 5 + the regression assertion.
- Serialising BOTH flat and nested (e.g., leaving the flat record components) → bloated body; the consumer would still parse (passthrough) but it is not contract-clean. Mitigation: AC-1 requires NO top-level flat fields.

# Test Requirements

- Update `DepartmentControllerSliceTest` (constructor) + `MasterdataLifecycleIntegrationTest` (accessors).
- Add a nested-shape assertion (`$.data[*].effectivePeriod.effectiveFrom` exists; no `$.data[*].effectiveFrom`).
- `./gradlew :projects:erp-platform:apps:masterdata-service:check` green.
- Live AC-7 = rebuild in the MONO-170 demo stack + console click.

# Definition of Done

- [ ] 5 Views nested + DTOs added + factories updated.
- [ ] Tests updated + regression assertion added; `:check` green.
- [ ] AC-7 live-verified in the demo stack.
- [ ] Diff scope confined; contract/ADR untouched.
- [ ] Task md + `INDEX.md` updated.
- [ ] Ready for review.

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (mechanical, well-scoped presentation-layer reshape across 5 symmetric Views + 3 test touch-points; confirmed single root cause, live-reproduced). 직접 수행 + masterdata-service `:check` + 데모 스택 라이브 재검증.
