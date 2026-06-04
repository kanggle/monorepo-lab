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
