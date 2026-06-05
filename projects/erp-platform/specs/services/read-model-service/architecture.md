# read-model-service — Architecture

## Identity

| Field | Value |
|---|---|
| Service Name | `read-model-service` |
| Service Type | `rest-api` + `event-consumer` |
| Architecture Style | **Hexagonal** |
| Domain | erp |
| Traits | internal-system, transactional, audit-heavy (project-level; this service exercises `transactional` T8 event idempotency + `internal-system` E5/E6/E7 boundaries — it is **not** an `audit-heavy` write surface, see § Outbox + audit_log invariants) |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Integrated Read Model (erp `rules/domains/erp.md` § Bounded Contexts) — read-only projection of organization master facts, **no domain business logic** (E5) |
| Deployable unit | `apps/read-model-service/` |
| Data store | MySQL `erp_read_model_db` schema (Flyway) — separate database from `masterdata-service`'s `erp_db` (CQRS read store; no shared tables) |
| Event publication | **None** — read-model is a terminal projection (E5: never re-emits authoritative master facts) |
| Event consumption | Kafka 4 topics from `masterdata-service` (same project): `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1` (EventDedupe idempotency) + 4 approval-transition topics `erp.approval.{submitted,approved,rejected,withdrawn}.v1` (TASK-ERP-BE-010, approval-fact projection) + 2 delegation topics `erp.approval.delegated.v1` + `erp.approval.delegation.revoked.v1` (TASK-ERP-BE-015, delegation-fact projection). `businesspartner` + `erp.permission.*` topics are **not** consumed (deferred / forward-declared) |

### Service Type Composition

`read-model-service` combines two service types in one deployable unit (CQRS
read-model role — same documented exception as scm `inventory-visibility-service`):

- `rest-api` for synchronous **read-only** queries (integrated employee org-view —
  the join of Employee × its Department subtree path × CostCenter × JobGrade).
  Read endpoints only; **no mutating REST** (E5 — the read-model owns no
  authoritative state).
- `event-consumer` for asynchronous **inbound** event subscription:
  - `erp.masterdata.department.changed.v1` → upsert `department_proj`
  - `erp.masterdata.employee.changed.v1` → upsert `employee_proj`
  - `erp.masterdata.jobgrade.changed.v1` → upsert `job_grade_proj`
  - `erp.masterdata.costcenter.changed.v1` → upsert `cost_center_proj`

Both surfaces share the same domain core (the four projection aggregates +
`EventDedupeRecord`) and persistence. Read **both**
`platform/service-types/rest-api.md` and `platform/service-types/event-consumer.md`
when implementing — documented exception to the "read exactly one service-type
file" rule, justified by the CQRS read-model role (inventory-visibility-service
precedent).

## Responsibilities

The erp integrated read model — first increment. Consumes `masterdata-service`'s
master-change events (same project, cross-service) and maintains denormalized
projection tables. Exposes a read-only REST API serving the **employee org-view**:
a single record showing an employee with the resolved department-hierarchy path,
cost center, and job grade — the cross-master join that the per-master
`masterdata-service` read API cannot serve in one call.

This is the inbound consumer side that `masterdata-service/architecture.md`
§ Dependencies forward-declared ("masterdata-service is a leaf — the
`read-model-service` v2 will be the inbound consumer"). It closes the
master-data change-propagation loop: a master mutation in `masterdata-service`
→ outbox → Kafka → this service's projection → operator-visible integrated view.

**Scope discipline (E5)**: this service projects facts read-only. It holds **no
domain business logic**, owns no aggregate state machine, and never re-emits or
mutates authoritative master facts. Each projected field has exactly one
source of record (`masterdata-service`).

> **[Amendment — TASK-ERP-BE-010, 2026-06-05] approval-fact projection (v1.1).**
> The integrated read model is extended to also consume the four
> `erp.approval.{submitted,approved,rejected,withdrawn}.v1` events
> (`approval-service`, [`read-model-subscriptions.md`](../../contracts/events/read-model-subscriptions.md))
> and maintain an `approval_fact_proj` projection (latest state per
> `approvalRequestId`), served by a read-only `GET /api/erp/read-model/approvals`
> list + `/{approvalRequestId}` detail ([`read-model-api.md`](../../contracts/http/read-model-api.md)).
> This **closes the `approval-service` → `read-model` event loop** (the approval
> events previously had zero consumers — `erp-approval-events.md` "Consumers in
> this increment = none"), mirroring the masterdata → read-model loop. It
> executes the integrated-full-view forward-declaration recorded in the
> ADR-MONO-016 §D3 read-model amendment ("the full integrated view —
> business-partner, approval/permission facts — stays v2-deferred") as a first
> increment for **approval facts** (business-partner / permission facts remain
> deferred). It is **additive** and stays within E5: the projection holds only
> the *latest fact* (status + ids + timestamps + last reason), NOT the
> authoritative transition `history` (which remains owned by `approval-service`
> — `GET /api/erp/approval/requests/{id}` is the source of record). No approval
> *business logic* is reconstructed (the state machine, authz, idempotency stay
> in approval-service); this service only projects the state it observes. The
> approval handlers join the existing `erp-read-model-v1` consumer group, reuse
> the `processed_events` dedupe (T8) + `@RetryableTopic` DLT resilience
> (ADR-MONO-005 Category C), and the list is org_scope-subtree-filtered on the
> subject's department (TASK-ERP-BE-008 read-filter parity). Subject display
> names + the subject's department (for the scope filter) are resolved at read
> time against `employee_proj`/`department_proj` (eventually-consistent, never
> fabricated). **Still no re-emission / no write-back / no publish (E5
> terminal).** Spec: this amendment + `read-model-subscriptions.md` (approval
> topics) + `read-model-api.md` (approval-fact endpoints).

> **[Amendment — TASK-ERP-BE-015] delegation-fact projection (v1.2).** The
> integrated read model is extended to also consume `erp.approval.delegated.v1`
> (grant create — previously the lone unconsumed approval topic) AND the NEW
> `erp.approval.delegation.revoked.v1` (grant revoke — added by the producer leg
> of this task; [`read-model-subscriptions.md`](../../contracts/events/read-model-subscriptions.md)
> § v1.2). It maintains a `delegation_fact_proj` projection (latest state per
> `grantId`: ACTIVE/REVOKED), served by a read-only
> `GET /api/erp/read-model/delegations` list + `/{grantId}` detail
> ([`read-model-api.md`](../../contracts/http/read-model-api.md) § Delegation
> facts). This **closes the `approval-service` → `read-model` delegation event
> loop** — the read answer to "who may act for whom". The grant→revoke accuracy
> requires the NEW revoke event: a create-only projection could never reflect a
> manual cancellation (only time-window expiry, evaluated read-time). It mirrors
> the approval-fact blueprint 1:1: the handlers join the existing
> `erp-read-model-v1` consumer group, reuse the `processed_events` dedupe (T8) +
> `@RetryableTopic` DLT resilience (ADR-MONO-005 Category C); invalid envelope
> (null `eventId`/`grantId`/`payload`, non-`erp` tenant) → immediate DLT. The
> projection is **sticky-terminal REVOKED** (last-event-wins; a late `delegated`
> after `revoked` never reverts) + **out-of-order tolerant** (a `revoked` before
> any `delegated` upserts a REVOKED row with the validity window ABSENT — no
> fabrication, E5). The list is org_scope-subtree-filtered on the **delegator's**
> department (TASK-ERP-BE-008 read-filter parity). **Still no re-emission / no
> write-back / no publish (E5 terminal).** Spec: this amendment +
> `read-model-subscriptions.md` (delegation topics) + `read-model-api.md`
> (delegation-fact endpoints) + `erp-approval-events.md` § v2.2 (the revoke
> producer leg).

> **[Amendment — TASK-ERP-BE-018] delegation scope projection (v1.3).** The
> `delegation_fact_proj` projection is extended with two grant-time fields —
> `scope` (`GLOBAL`|`REQUEST`) + `scopeRequestId` — projected from the
> `erp.approval.delegated.v1` payload's per-request scoping
> (`erp-approval-events.md` § v2.3 / approval-service v2.3, TASK-ERP-BE-017),
> closing the producer-only forward gap that BE-017 left (the producer emits the
> fields; until now nothing projected them). The fields surface on the
> `GET /api/erp/read-model/delegations` list + `/{grantId}` detail
> ([`read-model-api.md`](../../contracts/http/read-model-api.md) § Delegation
> facts) so an operator can see "blanket vs one-request" delegation. **scope is
> grant-time immutable metadata** — projected exactly like the validity window:
> the `delegated` handler **always** (re)stamps `scope`/`scopeRequestId` (even onto
> a sticky-terminal REVOKED row — a late grant fills a revoke-before-grant row's
> ABSENT scope while the status stays REVOKED), and the `revoked` handler **never**
> restates them (the revoke payload carries neither). A grant never seen leaves
> `scope` ABSENT (unknown, honest — E5 no fabrication). Migration V4 adds the two
> nullable columns + `ck_delegation_fact_proj_scope CHECK (scope IS NULL OR scope IN
> ('GLOBAL','REQUEST'))` — §16: the value set is DB-pinned (NULL allowed for a
> revoke-only row); no coherence CHECK (the producer already enforces
> scope↔scopeRequestId). The ACTIVE/REVOKED/sticky-terminal/dedupe/org_scope logic
> + the consumer group + the topics are **byte-unchanged** (additive field
> projection only). **Still no re-emission (E5 terminal).** The console scope
> display is the forward follow-up (TASK-PC-FE-056). Spec: this amendment +
> `read-model-subscriptions.md` § Delegation + `read-model-api.md` § Delegation
> facts + `erp-approval-events.md` § v2.3.

## Architecture Style Rationale

Hexagonal chosen because:
1. Multiple inbound adapters coexist naturally: Kafka consumers (4 topics) and
   REST controllers share the same domain core without coupling.
2. Projection logic (upsert / retire-mark / org-view assembly / dedupe check) is
   framework-free and fully testable.
3. Outbound adapters for JPA are interchangeable — important for slice testing
   and store migration.

Mirrors the masterdata-service Hexagonal canonical form (ADR-MONO-012) and the
scm inventory-visibility-service read-model precedent.

## Layer Structure

```
domain/         ← Pure Java: DepartmentProjection, EmployeeProjection,
                  JobGradeProjection, CostCenterProjection, EmployeeOrgView
                  (assembled VO), EventDedupeRecord, ChangeKind enum
application/    ← Use cases (ApplyMasterChangeUseCase, QueryEmployeeOrgViewUseCase)
                  + port interfaces (no Spring annotations in domain)
adapter/
  inbound/
    web/        ← REST controllers (@RestController) — read-only
    messaging/  ← Kafka @KafkaListener consumers (4 topics)
  outbound/
    persistence/ ← JPA entities + Spring Data repositories + adapters
config/         ← Spring @Configuration beans only (Kafka consumer, security)
```

## Service Type Compliance

### rest-api
- Stateless JWT auth (OAuth2 RS, GAP JWKS) — same chain as masterdata-service.
- `tenant_id=erp` fail-closed via **entitlement-trust dual-accept** at gateway
  (when activated) + service level (§ Multi-tenancy / Security).
- Read-only endpoints (no mutating REST).
- Standard error envelope `{ code, message }` (platform/error-handling.md).
- Paginated list endpoint; `?asOf=<ISO-8601>` point-in-time read parity with
  masterdata-service E3 (the projection retains retired rows — see § Idempotency).

### REST endpoints (v1 first increment)

All endpoints share the `/api/erp/read-model` base path. All require a JWT that
satisfies the entitlement-trust dual-accept gate (`tenant_id ∈ {erp, *}` ∪ signed
`entitled_domains ∋ erp`, § Multi-tenancy) **and** the READ authorization gate
(`erp.read` scope ∨ `isOperator()` ∨ entitled — mirrors masterdata-service's
`RoleScopeAuthorizationAdapter` READ gate, so the platform-console operator token
that already reads masterdata also reads this view; TASK-ERP-BE-004 finding).
Formal request / response shapes live in
[`read-model-api.md`](../../contracts/http/read-model-api.md).

| Method | Path | Public/Internal | Controller | Purpose |
|---|---|---|---|---|
| GET | `/api/erp/read-model/employees` | public | `EmployeeOrgViewController#list` | paginated employee org-view list (employee + department path + cost center + job grade) |
| GET | `/api/erp/read-model/employees/{id}` | public | `EmployeeOrgViewController#getOne` | single employee org-view; 404 `MASTERDATA_NOT_FOUND` when the projection has no such employee |

Every response carries `meta.warning: "Eventually-consistent read-model"` so
consumers cannot mistake the projection for the authoritative master
(`masterdata-service` is the source of record — E5).

`READ_MODEL_SOURCE_UNAVAILABLE` (erp.md § error codes) applies when a referenced
master projection is absent at assembly time (e.g. an employee references a
department whose `department.changed` event has not yet been consumed): the
org-view returns the employee with the unresolved reference fields `null` +
`meta.unresolved: ["department"]` — it **does not** fabricate the missing fact
(E5 forbidden pattern: no estimation/generation when source absent).

### Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe |
| `GET /actuator/info` | none | build info |
| `GET /actuator/prometheus` | network-isolated | metrics scrape (internal docker network only) |

### event-consumer
- Consumer group: `erp-read-model-v1`
- 4 topics from masterdata-service (same project): `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1`
- Manual ACK mode
- Retry: 3 attempts (exponential backoff) + DLT (`<topic>.DLT`)
- Idempotency: `processed_events` table keyed on envelope `eventId` (T8)
- Per-`aggregateId` ordering (partition key, guaranteed by producer contract) —
  serialises projection writes per aggregate (no concurrent multi-writer)

## Security

- OAuth2 Resource Server (RS256), JWKS `${OIDC_ISSUER_URL}/oauth2/jwks` — GAP IdP.
- Validators (`ServiceLevelOAuth2Config`): JwtTimestampValidator +
  `AllowedIssuersValidator` + decode-time `tenantClaimValidator`.
- **Decode-time** `tenantClaimValidator` applies the **entitlement-trust
  dual-accept** gate (`tenant_id ∈ {erp, *}` ∪ signed `entitled_domains ∋ erp`)
  at JWT decode (TASK-MONO-162 / TASK-ERP-BE-005 pattern) — a domain-entitled
  cross-tenant token (e.g. an operator whose `entitled_domains ∋ erp`) must
  survive decode before the filter runs.
- Service-level `TenantClaimEnforcer` filter (defense-in-depth) applies the same
  dual-accept gate; the decode validator and the filter are **independent gates
  and both dual-accept** (each carries its own local `isEntitled` helper).
- READ authorization gate (`RoleScopeAuthorizationAdapter`-equivalent): READ =
  `erp.read` scope ∨ `isOperator()` ∨ entitled. No mutating endpoints → no WRITE
  gate, no `org_scope` data-scope gate (E6 data-scope applies to write/targeted
  rows; the read-model list is tenant-scoped, not per-operator-subtree in this
  increment — see § Multi-tenancy).
- Public paths: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`
  (internal docker network only — never gateway-routed externally, E7).
- internal-system boundary (E7): external (non-SSO / non-internal-network)
  traffic rejected at Traefik / network layer (`EXTERNAL_TRAFFIC_REJECTED` if
  surfaced through a debug path), consistent with masterdata-service.

## Dependencies

| Direction | Target | Protocol | Notes |
|---|---|---|---|
| In | erp `gateway-service` (v1 deferred) → direct JWT until then | HTTP `/api/erp/read-model/**` | tenant-validated JWT (entitlement-trust dual-accept) |
| In | erp `masterdata-service` Kafka | Consumer subscribed to `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1` | EventDedupe (T8) idempotent; first erp inbound consumer |
| In | erp `approval-service` Kafka | Consumer subscribed to `erp.approval.{submitted,approved,rejected,withdrawn}.v1` (BE-010) + `erp.approval.delegated.v1` + `erp.approval.delegation.revoked.v1` (BE-015) | EventDedupe (T8); `approval_fact_proj` + `delegation_fact_proj` projections |
| Out | MySQL `erp_read_model_db` | JDBC | `department_proj` / `employee_proj` / `job_grade_proj` / `cost_center_proj` / `approval_fact_proj` / `delegation_fact_proj` / `processed_events` |
| Out | GAP `/oauth2/jwks` | HTTPS | RS256 JWT verification (libs/java-security) |
| Out (obs) | OTLP collector | HTTPS | `${OTLP_ENDPOINT}` traces |

No outbound business call; no event publication; no write back to
`masterdata-service` (E5 read-only boundary).

## Data Model (projection)

Denormalized read store. Each consumer upserts **its own** projection table keyed
by `aggregateId`; the org-view is assembled at **read time** by joining the four
tables (department path resolved by walking `parent_id`, bounded depth). This
keeps each event handler a single-table idempotent upsert (no fan-out re-stamp on
a department rename) — the read-time join always reflects the latest projected
master values.

```
department_proj(id PK, code, name, parent_id, status,
                effective_from, effective_to, last_event_at, last_event_id)
cost_center_proj(id PK, code, name, department_id, status,
                effective_from, effective_to, last_event_at, last_event_id)
job_grade_proj(id PK, code, name, display_order, status,
                effective_from, effective_to, last_event_at, last_event_id)
employee_proj(id PK, employee_number, name, department_id, cost_center_id,
                job_grade_id, status, effective_from, effective_to,
                last_event_at, last_event_id)
approval_fact_proj(approval_request_id PK, status, subject_type, subject_id,
                approver_id, submitter_id, submitted_at, finalized_at,
                last_reason, last_event_at, last_event_id)   -- BE-010
delegation_fact_proj(grant_id PK, delegator_id, delegate_id, valid_from,
                valid_to, status, reason, revoked_at, last_event_at,
                last_event_id, tenant_id,
                scope, scope_request_id)                      -- BE-015; scope* BE-018
processed_events(event_id VARCHAR PK, topic, aggregate_id, processed_at)
```

- `RETIRED` is **not** a structural delete (erp E2 / contract § Consumer rules):
  the row is retained with `status = RETIRED` + `effective_to` set; an `?asOf`
  read before the retirement still resolves it.
- Department path: read-time ancestry walk over `department_proj.parent_id`,
  depth-bounded (matches masterdata-service's parent-cycle invariant — the
  producer guarantees no cycle, so the walk terminates).
- `EmployeeOrgView` (assembled VO, not a table): `{ employee fields, department:
  {id, code, name, path[]}, costCenter: {id, code, name}, jobGrade: {id, code,
  name, displayOrder} }`; unresolved references → `null` + `meta.unresolved`.

A dedicated [`data-model.md`](data-model.md) is a low-priority follow-up if the
projection grows (masterdata-service / inventory-visibility precedent: inline the
model until it warrants its own file). This first increment inlines it here +
satisfies erp.md § Required Artifacts #4 (Integrated read model boundary map):
the single source of record for **every** projected field is `masterdata-service`.

## Saga / Long-running Flow (ADR-MONO-005)

`read-model-service` is a **read-model**; it owns no aggregate state machine and
makes no outbound synchronous business call → **no Category A (saga) and no
Category B (synchronous external)** flow. One ADR-MONO-005 category applies:

| Flow | Category | Resilience config | Fail behavior | Metrics | Status |
|---|---|---|---|---|---|
| masterdata change consumption (4 topics) | **C** (single-step idempotent consume, retry + DLT, no saga row) | manual ACK; 3 retries exponential backoff (1s, 2s); invalid envelope (null `eventId`/`payload`) → immediate DLT, no retry | duplicate `eventId` skipped via `processed_events`; retry exhaustion → `<topic>.DLT` (no silent discard) | consumer lag, DLT route count, dedupe-skip count | Target |

There is no outbound publish (no best-effort alert, unlike inventory-visibility)
— the read-model is terminal.

## Outbox + audit_log invariants

### Transactional outbox

**N/A — read-model is a terminal projection.** `read-model-service` runs no
transactional outbox and publishes no events (E5: it never re-emits authoritative
master facts). The only inbound is the masterdata change stream; the only outbound
is the read REST API. There is no state-of-record change to relay.

### Audit log (E2 / E8)

**N/A — no domain state machine, no operator-driven mutation.** erp E2/E8
(immutable audit of master/permission changes) target the authoritative write
surface (`masterdata-service` already records every master mutation in its
`audit_log`). `read-model-service` performs only idempotent re-application of
masterdata events — there are no mutating REST endpoints and no operator-driven
state transitions. Processing provenance is captured by **`processed_events`**
(`eventId` + `topic` + `processed_at`): every applied master event is traceable to
its source envelope. This service is **not** an `audit-heavy` write surface
(the project trait applies to masterdata-service's `audit_log`); no immutable
external-retention audit store is in this increment's scope (mirrors
inventory-visibility-service's § Audit log N/A rationale).

## Idempotency (T8)

`read-model-service` has **no mutating REST endpoints**, so the `Idempotency-Key`
header pattern (T1, used by masterdata-service) does **not** apply. Idempotency
lives entirely on the **event-consumer** side (T8):

- **Dedupe store**: `processed_events` keyed on the envelope `eventId`. A duplicate
  `eventId` is skipped without mutation — re-delivering the same master event
  leaves the projection byte-identical.
- **Idempotent projection**: each handler is an upsert by `aggregateId`
  (`CREATED`/`UPDATED`/`PARENT_MOVED` → upsert latest `after`; `RETIRED` → mark
  `status=RETIRED` + set `effective_to`, retaining the row). Replaying an applied
  `eventId` is a no-op.
- **Ordering**: producer partitions by `aggregateId` → per-aggregate publish-order
  delivery; the single consumer group serialises writes per aggregate (no
  concurrent multi-writer to guard, T7 ordering-based).

Invalid envelopes (null `eventId` or null `payload`) bypass dedupe and route
straight to DLT (cannot key the dedupe table).

## Multi-tenancy

**N/A as SaaS row-level isolation — single-tenant by project classification.**
`erp-platform` does not declare `multi-tenant` (PROJECT.md § Out of Scope; GAP is
the multi-tenant IdP). All projected rows belong to the `erp` tenant.

The domain claim is still **fail-closed enforced** at the gate via
**entitlement-trust dual-accept** (ADR-MONO-019 § D5, single-tenant gate,
defense-in-depth — identical to masterdata-service / TASK-ERP-BE-005). A token is
accepted when **either** `tenant_id ∈ {erp, *}` (`*` = SUPER_ADMIN platform-scope)
**or** the GAP-signed `entitled_domains ∋ erp`; rejection (403 `TENANT_FORBIDDEN`)
requires **both** branches to fail (fail-closed; entitlement only *widens*).
`entitled_domains` is read only from an RS256/JWKS-verified token (unforgeable —
GAP is the entitlement authority). Both the decode validator and the
`TenantClaimEnforcer` filter dual-accept independently.

Per-operator `org_scope` data-scope (E6) was **not** applied in the BE-007 first
increment (the list was tenant-scoped only). **TASK-ERP-BE-008 (ADR-MONO-020 D3
amendment 2026-06-05)** adds the symmetric **read filter**: when the JWT carries a
non-`"*"` `org_scope` (department subtree-root ids — membership-derived per
`operator_tenant_assignment.org_scope`, TASK-BE-338), the org-view list/detail is
filtered to employees whose resolved department is within ANY scoped subtree
(roots expanded → descendants via `department_proj.parent_id`, mirroring
masterdata-service's `RoleScopeAuthorizationAdapter` subtree containment). `"*"` /
absent `org_scope` = no read narrowing (net-zero — every BE-007 caller is
unaffected). This keeps the read-model's data-scope symmetric with the write
gate: an operator scoped to a subtree both writes and *sees* only that subtree.

## Mandatory Rule mapping (rules/domains/erp.md)

| Rule | Status | Mechanism |
|---|---|---|
| **E1** Master single source of record + reference integrity | N/A (consumer side) | This service holds **no** authoritative master; `masterdata-service` owns E1. The projection never creates a master — it only reflects consumed facts. Reference resolution (employee→department/cost-center/job-grade) is read-time join; unresolved → `null`, never fabricated. |
| **E2** Effective-dated master change + immutable audit | Partial (effective-dating reflected) | The projection retains `effective_from`/`effective_to` from events so `?asOf` reads reproduce historic revisions; `RETIRED` rows are retained (not deleted). Audit of the *change* is owned by masterdata-service's `audit_log` (E2 write side); the read-model's provenance is `processed_events`. |
| **E3** Approval state machine | Read-only projection (no logic) | The approval **state machine** lives in `approval-service`; this service only **projects** the observed terminal state (terminal-once: never reverts a terminal fact to SUBMITTED). It owns no approval logic (E5). [BE-010] |
| **E4** Approval transition idempotent + audit | `processed_events` dedupe (T8) | Transition idempotency + the immutable audit `history` are owned by `approval-service`; this consumer only dedupes on `eventId` and holds the latest fact (NOT the history — source of record = approval-service REST). [BE-010] |
| **E5** Integrated read model holds NO domain logic — read-only projection | ✅ **Primary subject** | **This service is E5's reference implementation**: read-only projection; no business rules re-implemented; each field's single source of record = `masterdata-service`; source-absent → `READ_MODEL_SOURCE_UNAVAILABLE` semantics (`null` + `meta.unresolved`, no fabrication); no event re-emission; no write-back. |
| **E6** Authorization via permission matrix + data scope — fail-closed | ✅ (read gate) | READ gate fail-closed: `erp.read` ∨ operator ∨ entitled, else `PERMISSION_DENIED`. No mutating surface → no WRITE/data-scope gate this increment (tenant-scoped list). |
| **E7** internal-system boundary — no external traffic, SSO enforced | ✅ | OAuth2 RS (GAP SSO) only; entitlement-trust tenant gate fail-closed; actuator scrape internal-network only; external traffic rejected at edge. |
| **E8** Permission/org change audited | N/A | No permission/org mutation surface (read-only). |

## Trait Rule mapping (rules/traits/)

| Trait Rule | Status | Mechanism |
|---|---|---|
| **internal-system** RBAC / SSO / no external exposure | ✅ | § Security — GAP SSO, entitlement-trust dual-accept, actuator network-isolated, no anonymous/self-signup path. |
| **transactional T1** Idempotency on mutating endpoints | N/A | No mutating REST endpoints. Idempotency is event-side (T8). |
| **transactional T8** Idempotent event consumption | ✅ | `processed_events` keyed on `eventId`; duplicate → skip without mutation. |
| **transactional T2/T3** Atomic state-change + outbox/relay | N/A | No transactional outbox — terminal read-model (no published events). |
| **transactional T4** State machine via dedicated module | N/A | No domain state machine; `status` (ACTIVE/RETIRED) mirrors the producer's master status, not a local machine. |
| **transactional T7** Optimistic locking on aggregates | ✅ (ordering-based) | `masterdata-service` is the sole write source; per-`aggregateId` partition-ordered consumption serialises projection writes per aggregate. No concurrent multi-writer to guard. |
| **audit-heavy** immutable audit store + retention | N/A | Read-model is not an audit write surface (see § Outbox + audit_log invariants); `audit_log` lives in masterdata-service. Provenance = `processed_events`. |

## Observability

- Logback MDC `traceId / requestId / tenantId (= erp) / userId` (libs/java-observability).
- Custom Micrometer metrics:
  - **Event consumer**: `read_model_event_dedupe_skipped_total`,
    `read_model_event_dlt_total{topic}`, `read_model_consumer_lag{topic}`,
    `read_model_projection_applied_total{aggregate,changeKind}`.
  - **Read API**: `read_model_org_view_unresolved_total{reference}` — counts
    org-views served with an unresolved reference (source-not-yet-consumed signal).
- Tracing: OTLP via `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`;
  dev sampling 100%. The consumed envelope's `traceId` is propagated so the
  master mutation → projection update is one continuous trace
  (federation observability parity, MONO-144 chain).
- Prometheus scrape on `/actuator/prometheus`, **internal docker network only**.

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Duplicate master `eventId` | skipped, no mutation (`processed_events`, T8) |
| 2 | Invalid envelope (null `eventId` / `payload`) | immediate `<topic>.DLT`, no retry |
| 3 | Transient consumer processing error | 3 retries (1s, 2s exponential) → `<topic>.DLT` on exhaustion |
| 4 | Cross-tenant JWT — `tenant_id ∉ {erp, *}` **and** signed `entitled_domains ∌ erp` (dual-accept both branches fail) | 403 `TENANT_FORBIDDEN` |
| 5 | Missing JWT / invalid signature / expired | 401 `UNAUTHORIZED` |
| 6 | Caller lacks read authorization (no `erp.read`, not operator, not entitled) | 403 `PERMISSION_DENIED` |
| 7 | External (non-internal-network) traffic at ingress | rejected at Traefik / network layer (`EXTERNAL_TRAFFIC_REJECTED` on a surfaced debug path) |
| 8 | org-view requested for an employee not yet projected | 404 `MASTERDATA_NOT_FOUND` (projection absence for the queried aggregate is a miss, not a fabricated row) |
| 9 | Employee references a department/cost-center/job-grade not yet consumed | org-view returned with that reference `null` + `meta.unresolved`; never fabricated (E5; `READ_MODEL_SOURCE_UNAVAILABLE` semantics) |
| 10 | Read endpoint serves eventually-stale data | by design — `meta.warning: "Eventually-consistent read-model"` (not a failure) |
| 11 | `RETIRED` master event consumed | row retained `status=RETIRED` + `effective_to` set; `?asOf` before retirement still resolves it (not a delete) |

## Testing Strategy

- **Unit** (`:read-model-service:test`):
  - domain — `EmployeeOrgView` assembly (resolved / unresolved-reference cases,
    department path walk), each projection's upsert/retire-mark, `EventDedupeRecord`.
  - application — `ApplyMasterChangeUseCase` (per `changeKind`),
    `QueryEmployeeOrgViewUseCase` (mocked ports, `@ExtendWith(MockitoExtension.class)`
    STRICT_STUBS).
  - adapters — 4 Kafka consumer mappers, validator units
    (`TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`),
    `TenantClaimEnforcerTest`.
- **Slice**: JPA adapter slices, `@WebMvcTest` + SecurityConfig +
  `GlobalExceptionHandler` error-envelope; controller slice asserting the
  `meta.warning` + `meta.unresolved` shape.
- **Integration** (`:read-model-service:integrationTest`, `@Tag("integration")`,
  Testcontainers MySQL + Kafka + WireMock/MockWebServer JWKS — **H2 forbidden**):
  - Consume each of the 4 topics → projection upsert; assemble org-view via the
    read API end-to-end (publish 4 events → GET `/employees/{id}` resolves all
    references).
  - Duplicate `eventId` → idempotent skip (projection unchanged).
  - Poison envelope → DLT; transient error → 3-retry then DLT.
  - Out-of-order / missing-reference → org-view `null` + `meta.unresolved`
    (no fabrication, E5).
  - `RETIRED` → row retained; `?asOf` before retirement still resolves.
  - Cross-tenant JWT → 403 `TENANT_FORBIDDEN`; entitled cross-tenant
    (`entitled_domains ∋ erp`) → 2xx (dual-accept); no read scope → 403
    `PERMISSION_DENIED`; no token → 401.

`integrationTest` is excluded from `./gradlew check` (Docker-free fast loop —
masterdata-service / inventory-visibility convention). The monorepo "Integration
(erp-platform, Testcontainers)" CI job (TASK-ERP-BE-004 established) runs it on
Linux runners; local Windows Docker availability is host-dependent (honest gap —
project memory `project_testcontainers_docker_desktop_blocker`).

## Required Artifacts mapping (rules/domains/erp.md § Required Artifacts)

| # | Artifact | Disposition |
|---|---|---|
| 4 | Integrated read model boundary map | **Inlined** here (§ Data Model + § Mandatory Rule mapping E5) — single source of record for every projected field = `masterdata-service`; projection-update via Kafka event subscription; erp never mutates the source (E5). Dedicated `data-model.md` = low-priority follow-up if the projection grows. |
| 7 | Bounded-context map | This service realises the **Integrated Read Model** bounded context (erp.md § Bounded Contexts) as a separate deployable — first split from the v1 single-deployable masterdata-service. |

Other erp Required Artifacts (#1 master model, #2 approval diagram, #3 permission
matrix, #5 internal boundary, #6 error codes) are owned by masterdata-service /
v2 services and are unchanged by this increment.

## References

- `platform/architecture-decision-rule.md`, `platform/service-types/INDEX.md`,
  `platform/service-types/rest-api.md` + `platform/service-types/event-consumer.md`
  (dual-type — documented exception, see § Service Type Composition),
  `platform/error-handling.md`, `platform/testing-strategy.md`,
  `platform/hardstop-rules.md` (HARDSTOP-09/10)
- `rules/domains/erp.md` (E5 — this service is E5's reference implementation; E1/E2/E6/E7),
  `rules/traits/internal-system.md`, `rules/traits/transactional.md` (T8)
- `projects/erp-platform/PROJECT.md` (§ Service Map — read-model-service),
  [`gap-integration.md`](../../integration/gap-integration.md)
- [`read-model-api.md`](../../contracts/http/read-model-api.md) (this PR),
  [`read-model-subscriptions.md`](../../contracts/events/read-model-subscriptions.md) (this PR),
  [`erp-masterdata-events.md`](../../contracts/events/erp-masterdata-events.md)
  (producer contract — consumed unchanged)
- precedent: `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
  (rest-api + event-consumer read-model, EventDedupe, no-outbox Cat C — closest analog);
  `projects/erp-platform/specs/services/masterdata-service/architecture.md`
  (producer + Hexagonal canonical form + security chain)
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D3 (read-model-service
  forward-declared; § D3 amendment records this first increment),
  `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` (Category C consumer)
- TASK-ERP-BE-007 — this spec + impl task
