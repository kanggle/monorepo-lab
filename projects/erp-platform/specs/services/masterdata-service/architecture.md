# masterdata-service — Architecture

This document declares the internal architecture of `erp-platform/apps/masterdata-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the rule files indexed by
`PROJECT.md`'s declared `domain` (`erp`) and `traits`
(`internal-system`, `transactional`, `audit-heavy`).

> **Provenance**: Authored by [TASK-ERP-BE-001](../../../tasks/done/TASK-ERP-BE-001-masterdata-service-bootstrap.md) **before**
> implementation (HARDSTOP-09 — architecture decision precedes code). The
> masterdata-service skeleton (`@SpringBootApplication`, `application.yml`,
> empty `db/migration/`) and the IAM `erp-platform-internal-services-client`
> + `erp` tenant V0018 seed shipped in [TASK-MONO-119](../../../../../tasks/done/)
> (ADR-MONO-016 ACCEPTED, Option C). Sections describe the **target v1
> implementation**; the impl PR follows this spec.

---

## Identity

| Field | Value |
|---|---|
| Service Name | `masterdata-service` |
| Project | `erp-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal** (Ports & Adapters) |
| Domain | erp |
| Traits | internal-system, transactional, audit-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Organization Master Data (Department hierarchy / Employee org-attributes / JobGrade / CostCenter / BusinessPartner; effective-dated; v1 single deployable; Approval Workflow + Integrated Read Model forward-declared as v2 services) |
| Deployable unit | `apps/masterdata-service/` |
| Data store | MySQL `erp_db` (Flyway) |
| Event publication | Kafka via transactional outbox (`libs/java-messaging` `BaseEventPublisher`) — see § Outbox + audit_log invariants |
| Outbound integration | None real in v1 — internal-only (E7); IAM JWKS for JWT verification only |

### Service Type Composition

`masterdata-service` is a single-type **`rest-api`** service per
[`platform/service-types/INDEX.md`](../../../../../platform/service-types/INDEX.md).
All v1 responsibilities (master-data CRUD + effective-dated revisions +
retire/move-parent + point-in-time queries) are exposed through the
synchronous HTTP request/response surface. Kafka publication is a **side
effect** of REST mutations through the transactional outbox and, per
`platform/service-types/INDEX.md` ("REST service that also publishes events
→ `rest-api`"), **does not promote the service to `event-consumer`** —
identical reasoning to `finance-platform/account-service` and
`scm-platform/procurement-service`.

**Reconciliation of `TASK-ERP-BE-001 §1` framing** — the task lists the
Service Type as `rest-api` and references "(해당 시)" event publication. The
architecture.md is the authoritative place for the Service Type decision
(HARDSTOP-09/10; CLAUDE.md Source-of-Truth: `platform/service-types/` >
task). v1 masterdata-service does not *primarily react to* inbound domain
events; it publishes its own (the integrated read model v2 will be the
inbound consumer side). `PROJECT.md`'s project-level `service_types:
[rest-api]` matches. If a v1 inbound subscription is later added it becomes
`rest-api + event-consumer (<trigger>)` — a clarification, not a
re-classification (INDEX.md note 2).

---

## Responsibilities

`masterdata-service` owns the v1 **organization master-data lifecycle** for
erp-platform. It MUST:

- Manage the five master aggregates — `Department` (hierarchical),
  `Employee` (organization attributes), `JobGrade`, `CostCenter`,
  `BusinessPartner` — with create / update / retire / point-in-time-query
  use cases (E1·E2).
- Enforce **reference integrity** (E1): a master record that is still
  referenced by another master MUST NOT be physically deleted or implicitly
  retired through a cascading delete; retirement is a logical state
  transition guarded by a structural reference check
  (`MASTERDATA_REFERENCE_VIOLATION`). Natural-key uniqueness per master
  (`MASTERDATA_DUPLICATE_KEY`). The `Department` parent chain must remain
  acyclic (`MASTERDATA_PARENT_CYCLE`).
- Manage **effective-dated** master revisions (E2): every mutating use case
  appends a new revision with `effectiveFrom` / `effectiveTo` rather than
  overwriting the prior row; point-in-time reads (`?asOf=`) resolve the
  revision effective at the supplied timestamp. Overlapping periods for the
  same natural key → `MASTERDATA_EFFECTIVE_PERIOD_INVALID`.
- Enforce the **authorization matrix + data scope** (E6) — every read and
  write traverses a single un-bypassable `AuthorizationPort` that resolves
  the caller's role-set AND organization scope (descendant departments)
  before any repository call. Fail-CLOSED on missing role/scope
  (`PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`).
- Append every mutation (create / update / retire / move-parent / authorization
  decision recorded with `actor` / `occurred_at` / `before_state` /
  `after_state` / `reason`) to an immutable append-only `audit_log` in the
  same transaction as the mutation itself (E2·E8). UPDATE/DELETE on
  `audit_log` is structurally blocked.
- Validate IAM RS256 JWT (OAuth2 Resource Server) and fail-closed on
  `tenant_id ∉ {erp, *}` (defense-in-depth, § Multi-tenancy). Reject
  external traffic at the network boundary (E7 — `EXTERNAL_TRAFFIC_REJECTED`).
- Publish `erp.masterdata.<aggregate>.changed.v1` Kafka events through the
  transactional outbox (forward integration point for the v2
  `read-model-service` / `approval-service` / `notification-service`).

It MUST NOT:

- Implement approval-workflow state machines, approver routing, inbox views,
  or delegation — `approval-service` v2 (ADR-MONO-016 § D3).
- Implement the integrated read model that subscribes to other domains'
  events — `read-model-service` v2 (ADR-MONO-016 § D3; E5 read-only
  boundary).
- Own permission-matrix CRUD, notification fan-out, or the operator admin
  console — `permission-service` / `notification-service` / `admin-service`
  v2 (`PROJECT.md` Service Map v2).
- Re-implement domain business logic owned by other systems
  (procurement / inventory / orders / accounting). erp owns master data
  identity + effective-dating + audit only (E5 — `READ_MODEL_SOURCE_UNAVAILABLE`
  is the v2 read-model code, not a v1 surface here).
- Couple to network / directory / vendor SDKs in `domain/` or
  `application/` — must stay behind `infrastructure/` ports
  (`rules/domains/erp.md` Forbidden Patterns).
- Expose any public, self-signup, or anonymous endpoint surface (E7) —
  `/actuator/{health,info}` is the only unauthenticated path; `/prometheus`
  is network-isolated.

---

## Architecture Style Rationale

**Hexagonal (Ports & Adapters)** chosen because:

1. **`audit_log` + persistence + event publication are swappable outbound
   concerns** — `AuditLogPort`, `MasterRepository` family, and the outbox
   publisher sit behind ports. v1 ships MySQL JPA adapters and a
   `BaseEventPublisher`-derived outbox; v2 read-model / approval consumers
   are wired only against the published topics, never the persistence
   internals.
2. **Effective-dating + reference-integrity invariants must be framework-free
   and exhaustively unit-tested** — `Department.moveParent(...)` (cycle
   guard), `EffectivePeriod.overlapsWith(...)` (period-overlap guard), and
   the retirement-blocked-by-reference rule are pure Java (no Spring/JPA in
   the invariant logic) so the E1·E2 invariants are provable by fast unit
   tests.
3. **The authorization matrix must be un-bypassable through a single
   application path** (E6) — one `AuthorizationPort` invocation per use case
   funnels every read and write through role-set + data-scope evaluation
   before any repository call. Hexagonal makes "no other path to the
   `MasterRepository`" structurally enforceable; the controller / presentation
   layer holds no `@Transactional` and no direct repository handle.
4. **Testability** — domain unit (no Spring; aggregate invariants + period
   math + cycle detection) + application unit (mock ports + STRICT_STUBS) +
   `@WebMvcTest` slice (SecurityConfig + `GlobalExceptionHandler`
   error-envelope) + Testcontainers integration (MySQL — **H2 forbidden**;
   parity with production MySQL Hibernate type bindings — the finance V1__init
   MySQL lesson applies here too).

Aligns with `platform/architecture-decision-rule.md` and the default
Hexagonal expectation for `transactional` services. `gateway-service`
(v1 deferred per `PROJECT.md` Service Map; activated later) will be the
single intentional Layered exception when introduced.

---

## Layer Structure

Hexagonal variant — `presentation/` is the inbound web adapter,
`infrastructure/` aggregates outbound adapters + config. Root package
`com.example.erp.masterdata` (matches the TASK-MONO-119 skeleton
`group = 'com.example.erp.masterdata'`).

```
com.example.erp.masterdata/
├── MasterdataServiceApplication.java         ← skeleton (TASK-MONO-119)
├── domain/                                   ← pure Java, no framework
│   ├── department/
│   │   ├── Department.java                   ← aggregate root (hierarchical)
│   │   ├── DepartmentId.java
│   │   ├── DepartmentCode.java               ← natural key VO
│   │   ├── ParentChain.java                  ← cycle guard (pure)
│   │   └── repository/DepartmentRepository.java       ← outbound port
│   ├── employee/
│   │   ├── Employee.java                     ← aggregate root (org attributes)
│   │   ├── EmployeeId.java
│   │   ├── EmployeeNumber.java               ← natural key VO
│   │   └── repository/EmployeeRepository.java
│   ├── jobgrade/
│   │   ├── JobGrade.java                     ← aggregate root
│   │   ├── JobGradeCode.java                 ← natural key VO (incl. ordering)
│   │   └── repository/JobGradeRepository.java
│   ├── costcenter/
│   │   ├── CostCenter.java                   ← aggregate root (references Department)
│   │   ├── CostCenterCode.java
│   │   └── repository/CostCenterRepository.java
│   ├── businesspartner/
│   │   ├── BusinessPartner.java              ← aggregate root
│   │   ├── BusinessPartnerCode.java
│   │   ├── PaymentTerms.java                 ← VO
│   │   └── repository/BusinessPartnerRepository.java
│   ├── effectivedate/
│   │   ├── EffectivePeriod.java              ← VO (effectiveFrom / effectiveTo)
│   │   ├── EffectiveRevision.java            ← per-revision identity (id + period)
│   │   └── PointInTime.java                  ← asOf resolution policy (pure)
│   ├── reference/
│   │   ├── ReferenceChecker.java             ← retirement-blocked-by-reference (pure rules)
│   │   └── ReferencingAggregate.java         ← who-points-at-whom matrix
│   ├── authorization/
│   │   ├── Role.java                         ← role identifier VO
│   │   ├── DataScope.java                    ← org-scope VO (set of department subtrees)
│   │   └── AuthorizationDecision.java        ← (allow|deny + reason)
│   ├── audit/
│   │   ├── AuditLog.java
│   │   └── AuditLogRepository.java           ← outbound port (append-only)
│   └── error/                                ← domain exceptions (erp codes)
│       (MasterdataNotFoundException, MasterdataDuplicateKeyException,
│        MasterdataReferenceViolationException, MasterdataParentCycleException,
│        MasterdataEffectivePeriodInvalidException,
│        PermissionDeniedException, DataScopeForbiddenException, ...)
├── application/                              ← use cases + outbound ports
│   ├── MasterdataApplicationService.java     ← @Transactional command boundary
│   ├── ActorContext.java
│   ├── view/                                 ← read-model DTOs (DepartmentView, EmployeeView, ...)
│   ├── command/                              ← CreateDepartmentCommand, UpdateEmployeeCommand,
│   │                                            RetireCostCenterCommand, MoveParentCommand, ...
│   ├── event/
│   │   └── MasterdataEventPublisher.java     ← extends BaseEventPublisher (libs/java-messaging)
│   └── port/outbound/
│       ├── AuthorizationPort.java            ← role-set + data-scope evaluator (un-bypassable)
│       ├── ClockPort.java
│       └── IdempotencyStore.java             ← DB-table dedupe (Redis not wired v1; see § Idempotency)
├── infrastructure/                           ← outbound adapters + config
│   ├── persistence/jpa/                      ← Spring Data + adapter beans (toDomain/fromDomain)
│   │   (DepartmentJpaEntity/Repository/Adapter, EmployeeJpaEntity..., JobGradeJpaEntity...,
│   │    CostCenterJpaEntity..., BusinessPartnerJpaEntity..., AuditLogJpaEntity...,
│   │    outbox + processed_events + idempotency_keys)
│   ├── outbox/MasterdataOutboxPollingScheduler.java   ← extends libs OutboxPollingScheduler
│   ├── authorization/JwtBackedAuthorizationAdapter.java ← maps JWT roles+scope → AuthorizationDecision
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   ├── ServiceLevelOAuth2Config.java
│   │   ├── AllowedIssuersValidator.java
│   │   ├── TenantClaimValidator.java
│   │   ├── ActorContextResolver.java
│   │   └── ActorContextJwtAuthenticationConverter.java
│   └── config/ (ClockConfig, JpaConfig)
└── presentation/                             ← inbound web adapter
    ├── controller/
    │   ├── DepartmentController.java         ← /api/erp/masterdata/departments/**
    │   ├── EmployeeController.java
    │   ├── JobGradeController.java
    │   ├── CostCenterController.java
    │   └── BusinessPartnerController.java
    ├── advice/GlobalExceptionHandler.java    ← domain → HTTP envelope (erp codes)
    ├── dto/                                  ← request / response DTOs
    ├── filter/TenantClaimEnforcer.java       ← service-level fail-closed
    └── security/PublicPaths.java
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
  (per skeleton `build.gradle`; `data-redis` is wired but not used as the
  primary idempotency store in v1 — see § Idempotency).
- `org.springframework.kafka:spring-kafka` (transitive through
  `libs:java-messaging`).
- `org.flywaydb:flyway-core`, `flyway-mysql`, `com.mysql:mysql-connector-j`
  (runtime).
- `io.micrometer:micrometer-registry-prometheus`,
  `micrometer-tracing-bridge-otel`,
  `io.opentelemetry:opentelemetry-exporter-otlp`.
- `com.fasterxml.jackson.{core:jackson-databind, datatype:jackson-datatype-jsr310}`.
- `net.logstash.logback:logstash-logback-encoder` (prod profile).
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`,
  `libs:java-observability`, `libs:java-security`.

### Forbidden dependencies

- Network directory / LDAP / external HR SDKs in `domain/` or `application/` —
  must be behind `infrastructure/` ports if ever introduced. erp v1 has
  none real (E7 internal-only; the only outbound external dependency is
  IAM JWKS for JWT verification, which `libs:java-security` already wraps).
- Persistence frameworks beyond `spring-boot-starter-data-{jpa,redis}` — no
  reactive variants (Servlet stack).
- Direct cross-tenant repository methods that omit `tenant_id` — every
  repository signature carries `tenant_id` (defense-in-depth, even though
  erp is single-tenant; mirrors finance/scm).
- Direct write paths into another domain's authoritative tables (E5) —
  procurement/inventory/order/accounting facts remain owned by their
  source-of-record systems; erp must not pre-build a half ledger or a
  half procurement table.

### Boundary rules

- `domain/` MUST NOT depend on Spring (JPA annotations on entities are the
  single allowed exception; `EffectivePeriod`, `ParentChain`,
  `ReferenceChecker`, and `AuthorizationDecision` are pure).
- `application/MasterdataApplicationService` is the **only**
  `@Transactional` command boundary — controllers MUST NOT carry
  `@Transactional`.
- Every read and write MUST pass through the single application path that
  invokes `AuthorizationPort.evaluate(...)` BEFORE any repository call —
  no other entry point to the repositories exists (E6 structural
  enforcement). `JwtBackedAuthorizationAdapter` is the v1 adapter; v2 may
  swap in a real `permission-service` client behind the same port.
- Every mutating use case MUST append exactly one `AuditLog` row in the
  same `@Transactional` boundary as the master mutation + outbox row
  (E2·E8 atomicity).
- `presentation/controller/` MUST NOT touch JPA repositories directly — all
  persistence flows through `application/` use cases.
- `presentation/filter/TenantClaimEnforcer` is defense-in-depth only —
  gateway (when introduced) + JWT validator chain are the primary tenant
  gate.
- `domain/department/Department.moveParent(...)` MUST refuse to set a
  descendant or self as the new parent (cycle guard) — pure unit-testable
  invariant; the persistence adapter never observes a cyclic intermediate
  state.

---

## Aggregate lifecycles (v1)

Each aggregate is `ACTIVE` or `RETIRED` (logical) and every transition
appends an `audit_log` row in the same Tx (E2·E8). Physical deletion is
blocked while the row is referenced (E1). Effective-dated revisions are
the unit of update — a `PATCH` does not overwrite the prior row but
appends a new revision with `effectiveFrom = now` (or an operator-supplied
future date, validated for non-overlap).

### Department

Hierarchical. Each `Department` has at most one parent (`parentId` nullable
for the root). The set of `Department` rows is a forest — cycle-free at
all times (E1). `moveParent(newParentId)` traverses ancestry of
`newParentId` and refuses if `self` is on that ancestry path
(`MASTERDATA_PARENT_CYCLE`). Logical retire is blocked while any
`Employee` / `CostCenter` references this department in its current
effective revision (`MASTERDATA_REFERENCE_VIOLATION`).

```
ACTIVE
  ├─(operator retire, no live references)→ RETIRED ★
  └─(move-parent, no cycle)→ ACTIVE (new revision, audit_log row)
RETIRED ★ (terminal; reactivation = new effective revision via PATCH only)
```

### Employee

Organization attributes only (HR depth — payroll/attendance/evaluation — is
out of scope per `PROJECT.md` v1 OUT). Each effective revision carries the
employee's `departmentId` + `costCenterId` + `jobGradeId`. Reference
integrity: those three referenced masters must be `ACTIVE` (or remain
referenced through the prior effective revision — retirement of a still-referenced
master is blocked at the *referenced* master, not the referencing one).

```
ACTIVE
  └─(operator retire, …)→ RETIRED ★
RETIRED ★
```

### JobGrade

Salary-grade ordering (display order field). Logical retire blocked while
referenced by any active `Employee` revision.

```
ACTIVE
  └─(operator retire, no live references)→ RETIRED ★
RETIRED ★
```

### CostCenter

References one `Department` per effective revision. Logical retire blocked
while referenced by any active `Employee` revision or by any external
domain (the cross-domain reference check is forward-decl — v1 has no
inbound `read-model` subscribers; v2 `read-model-service` will surface the
external-reference shape; v1 enforces only the internal-master references).

```
ACTIVE
  └─(operator retire, …)→ RETIRED ★
RETIRED ★
```

### BusinessPartner

External counterparty (customer / supplier / both). `paymentTerms` is an
embedded VO updated through new effective revisions. v1 has no real
integration with procurement/finance — those domains read events for
their own caches (forward integration).

```
ACTIVE
  └─(operator retire, no live references)→ RETIRED ★
RETIRED ★
```

★ terminal logical state. Physical deletion never exposed via REST in v1.

---

## Reference Integrity model (E1)

```
Employee.departmentId           → Department      (must be ACTIVE in employee's effective rev)
Employee.costCenterId           → CostCenter      (same)
Employee.jobGradeId             → JobGrade        (same)
CostCenter.departmentId         → Department      (same)
Department.parentId             → Department      (nullable; cycle-free)
```

`ReferenceChecker.canRetire(target)` enumerates all aggregates that could
reference `target` and verifies none has an *active* effective revision
pointing at `target.id`. The check runs inside the retire use case's
`@Transactional` boundary (so a concurrent reference create-or-update
cannot slip in between check and retire; v1 uses table-level optimistic
locks on the referenced aggregate + `@Version` on the referencing
aggregates — see § Failure Modes #14).

Errors:
- `MASTERDATA_REFERENCE_VIOLATION` — retire blocked by ≥1 live referencer.
- `MASTERDATA_DUPLICATE_KEY` — natural-key collision on create (per-aggregate
  unique constraint).
- `MASTERDATA_PARENT_CYCLE` — `Department.moveParent` would close a cycle.

---

## Effective-dating model (E2)

Each aggregate's persistence row carries `(id, effective_from, effective_to,
…business fields…)`. The `(id, effective_from)` tuple is unique. The
"current" revision is the one with `effective_from ≤ now < effective_to`
(or `effective_to IS NULL` for open-ended); a future revision has
`effective_from > now`.

- `effective_from` / `effective_to` are `DATE` for `Department`, `JobGrade`,
  `CostCenter`, `BusinessPartner` (day granularity is the operational
  boundary for org-master changes).
- For `Employee` the same columns are `DATE` too — the v1 surface does not
  need sub-day granularity for org-attribute changes (assignment effective
  dates are operationally "from <day>").

**Overlap rule**: for the same natural-key (per aggregate), the set of
`[effective_from, effective_to)` intervals must be pairwise disjoint.
Insertion of a revision whose interval overlaps an existing one →
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`. Insertion with
`effective_to ≤ effective_from` is also rejected with the same code.

**Point-in-time read** (`?asOf=<ISO-8601-DATE>`): the application use case
filters revisions by `effective_from ≤ asOf < effective_to` (or open-ended)
and returns the single row that was effective on that date. Default
`asOf = today (UTC)` if the query parameter is absent. Required for the
E2 reproducibility AC.

---

## Authorization matrix + Data scope (E6)

Single un-bypassable application path:

1. Every use case method begins with `AuthorizationPort.evaluate(actor,
   role-required, target-data-scope) → AuthorizationDecision`. Decision =
   `ALLOW` or `DENY(reason)`. `DENY` short-circuits to a domain exception
   (`PermissionDeniedException` for missing role, `DataScopeForbiddenException`
   for out-of-scope data) before the repository is touched.
2. `Role` is derived from the JWT's `scope` claim
   (`erp.read` / `erp.write` minimum; per-aggregate roles e.g.
   `erp.masterdata.department.write` reserved for future fine-grained
   matrix work — v1 uses the coarse scope).
3. `DataScope` is derived from the JWT's `org_scope` claim (a set of
   `departmentId`s the caller may read/write under). For
   `client_credentials` (machine-to-machine, internal services), the
   claim resolves to the platform-wide `*` scope (defense-in-depth
   gateway enrichment described in `specs/integration/iam-integration.md`
   § "sub claim of client_credentials tokens"). For human operators
   (v2 user-flow), the claim is resolved from the user's organizational
   membership. **v1 bridge (TASK-BE-337)**: the console **assume-tenant
   operator token** (whose domain authorization rides `roles ∋ ERP_OPERATOR`
   — derived by IAM at assume-tenant from the selected tenant's entitled
   domains, ADR-MONO-035 O1 / step 4a; the legacy `account_type=OPERATOR`
   claim of ADR-MONO-020 D4 was removed at ADR-MONO-032 D5 step 4) is enriched
   by IAM (`TenantClaimTokenCustomizer.customizeForAssumeTenant`) with
   `org_scope=["*"]` — the same platform-wide default as
   `client_credentials`, scoped WITHIN the already-tenant-gated request
   (the tenant gate isolates cross-tenant, so `*` = all departments of the
   assumed tenant). This unblocks the console erp department write pilot
   (TASK-PC-FE-046) until v2 membership-derived subtree scoping lands; the
   per-subtree fail-closed default (point below) is otherwise unchanged.

   **v2 membership-derived (TASK-BE-338 / TASK-ERP-BE-008, ADR-MONO-020 D3
   amendment 2026-06-05)** — supersedes the `["*"]` bridge for the
   assume-tenant operator token: IAM injects the operator's **actual**
   `org_scope` from the per-assignment source
   (`operator_tenant_assignment.org_scope`, admin-service — `NULL ⟺ ["*"]`
   = whole tenant, net-zero). The claim carries department **subtree-root**
   ids (NOT the expanded set — IAM does not know erp's department tree).
   `RoleScopeAuthorizationAdapter` therefore **expands each root → its
   descendants via the `department` hierarchy** and authorizes when the
   target department is within ANY scoped subtree (replacing the flat
   `dataScopeDepartmentIds.contains(target)` exact-match). `"*"` remains the
   platform-wide bypass (machine tokens + unscoped assignments). The
   fail-CLOSED default (no `org_scope` + non-null target → `DATA_SCOPE_FORBIDDEN`)
   is unchanged. The subtree walk terminates on the producer's parent-cycle
   invariant (§ Department — `MASTERDATA_PARENT_CYCLE`); depth-bounded
   defensively.
4. **Fail-CLOSED default** — if the JWT lacks a recognizable role/scope,
   the decision is `DENY`. There is no allow-by-default codepath.

**Entitlement-trust READ dual-accept** (ADR-MONO-019 § D5, TASK-MONO-161) —
the `RoleScopeAuthorizationAdapter` **READ** branch also accepts a signed
`entitled_domains ∋ "erp"` claim, mirroring the tenant gate's
`TenantClaimValidator.isEntitled` dual-accept at the **role/scope authorization
layer**:

```
READ  authorized when:  erp.read ∨ erp.write ∨ isOperator() ∨ isEntitledTo("erp")
WRITE authorized when:  erp.write ∨ isOperator()        ← entitlement NEVER widens WRITE
```

Rationale: when MONO-161 landed, an operator viewing a customer they are
*entitled* to (via the assume-tenant token — `tenant_id=<customer>` +
`entitled_domains=[…,erp]`) carried no `erp.read`/operator role, yet the tenant
gate already admitted them; without the dual-accept the **separate** authz layer
would still 403 the entitled-but-no-role token (the tenant gate and the
role/scope authz layer are independent gates, both must dual-accept). Since
ADR-MONO-035 step 4a that same assume-tenant token **also** carries
`roles ∋ ERP_OPERATOR` (derived from the selected tenant's entitled domains —
the `account_type=OPERATOR` claim of ADR-MONO-020 D4 having been removed at
ADR-MONO-032 D5 step 4), so `isOperator()` now admits it directly and the
`isEntitledTo("erp")` branch is retained **net-zero** as defense-in-depth (and
for any entitled token shape lacking the derived operator role) — the live
authorization code is unchanged.
`entitledDomains` is lifted from the RS256/JWKS-verified JWT by
`ActorContextJwtAuthenticationConverter` (fail-closed on shape anomaly:
absent / non-list / non-string element → empty set, mirroring
`safeStringList`). **Net-zero**: SUPER_ADMIN / scope-bearing /
`client_credentials` tokens authorize exactly as before (the change only ADDS
an OR branch). **READ-only**: WRITE/mutation stays scope/role-gated, and the
**targeted** data-scope check is unchanged — an entitlement-trust READ with a
non-null `targetDepartmentId` and no `org_scope` still fails closed
(`DATA_SCOPE_FORBIDDEN`); entitlement-trust grants the **READ-overview**
(`targetDepartmentId == null`) granularity only.

The matrix is per-use-case (declared in `MasterdataApplicationService`
method signatures via constants), not a generic interceptor — the
declaration is auditable by reading the use-case source.

`v1` carries the role/scope claims through the JWT only; the
`JwtBackedAuthorizationAdapter` materializes the decision. `v2`
`permission-service` swaps the adapter behind the same `AuthorizationPort`
without touching the application.

Errors:
- `PERMISSION_DENIED` (403) — required role not present.
- `DATA_SCOPE_FORBIDDEN` (403) — the target row's owning department is
  outside the caller's data scope (descendant departments only).

---

## Outbox + audit_log invariants

Transactional outbox (`libs/java-messaging` `BaseEventPublisher` +
`MasterdataOutboxPollingScheduler extends OutboxPollingScheduler`): every
event write shares the use-case `@Transactional` boundary (E2 atomicity).
Source = `"erp-platform-masterdata-service"`. Topics → § contract
[`erp-masterdata-events.md`](../../contracts/events/erp-masterdata-events.md).

`audit_log` (append-only, no UPDATE/DELETE, written in the same Tx) records
**every** mutation: aggregate create / effective-revision append / retire /
move-parent. Columns: `actor` (JWT sub or operator id), `occurred_at`
(server clock), `aggregate_type` + `aggregate_id`, `before_state`
(JSON snapshot of the prior effective revision, or `null` on create),
`after_state` (JSON snapshot of the new revision, or `null` on retire),
`reason` (operator-supplied, required for retire and move-parent).

**Append-only enforcement** — chosen mechanism is **application-layer
guard** (no UPDATE/DELETE statements emitted by any adapter; the JPA
`AuditLogJpaRepository` exposes only `save(...)` and read queries; the
domain port `AuditLogRepository` exposes only `append(...)` and read).
**Rationale**: portability across MySQL versions and ease of
Testcontainers-driven verification, mirroring the finance
account-service precedent (`audit_log` no UPDATE/DELETE). A DB-level
trigger may be retrofitted in v2 as defense-in-depth without affecting the
application contract.

The combined invariant (Audit + Outbox + Master mutation atomic): a use
case completes only if the row mutation, the audit_log append, and the
outbox event write all commit in the same Tx. The outbox poller retries
publish-side failures separately (E2 reproducibility is unaffected by
broker downtime).

---

## Multi-tenancy

erp-platform is **not** internally multi-tenant (single-org internal
system per `PROJECT.md` Out-of-Scope `multi-tenant`). IAM supplies
`tenant_id = erp`. Defense-in-depth (mirrors finance / scm):

1. **Gateway** (v1 deferred) — domain gate at JWT decode.
2. **Service JWT validator chain** — `AllowedIssuersValidator` (SAS issuer
   + legacy `iam-platform` D2-b window — byte-identical to the
   future gateway's allowed-issuers) + `TenantClaimValidator`.
3. **Service filter** — `TenantClaimEnforcer` → 403 `TENANT_FORBIDDEN` when
   the gate rejects (public paths skipped).

**Domain gate — entitlement-trust dual-accept** (ADR-MONO-019 § D5, mirrors
the finance pilot). Both enforcement points (`TenantClaimValidator` at decode
time and `TenantClaimEnforcer` filter) apply the *same* rule via the shared
`TenantClaimValidator.isEntitled(jwt, domain)` helper (single source of truth
— a split would let entitled traffic pass decode yet be blocked by the
filter). A token is accepted when **either**:

- **(legacy slug)** `tenant_id ∈ {erp, *}` — `*` is SUPER_ADMIN
  platform-scope; **or**
- **(entitlement-trust)** the IAM-signed `entitled_domains` claim (a list of
  domain keys) contains `erp`.

Rejection (403 `TENANT_FORBIDDEN`) requires **both** branches to fail
(fail-closed; entitlement only *widens* the allowed set, never weakens the
legacy reject). `entitled_domains` is read only from an RS256/JWKS-verified
token, so it is unforgeable — **IAM is the entitlement authority**; a
non-list / null / empty / non-string-element claim degrades to "not entitled".
Row-level isolation is unchanged: row scoping still keys off `tenant_id`, so
an entitled cross-slug token sees only its own `tenant_id` partition. While
IAM has not yet populated `entitled_domains` the claim is absent → only the
legacy path applies → **production net-zero**. This is the ADR-MONO-019
**dual-accept window**; the legacy `tenant_id == slug` branch is removed in
step 4 once IAM populates the claim (separate follow-up).

Config keys (TASK-MONO-119 skeleton `application.yml`):
`erpplatform.oauth2.allowed-issuers` + `.required-tenant-id=erp`.
Every persistence table carries `tenant_id VARCHAR(64) NOT NULL DEFAULT
'erp'`; repository methods always embed `tenant_id` in `WHERE` (no
tenant-omitting method exists — even though only `erp` is expected, the
column is the structural guard against accidental cross-project data
pollution).

---

## Security

- **JWT (RS256)**: `oauth2-resource-server` against
  `${OIDC_ISSUER_URL:http://iam.local}/oauth2/jwks`; RS256 only;
  `JwtTimestampValidator` + `AllowedIssuersValidator` + `TenantClaimValidator`.
  IAM `erp-platform-internal-services-client` (client_credentials,
  scopes `erp.read` / `erp.write`, V0018) is the v1 caller.
- **External-traffic rejection (E7)** — `EXTERNAL_TRAFFIC_REJECTED` is
  enforced at two layers:
  1. **Network** — Docker Compose `erp.local` Traefik label on an
     `internal: true` Docker network. The shared Traefik ingress accepts
     only requests originating from the platform-console / internal LAN
     CIDR allow-list. External (public-internet) traffic never reaches
     the service.
  2. **Application** — `PublicPaths` filter rejects any request to a
     non-actuator path that arrives without a valid JWT
     (`UNAUTHORIZED` → never `EXTERNAL_TRAFFIC_REJECTED` in v1; the
     externally-rejected case is the network layer above). The error code
     is registered so a future ingress-policy bypass surface (e.g.
     debugging endpoint) can emit it deterministically.
- **Public paths**: `/actuator/{health,info}` only; `/actuator/prometheus`
  is network-isolated (Docker internal network only); all else JWT or
  `denyAll()`. (No v1 webhook surface — internal-only.)
- **No self-signup, no anonymous endpoints** (E7 Forbidden Patterns).

---

## REST endpoints (v1)

All under `/api/erp/masterdata/**` (gateway, when introduced, rewrites
`/api/v1/erp/masterdata/**`). Formal shapes →
[`masterdata-api.md`](../../contracts/http/masterdata-api.md).

| Method | Path | Auth | Idempotency | Use case |
|---|---|---|---|---|
| `POST` | `/api/erp/masterdata/departments` | JWT (`erp.write`) | required | create department |
| `GET` | `/api/erp/masterdata/departments` | JWT (`erp.read`) | n/a | list (scope-aware, `?asOf=&active=&page=&size=`) |
| `GET` | `/api/erp/masterdata/departments/{id}` | JWT (`erp.read`) | n/a | detail (`?asOf=`) |
| `PATCH` | `/api/erp/masterdata/departments/{id}` | JWT (`erp.write`) | required | append effective revision |
| `POST` | `/api/erp/masterdata/departments/{id}/retire` | JWT (`erp.write`) | required | logical retire (reference-checked) |
| `POST` | `/api/erp/masterdata/departments/{id}/move-parent` | JWT (`erp.write`) | required | move-parent (cycle-checked) |
| `POST` | `/api/erp/masterdata/employees` | JWT (`erp.write`) | required | create employee |
| `GET` | `/api/erp/masterdata/employees` | JWT (`erp.read`) | n/a | list |
| `GET` | `/api/erp/masterdata/employees/{id}` | JWT (`erp.read`) | n/a | detail |
| `PATCH` | `/api/erp/masterdata/employees/{id}` | JWT (`erp.write`) | required | append revision |
| `POST` | `/api/erp/masterdata/employees/{id}/retire` | JWT (`erp.write`) | required | retire |
| `POST` | `/api/erp/masterdata/job-grades` | JWT (`erp.write`) | required | create |
| `GET` | `/api/erp/masterdata/job-grades` | JWT (`erp.read`) | n/a | list |
| `GET` | `/api/erp/masterdata/job-grades/{id}` | JWT (`erp.read`) | n/a | detail |
| `PATCH` | `/api/erp/masterdata/job-grades/{id}` | JWT (`erp.write`) | required | append revision |
| `POST` | `/api/erp/masterdata/job-grades/{id}/retire` | JWT (`erp.write`) | required | retire |
| `POST` | `/api/erp/masterdata/cost-centers` | JWT (`erp.write`) | required | create |
| `GET` | `/api/erp/masterdata/cost-centers` | JWT (`erp.read`) | n/a | list |
| `GET` | `/api/erp/masterdata/cost-centers/{id}` | JWT (`erp.read`) | n/a | detail |
| `PATCH` | `/api/erp/masterdata/cost-centers/{id}` | JWT (`erp.write`) | required | append revision |
| `POST` | `/api/erp/masterdata/cost-centers/{id}/retire` | JWT (`erp.write`) | required | retire |
| `POST` | `/api/erp/masterdata/business-partners` | JWT (`erp.write`) | required | create |
| `GET` | `/api/erp/masterdata/business-partners` | JWT (`erp.read`) | n/a | list |
| `GET` | `/api/erp/masterdata/business-partners/{id}` | JWT (`erp.read`) | n/a | detail |
| `PATCH` | `/api/erp/masterdata/business-partners/{id}` | JWT (`erp.write`) | required | append revision |
| `POST` | `/api/erp/masterdata/business-partners/{id}/retire` | JWT (`erp.write`) | required | retire |
| `GET` | `/actuator/{health,info}` | none | n/a | probes / build info |
| `GET` | `/actuator/prometheus` | network-isolated | n/a | metrics scrape (internal docker network only) |

Endpoint count = 5 masters × (1 create + 1 list + 1 detail + 1 patch +
1 retire) + 1 Department move-parent = **26 business endpoints** + 2
actuator probes = **28 total**.

---

## Idempotency

All mutating endpoints require `Idempotency-Key` (missing → 400
`IDEMPOTENCY_KEY_REQUIRED`). `IdempotencyStore` port: **DB-table primary**
(`idempotency_keys` MySQL table). Redis is wired in
`spring-boot-starter-data-redis` (skeleton) but **not used as the primary
store in v1** — masterdata mutation traffic is operator-scale (low TPS),
the DB-table primary is sufficient, and removing Redis from the critical
path simplifies the fail-CLOSED matrix. If Redis is later added as primary
the port stays unchanged. Both-store-down would surface as
`IDEMPOTENCY_STORE_UNAVAILABLE` (503). The v1 DB-table primary is
reachable inside the same Tx as the mutation, so the common path never
raises it — but the fail-CLOSED store (`DbIdempotencyStore`) does emit
the 503 in v1 on the claim path's `DataAccessException` or
unresolved-insert-race branch (rare, but genuine v1 behaviour).

Same key + identical payload → first stored response replayed (no
re-mutation). Same key + different payload → 409
`IDEMPOTENCY_KEY_CONFLICT`. Key scope = `(idempotency_key, endpoint,
tenant_id)`.

---

## Dependencies

| Dir | Target | Protocol | Notes |
|---|---|---|---|
| In | erp `gateway-service` (v1 deferred) → direct JWT until then | HTTP `/api/erp/masterdata/**` | tenant-validated JWT |
| Out | MySQL `erp_db` | JDBC | `department`, `employee`, `job_grade`, `cost_center`, `business_partner`, `audit_log`, `outbox`, `processed_events`, `idempotency_keys` (all effective-dated where applicable) |
| Out | Kafka | TCP | `erp.masterdata.{department,employee,jobgrade,costcenter,businesspartner}.changed.v1`; `acks=all`, `enable.idempotence=true` |
| Out | IAM `/oauth2/jwks` | HTTPS | RS256 JWT verification (libs/java-security) |
| Out (obs) | OTLP collector | HTTPS | `${OTLP_ENDPOINT}` traces |

No cross-service inbound master-event consumption in v1 (masterdata-service
is a leaf — the `read-model-service` v2 will be the inbound consumer).

> **[Note — TASK-ERP-BE-007, 2026-06-04]** The inbound consumer now exists as a
> first increment: `read-model-service` (`rest-api` + `event-consumer`)
> subscribes to `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1`
> to project the integrated employee org-view. `masterdata-service` itself is
> **unchanged** — it remains the leaf producer (no inbound consumption here); the
> consumer is a separate deployable. See
> [`read-model-service/architecture.md`](../read-model-service/architecture.md).

---

## Observability

- Logback MDC `traceId / requestId / tenantId / userId` (libs/java-observability;
  pattern already in skeleton `application.yml`).
- Counters:
  - `masterdata_outbox_publish_failures_total`
  - `masterdata_reference_violation_total{kind}` — split per-aggregate-kind for
    operator dashboards.
  - `masterdata_scope_forbidden_total` — fail-closed authorization signal.
  - `masterdata_parent_cycle_blocked_total` — Department move-parent guard hits.
  - `masterdata_effective_period_invalid_total` — overlap-rejection signal.
- Tracing OTLP via `micrometer-tracing-bridge-otel`; sampling 1.0 (dev).
- `/actuator/prometheus` internal docker network only.

---

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Missing `Idempotency-Key` on mutation | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 2 | Same key, different payload | 409 `IDEMPOTENCY_KEY_CONFLICT` |
| 3 | Cross-tenant JWT — `tenant_id ∉ {erp,*}` **and** signed `entitled_domains ∌ erp` (dual-accept both branches fail) | 403 `TENANT_FORBIDDEN` |
| 4 | Missing JWT / invalid signature / expired | 401 `UNAUTHORIZED` |
| 5 | External (non-internal-network) traffic at ingress | rejected at Traefik / network layer; if surfaced through a debug path → 403 `EXTERNAL_TRAFFIC_REJECTED` |
| 6 | Caller lacks required role | 403 `PERMISSION_DENIED` |
| 7 | Target row's owning department outside caller scope | 403 `DATA_SCOPE_FORBIDDEN` |
| 8 | Unknown aggregate id | 404 `MASTERDATA_NOT_FOUND` |
| 9 | Natural-key collision on create | 409 `MASTERDATA_DUPLICATE_KEY` |
| 10 | Retire blocked by live referencer | 409 `MASTERDATA_REFERENCE_VIOLATION` |
| 11 | `Department.moveParent` would close a cycle | 409 `MASTERDATA_PARENT_CYCLE` |
| 12 | Effective period overlap / inverted | 422 `MASTERDATA_EFFECTIVE_PERIOD_INVALID` |
| 13 | Validation failure (bean-validation) | 400 `VALIDATION_ERROR` |
| 14 | Optimistic-lock conflict on concurrent update | 409 `CONCURRENT_MODIFICATION` |
| 15 | Outbox publish failure | row stays `PENDING`, retried next tick; counter increments |
| 16 | `audit_log` UPDATE/DELETE attempt | not exposed via any port; only application-bug surface → 500 `INTERNAL_ERROR` + alert |

---

## Testing Strategy

- **Unit** (`:masterdata-service:test`):
  - domain — `ParentChainTest` (cycle-detection matrix),
    `EffectivePeriodTest` (overlap / inverted period rejection),
    `ReferenceCheckerTest` (per-aggregate referencer enumeration), each
    aggregate's invariants (`DepartmentTest`, `EmployeeTest`, …).
  - application — `MasterdataApplicationServiceTest`
    (`@ExtendWith(MockitoExtension.class)` STRICT_STUBS, one happy + edge
    per Command).
  - adapters — validator unit tests, `TenantClaimEnforcerTest`,
    `JwtBackedAuthorizationAdapterTest` (role + scope resolution matrix).
- **Slice**: JPA adapter slices, `@WebMvcTest` + SecurityConfig +
  `GlobalExceptionHandler` error-envelope.
- **Integration** (`:masterdata-service:integrationTest`,
  `@Tag("integration")`, Testcontainers MySQL + WireMock JWKS —
  **H2 forbidden**): create→list→detail→patch→retire happy path;
  cross-tenant JWT → 403 `TENANT_FORBIDDEN`; reference-integrity guard
  (retire blocked + retire-after-dereference works); effective-period
  overlap reject; parent-cycle reject; authorization fail-closed
  (missing role → 403); data-scope denial (out-of-subtree row → 403);
  `audit_log` append-only (every mutation = exactly one audit row,
  none observed UPDATE/DELETE'd in the same suite); point-in-time
  read reproducibility (`?asOf=<past>` returns the historic revision);
  optimistic-lock concurrency. `integrationTest` excluded from
  `./gradlew check` (Docker-free fast loop).

---

## Required Artifacts mapping (rules/domains/erp.md § Required Artifacts)

| # | Artifact | Disposition |
|---|---|---|
| 1 | Master-data model + reference-integrity map | **Inlined** here (§ Aggregate lifecycles + § Reference Integrity model) — finance/scm precedent (dedicated `data-model.md` = low-priority follow-up if the model grows) |
| 2 | Approval state diagram | **Deferred** — `approval-service` v2 (ADR-MONO-016 § D3) owns it |
| 3 | Permission matrix model | **Inlined, v1 surface** (§ Authorization matrix + Data scope); `permission-service` v2 will own the full matrix CRUD + storage |
| 4 | Integrated read model boundary map | **Deferred** — `read-model-service` v2 (ADR-MONO-016 § D3) owns the source-of-record map; v1 emits only the outbound events that populate it |
| 5 | internal-system boundary policy | **Inlined** (§ Security § Multi-tenancy) — gateway will be the dedicated artifact when activated |
| 6 | Error-code registration | This spec PR adds erp codes to `platform/error-handling.md` |
| 7 | Bounded-context map | v1 single deployable; context split → `PROJECT.md` Service Map v2 (approval-service / read-model-service / permission-service / notification-service / admin-service) |

---

## References

- `platform/architecture-decision-rule.md`, `platform/service-types/INDEX.md`,
  `platform/service-types/rest-api.md`, `platform/error-handling.md`,
  `platform/testing-strategy.md`, `platform/hardstop-rules.md` (HARDSTOP-09/10),
  `platform/shared-library-policy.md` (HARDSTOP-03)
- `rules/domains/erp.md` (E1–E8 — governing), `rules/traits/internal-system.md`,
  `rules/traits/transactional.md`, `rules/traits/audit-heavy.md` (trait rule
  files loaded if present per `rules/README.md` resolution order)
- `projects/erp-platform/PROJECT.md`,
  [`iam-integration.md`](../../integration/iam-integration.md)
- [`masterdata-api.md`](../../contracts/http/masterdata-api.md) (this PR),
  [`erp-masterdata-events.md`](../../contracts/events/erp-masterdata-events.md) (this PR)
- precedent: `projects/finance-platform/specs/services/account-service/architecture.md`
  (Hexagonal canonical-form shape reference);
  `projects/scm-platform/specs/services/procurement-service/architecture.md`
  (Hexagonal alt-precedent)
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D2/D3 (v1 =
  masterdata-service; approval/read-model = v2),
  `docs/adr/ADR-MONO-013-platform-console-foundation.md` § 3.3
  (backend-only; UI is the platform-console parity slice)
- TASK-MONO-119 — bootstrap (skeleton + IAM V0018), TASK-ERP-BE-001 — this
  spec + impl task
