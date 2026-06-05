# Event Subscriptions — read-model-service

Kafka topics **consumed** by `erp-platform/apps/read-model-service` to populate
the integrated read model (employee org-view, first increment). This service
**publishes no events** (E5 — a terminal read-only projection).

Authoritative architecture:
[`read-model-service/architecture.md`](../../services/read-model-service/architecture.md).
Producer contract (consumed unchanged):
[`erp-masterdata-events.md`](erp-masterdata-events.md).

---

## Consumed topics (v1 first increment)

| Topic | Producer | Projection table | Handler |
|---|---|---|---|
| `erp.masterdata.department.changed.v1` | masterdata-service | `department_proj` | upsert by `aggregateId`; `RETIRED` → mark; `PARENT_MOVED` → upsert new `parentId` |
| `erp.masterdata.employee.changed.v1` | masterdata-service | `employee_proj` | upsert by `aggregateId`; `RETIRED` → mark |
| `erp.masterdata.jobgrade.changed.v1` | masterdata-service | `job_grade_proj` | upsert by `aggregateId`; `RETIRED` → mark |
| `erp.masterdata.costcenter.changed.v1` | masterdata-service | `cost_center_proj` | upsert by `aggregateId`; `RETIRED` → mark |

**`erp.masterdata.businesspartner.changed.v1` is NOT consumed** in this increment
— business-partner is not part of the employee org-view. Subscribing to it
(for a full integrated view) is a follow-up; until then this is a deliberate,
recorded gap (no silent drop — the topic is simply not in the subscription list).

Consumer group: `erp-read-model-v1`.

---

## Consumed topics (v1.1 — approval-fact projection, TASK-ERP-BE-010)

The integrated read model is extended to project **approval facts** (the latest
state of each approval request), closing the previously-empty consumer side of
[`erp-approval-events.md`](erp-approval-events.md) ("Consumers in this increment
= none" — `approval-service` was a producer-only leaf). This is the read-model
side of the `approval-service` → `read-model` event loop, mirroring the
masterdata → read-model loop above. Still **read-only / no re-emission** (E5).

| Topic | Producer | Projection table | Handler |
|---|---|---|---|
| `erp.approval.submitted.v1` | approval-service | `approval_fact_proj` | upsert by `aggregateId` (= `approvalRequestId`); set `status=SUBMITTED`, `submittedAt`, route/subject ids |
| `erp.approval.approved.v1` | approval-service | `approval_fact_proj` | upsert; `status=APPROVED`, `finalizedAt`, optional `lastReason` |
| `erp.approval.rejected.v1` | approval-service | `approval_fact_proj` | upsert; `status=REJECTED`, `finalizedAt`, `lastReason` (required) |
| `erp.approval.withdrawn.v1` | approval-service | `approval_fact_proj` | upsert; `status=WITHDRAWN`, `finalizedAt`, `lastReason` (required) |

Consumer group: `erp-read-model-v1` (shared — the approval handlers join the
existing group; partition key = `aggregateId` = `approvalRequestId`).

**Projection semantics** (approval facts):
- **Latest-state upsert keyed on `approvalRequestId`** — the projection holds the
  *current* fact (status + ids + timestamps + last reason), NOT the full
  transition history. The authoritative full `history` stays with
  `approval-service` (`GET /api/erp/approval/requests/{id}` is the source of
  record — E5: the read-model does not reconstruct the audit trail).
- **Terminal-once** (E3): once a terminal event (`approved`/`rejected`/
  `withdrawn`) is projected, a later non-duplicate transition for the same
  `approvalRequestId` is out-of-contract; the handler keeps the terminal fact
  (idempotent / last-terminal-wins, never reverts a terminal to SUBMITTED).
- **Out-of-order tolerance**: per-`approvalRequestId` ordering is guaranteed by
  the producer partition key, so `submitted` precedes its terminal. A terminal
  arriving without a prior `submitted` (e.g. compaction / replay-from-middle)
  still upserts a row (the fact is the terminal state); missing `submittedAt`
  is left ABSENT (E5 — no fabrication).
- **Subject enrichment** is read-time only: the fact stores the opaque
  `subjectId`/`subjectType`; display names + the subject's department (for the
  org_scope read filter) are resolved at read time against the existing
  `employee_proj` / `department_proj` (eventually-consistent; unresolved →
  surfaced, never fabricated).

> `erp.approval.delegated.v1` is **consumed** as of TASK-ERP-BE-015 (see § v1.2
> below). `erp.permission.*` / other catalog topics remain forward-declared and
> unconsumed (recorded gap, no silent drop).

---

## Consumed topics (v1.2 — delegation-fact projection, TASK-ERP-BE-015)

The integrated read model is extended to project **delegation facts** (the latest
state of each delegation grant: ACTIVE/REVOKED) so an operator can query "who may
act for whom" read-only. This consumes BOTH the existing
`erp.approval.delegated.v1` (grant create — previously unconsumed) AND the NEW
`erp.approval.delegation.revoked.v1` (grant revoke — added by the producer leg of
this task, [`erp-approval-events.md`](erp-approval-events.md) § v2.2). Without the
revoke event a create-only projection could never reflect a manual cancellation.
Still **read-only / no re-emission** (E5).

| Topic | Producer | Projection table | Handler |
|---|---|---|---|
| `erp.approval.delegated.v1` | approval-service | `delegation_fact_proj` | upsert by `aggregateId` (= `grantId`); set `status=ACTIVE`, `delegatorId`/`delegateId`, `validFrom`/`validTo`, `reason`, **`scope`/`scopeRequestId` (TASK-ERP-BE-018)** |
| `erp.approval.delegation.revoked.v1` | approval-service | `delegation_fact_proj` | upsert; `status=REVOKED`, `revokedAt`; the validity window **and `scope`/`scopeRequestId` are preserved** (the revoke payload carries neither) |

Consumer group: `erp-read-model-v1` (shared — the delegation handlers join the
existing group; partition key = `aggregateId` = `grantId`).

**Projection semantics** (delegation facts):
- **Latest-state upsert keyed on `grantId`** — the projection holds the *current*
  fact (status + ids + window + last reason + revoke timestamp), NOT the grant
  audit history (which stays with `approval-service`).
- **Sticky-terminal REVOKED** (last-event-wins): once `revoked` is projected, a
  later non-duplicate `delegated` for the same `grantId` never reverts REVOKED →
  ACTIVE (a late grant may fill in a previously-absent validity window, but the
  status stays REVOKED).
- **Out-of-order tolerance**: a `revoked` arriving without a prior `delegated`
  (compaction / replay-from-middle) still upserts a REVOKED row; the grant-only
  fields (`validFrom`/`validTo`, **and `scope`/`scopeRequestId` — TASK-ERP-BE-018**)
  are left ABSENT (E5 — no fabrication; the revoke payload carries none).
- **`scope`/`scopeRequestId` are grant-time immutable metadata (TASK-ERP-BE-018)** —
  projected from the `delegated` payload (`erp-approval-events.md` § v2.3): `scope`
  ∈ {`GLOBAL`, `REQUEST`}, `scopeRequestId` set only when `REQUEST`. Treated exactly
  like the validity window: the `delegated` handler **always** (re)stamps them — even
  onto a sticky-terminal REVOKED row (a late grant fills a revoke-before-grant row's
  ABSENT scope while the status stays REVOKED) — and the `revoked` handler **never**
  restates them. A grant that is never seen leaves `scope` ABSENT (unknown, honest).
- **Time-window expiry is read-time only**: `validTo` in the past is NOT a status —
  an `?activeAt=<instant>` list filter evaluates `status=ACTIVE ∧ validFrom ≤ t ∧
  (validTo null ∨ t ≤ validTo)`; the projected status tracks only the grant/revoke
  lifecycle.
- **org_scope read filter** is the **delegator's department subtree**
  (TASK-ERP-BE-008 parity): the `delegatorId` (an employee id) is resolved to its
  `employee_proj` department; `["*"]`/absent = net-zero.

---

## Envelope (consumed — libs/java-messaging standard)

The producer envelope is defined in
[`erp-masterdata-events.md`](erp-masterdata-events.md) § Envelope. This consumer
reads:

- `eventId` — dedupe key (`processed_events` PK). Null → invalid → DLT.
- `aggregateId` — projection row PK / partition key (per-aggregate ordering).
- `payload.changeKind` — `CREATED | UPDATED | RETIRED | PARENT_MOVED`.
- `payload.after` — the projected master values (`null` for `RETIRED`).
- `payload.before` — not required by the projection (latest-wins upsert).
- `payload.occurredAt` / `effectivePeriod` (inside `after`) — retained for
  `?asOf` point-in-time read parity (E2).
- `traceId` — propagated into the projection-update span (observability
  continuity: master mutation → projection update is one trace).

---

## Consumption rules

- **Idempotency (T8)**: dedupe on `eventId` via `processed_events`; a duplicate
  is skipped without mutation (re-delivery leaves the projection byte-identical).
- **Ordering**: per-`aggregateId` only (producer partitions by `aggregateId`).
  Cross-aggregate ordering is not guaranteed — handlers treat each aggregate
  stream independently. An employee event referencing a not-yet-consumed
  department resolves to an unresolved reference at **read** time (org-view
  `null` + `meta.unresolved`), never a fabricated value (E5).
- **RETIRED is not a delete** (erp E2 / producer contract): retain the row with
  `status = RETIRED` + `effective_to`; an `?asOf` read before the retirement
  still resolves it as `ACTIVE`.
- **Delivery resilience (ADR-MONO-005 Category C)**: manual ACK; 3 retries
  (exponential backoff) → `<topic>.DLT`; invalid envelope (null `eventId` /
  `payload`) → immediate DLT, no retry.
- **No re-emission (E5)**: the projection never publishes derived events or
  writes back to `masterdata-service`. The read model is terminal.

---

## Relationship to platform / rules

- Idempotent consumption + dedupe = `rules/traits/transactional.md` T8 +
  `libs/java-messaging` standard.
- Read-only projection boundary = `rules/domains/erp.md` E5 (the read-model holds
  no domain business logic; each projected field's single source of record is
  `masterdata-service`).
- Consumer resilience (retry + DLT, no saga) =
  [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md)
  Category C — same shape as scm `inventory-visibility-subscriptions.md`.
