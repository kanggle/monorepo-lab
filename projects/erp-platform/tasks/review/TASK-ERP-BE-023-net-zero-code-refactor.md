# TASK-ERP-BE-023 — net-zero code refactoring across erp-platform services

**Status:** review

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (refactoring-engineer dispatch; security-adjacent authz predicate among the targets)

---

## Goal

`/refactor-code` (service-scan) pass over all 4 erp-platform services (masterdata / approval / notification / read-model) applying **behavior-preserving (net-zero)** refactorings only — no requirement, contract, API/event schema, status-code, error-message, or domain-invariant change. Follows the spec-side passes TASK-ERP-BE-021 (roles-only alignment) + TASK-ERP-BE-022 (refactor-spec); this is the code-side complement.

A 4-service discovery scan found the code **mature and largely clean** (built across 20+ ERP-BE tasks with dispatcher-independent re-verification). Domain invariants — fail-closed authorization, idempotency claim-before-execute, append-only audit, ApprovalStateMachine guard ordering, SoD, @Transactional outbox boundaries, terminal-consumer (no outbox/publish), E5 read-only projections, dedupe/terminal-once/sticky-terminal — were confirmed correct and are **explicitly not refactor targets**. The genuine opportunities were all duplication-removal + import hygiene, all verifiable by the Docker-free `:check` (unit/slice) local gate.

## Scope

**Applied refactorings (6):**

| # | Service | Category | Change |
|---|---|---|---|
| R1 | read-model | extract-class / dedup | `expandOrgScope` (identical in 3 query use cases) → new `@Component application/OrgScopeExpander`; the 3 use cases delegate, redundant `departmentRepository`/`departmentPathMaxDepth` fields dropped where they became unused. |
| R2 | read-model | extract-class / dedup | the depth-bounded, cycle-guarded `resolvePath` ancestry walk (duplicated in org-view + approval-fact use cases) → new `@Component application/DepartmentPathResolver` returning neutral `Node` triples; each caller maps to its own view `PathNode`. |
| R3 | read-model | extract-method / dedup | `validatePaging` + `orgScopeRootIds(Jwt)` controller plumbing (repeated across 3 controllers) → new package-private `adapter/inbound/web/ReadQueryWebSupport`; all 3 controllers use it. |
| R4 | approval | dedup | the triplicated erp READ/WRITE scope-tuple predicate (2 app services + `JwtBackedAuthorizationAdapter`) → `ActorContext.canWriteErp()` / `canReadErp()`; all 3 sites delegate. Fall-through-to-`AuthorizationPort`, READ-vs-WRITE entitlement asymmetry (entitlement NEVER widens WRITE), SoD, and exception types/messages preserved verbatim. |
| R5 | notification | extract-method / dedup | the byte-identical parse→validate→tenant-resolve prologue of `EnvelopeToCommandMapper`'s 3 `map*` methods → private `parseAndValidateTenant(...)` returning a `ValidatedEnvelope` record; `InvalidEnvelopeException` messages verbatim. |
| R6 | masterdata | naming / import hygiene | inline FQN `java.time.ZoneOffset.UTC` (5 domain entities' `retire()`) + `java.util.ArrayList` (`TenantClaimValidator`) → proper imports. Compile-only behavior-identical. |

**Out of scope (deliberately skipped):**
- masterdata cross-layer micro-duplication (`WILDCARD_TENANT` constant + `writeError` JSON helper across presentation↔infrastructure) — extraction's correct shared home is non-obvious + payoff low; left as-is.
- All parallel-additive consumer/projection structure (Kafka consumers, projection handlers, RecipientResolver/NotificationFactory overloads, masterdata's per-aggregate controller/repo/view symmetry) — intentional standalone/independent design; collapsing would couple independent flows. Not touched.

## Acceptance Criteria

- **AC-1 (net-zero)** — no externally observable behavior change; no API/event contract, status code, or error-message change. The R4 authz predicates `canWriteErp()`/`canReadErp()` are byte-equivalent to the original inline tuples (WRITE = operator ∨ erp.write ∨ erp.approval.create ∨ erp.approval.approve; READ = WRITE ∨ erp.read ∨ entitled), preserving the READ-only entitlement asymmetry.
- **AC-2 (tests preserved)** — every affected service's `:check` (unit + slice) is BUILD SUCCESSFUL before and after. Test code adjusted only for collaborator wiring (the 3 read-model query use-case unit tests construct the new `OrgScopeExpander`/`DepartmentPathResolver` as real instances against the existing `departmentRepository` mock); **no assertion changed**.
- **AC-3 (local gate)** — `:check` GREEN for read-model / approval / notification / masterdata locally (Docker-free; Testcontainers IT is BLOCKED on the dev host — CI Linux "Integration (erp-platform, Testcontainers)" is the authoritative behavioral backstop).
- **AC-4 (invariants untouched)** — fail-closed authz, idempotency, audit append-only, state-machine guards, SoD, outbox atomicity, terminal-consumer, E5 read-only, dedupe/terminal-once/sticky-terminal all unchanged (verified by review + the unchanged unit/slice/IT suites).
- **AC-5 (CI authoritative)** — the erp Testcontainers IT job is GREEN on the PR (end-to-end token→authz→projection paths exercised — the gate the local `:check` cannot run).

## Related Specs

- `projects/erp-platform/specs/services/{masterdata,approval,notification,read-model}-service/architecture.md` — the declared Hexagonal architecture + invariants the refactors preserve.

## Related Contracts

- None changed. (No API/event contract touched.)

## Edge Cases

- **R1/R2 collaborator depth field** — `departmentPathMaxDepth` moved onto `OrgScopeExpander`/`DepartmentPathResolver`; QueryEmployeeOrgViewUseCase keeps its own copy only because it still uses `departmentRepository.findSubtreeIds` for the explicit `?departmentId=` filter. The approval/delegation use cases dropped the now-unused field (and their `setField(..., "departmentPathMaxDepth", 32)` test line) — net-zero, no assertion change.
- **R4 entitlement asymmetry** — `canWriteErp()` deliberately excludes `isEntitledTo(...)`; only `canReadErp()` adds it. A behavior bug would surface if entitlement leaked into WRITE — guarded by AC-1 + the existing authz unit tests (`ApprovalApplicationServiceTest` / `DelegationApplicationServiceTest` / `JwtBackedAuthorizationAdapterTest`).
- **R3 `Math.min(size, MAX_SIZE)` clamp** — preserved as-is (the existing redundant-but-harmless clamp after `validatePaging`); not "cleaned" to avoid any behavior-equivalence judgment.
- **Orphaned constants** — R4 left `ENTITLED_DOMAIN` dead in `ApprovalApplicationService` + `JwtBackedAuthorizationAdapter` (the literal moved into `ActorContext`); both removed to satisfy the `-Xlint`/checkstyle gate — part of the same net-zero dedup.

## Failure Scenarios

- **F1 — authz regression hidden by `:check`** — a subtle predicate change in R4 could mis-authorize. Guarded by AC-1 (byte-equivalence review) + the authz unit tests + the CI IT cross-tenant/role matrix (AC-5).
- **F2 — collaborator wiring breaks projection** — if a new bean were mis-injected, projections would fail. Guarded by AC-2/AC-3 (`:check` GREEN) + CI IT projection end-to-end (AC-5).
- **F3 — net-zero violation surfacing only in Testcontainers** — local `:check` runs unit/slice, not the full token→authz→DB/Kafka path. AC-5 CI IT is the authoritative gate (per `feedback_spring_boot_diagnostic_patterns` § 14).
