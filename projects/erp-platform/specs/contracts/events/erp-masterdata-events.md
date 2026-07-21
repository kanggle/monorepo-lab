# Event Contract — erp-masterdata-events

Kafka events published by `erp-platform/apps/masterdata-service` through
the **transactional outbox** (`libs/java-messaging` `BaseEventPublisher` +
`MasterdataOutboxPublisher extends AbstractOutboxPublisher<MasterdataOutboxJpaEntity>`).

Authoritative architecture:
[`masterdata-service/architecture.md`](../../services/masterdata-service/architecture.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md)
§ Internal Event Catalog + E1 / E2 / E5 / E8.

## v1 publication decision

v1 **PUBLISHES** master-data change events. Rationale:

- The skeleton `build.gradle` already wires
  `implementation project(':libs:java-messaging')` (TASK-MONO-119) — the
  transactional outbox is available at zero additional cost.
- The integrated read model (`read-model-service`, v2 per ADR-MONO-016
  § D3) consumes these events to populate the cross-domain read views.
  Publishing in v1 establishes the forward interface so the v2 read-model
  is wired against a stable contract rather than a retrofit. Same shape
  as finance v1 publishing `finance.transaction.*` for `ledger-service`
  v2's future consumption.
- Per `rules/domains/erp.md` § Internal Event Catalog, the recommended
  prefix is `<prefix>.masterdata.{created,updated,deactivated}`. This
  contract collapses those into a single `…changed.v1` topic per
  aggregate with a `changeKind` enum payload field — fewer topics,
  identical semantics, easier per-aggregate ordering. The catalog's
  intent (E5 — read-only consumers receive aggregate change facts) is
  preserved.

v1 consumers = **none** (masterdata-service is a leaf in v1). Forward
consumers:
- `read-model-service` v2 (ADR-MONO-016 § D3) — populates integrated read
  views.
- `approval-service` v2 — references master rows from approval requests.
- `notification-service` v2 — fan-out master-change notifications.

This contract is the v1 forward interface for those v2 consumers.

> **[Amendment — TASK-ERP-BE-007, 2026-06-04]** The **first consumer now
> exists**: `read-model-service` (first increment) subscribes to 4 of the 5
> topics — `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1`
> — to project the integrated **employee org-view** (read-only, E5). The
> `businesspartner` topic stays unconsumed in this increment (not part of the
> org-view). The producer side is **unchanged** by this — `read-model-service`
> consumes this contract verbatim; the consumer-side rules it follows are in
> [`read-model-subscriptions.md`](read-model-subscriptions.md). "v1 consumers =
> none" remains the historical record of the producer-only bootstrap;
> `read-model-service` is the first realisation of the forward interface.

---

## Envelope (libs/java-messaging standard)

- **Producer source**: `"erp-platform-masterdata-service"` on every
  envelope.
- **Topic convention**: `<eventType>` + `.v1`. Breaking change → `.v2`,
  dual-publish during the deprecation window (platform convention).
- **Delivery**: at-least-once (outbox poll). Consumers MUST dedupe on
  `eventId` (`processed_events` pattern). Producer `acks=all`,
  `enable.idempotence=true`.
- **Ordering**: partition key = `aggregateId` (per-aggregate ordering;
  e.g. all Department-A events arrive in publish order). Cross-aggregate
  ordering is **not** guaranteed (consumers treat each aggregate stream
  independently). The `aggregateId` is carried BOTH as a top-level envelope
  field (the consumer's projection PK + dedupe join) AND as `partitionKey`
  (the Kafka message key). The read-model consumer requires the **top-level
  `aggregateId`** — an envelope missing it is invalid and routes to `.DLT`
  (TASK-ERP-BE-032).
- **Envelope fields (v1 wire)**: `eventId`, `eventType`, `source`,
  `occurredAt`, `schemaVersion` (currently `1`), `tenantId`, `aggregateType`,
  `aggregateId`, `partitionKey` (= `aggregateId`), `payload`. `traceId` is
  **deferred in v1** — the transactional-outbox relay does not yet propagate
  W3C trace context, so the field is omitted (forward-compatible: consumers
  treat it as optional). `schemaVersion` bumps on a breaking payload change
  ahead of the `.v2` topic cutover.
- **No PII beyond identifiers**: payloads carry the master record's
  business identifiers + business fields. There is no regulated PII in
  erp master data v1 — `Employee.name` is the only personally identifying
  field and is part of the master fact (consumers need it to render
  approval / read-model views). No KYC data, no national-id-like fields,
  no secrets are present in v1 master records — if such fields are added
  in a future revision, this contract MUST be revisited (E8 audit
  responsibility).

```json
{
  "eventId": "<uuid>",
  "eventType": "erp.masterdata.department.changed",
  "source": "erp-platform-masterdata-service",
  "occurredAt": "<ISO-8601 UTC>",
  "schemaVersion": 1,
  "tenantId": "erp",
  "aggregateType": "department|employee|jobgrade|costcenter|businesspartner",
  "aggregateId": "<id>",
  "partitionKey": "<id>",
  "payload": { ... }
}
```

---

## Topics (v1)

| eventType constant | Kafka topic | aggregate | Emitted when |
|---|---|---|---|
| `EVENT_DEPARTMENT_CHANGED` | `erp.masterdata.department.changed.v1` | department | created / effective-revision appended / retired / moved-parent |
| `EVENT_EMPLOYEE_CHANGED` | `erp.masterdata.employee.changed.v1` | employee | created / effective-revision appended / retired |
| `EVENT_JOBGRADE_CHANGED` | `erp.masterdata.jobgrade.changed.v1` | jobgrade | created / effective-revision appended / retired |
| `EVENT_COSTCENTER_CHANGED` | `erp.masterdata.costcenter.changed.v1` | costcenter | created / effective-revision appended / retired |
| `EVENT_BUSINESSPARTNER_CHANGED` | `erp.masterdata.businesspartner.changed.v1` | businesspartner | created / effective-revision appended / retired |

> Distinct `…created` / `…updated` / `…retired` topic variants per the
> rules/domains/erp.md § Internal Event Catalog recommendation are
> consolidated into the single `…changed.v1` topic per aggregate with a
> `changeKind` discriminator field. Consumers that want only one kind
> filter in their handler — saves five-topic-per-kind admin (5 aggregates
> × 4 kinds = 20 topics → 5 topics). The catalog's intent is preserved.

---

## Payload schemas (v1)

`changeKind` enum: `CREATED | UPDATED | RETIRED | PARENT_MOVED`
(`PARENT_MOVED` only for `department`).

`erp.masterdata.department.changed` payload:
```json
{ "aggregateId": "dept-...",
  "changeKind": "CREATED|UPDATED|RETIRED|PARENT_MOVED",
  "tenantId": "erp",
  "occurredAt": "<ISO-8601 UTC>",
  "actor": "<JWT sub or operator id>",
  "before": { "code", "name", "parentId", "status", "effectivePeriod" } | null,
  "after":  { "code", "name", "parentId", "status", "effectivePeriod" } | null,
  "reason": "<retire/move reason; null for CREATED/UPDATED>" }
```

`erp.masterdata.employee.changed` payload:
```json
{ "aggregateId": "emp-...",
  "changeKind": "CREATED|UPDATED|RETIRED",
  "tenantId": "erp",
  "occurredAt": "<ISO-8601 UTC>",
  "actor": "...",
  "before": { "employeeNumber", "name", "departmentId", "costCenterId",
              "jobGradeId", "status", "effectivePeriod" } | null,
  "after":  { ... same shape ... } | null,
  "reason": "...?" }
```

`erp.masterdata.jobgrade.changed` payload:
```json
{ "aggregateId": "jg-...",
  "changeKind": "CREATED|UPDATED|RETIRED",
  "tenantId": "erp",
  "occurredAt": "<ISO-8601 UTC>",
  "actor": "...",
  "before": { "code", "name", "displayOrder", "status", "effectivePeriod" } | null,
  "after":  { ... } | null,
  "reason": "...?" }
```

`erp.masterdata.costcenter.changed` payload:
```json
{ "aggregateId": "cc-...",
  "changeKind": "CREATED|UPDATED|RETIRED",
  "tenantId": "erp",
  "occurredAt": "<ISO-8601 UTC>",
  "actor": "...",
  "before": { "code", "name", "departmentId", "status", "effectivePeriod" } | null,
  "after":  { ... } | null,
  "reason": "...?" }
```

`erp.masterdata.businesspartner.changed` payload:
```json
{ "aggregateId": "bp-...",
  "changeKind": "CREATED|UPDATED|RETIRED",
  "tenantId": "erp",
  "occurredAt": "<ISO-8601 UTC>",
  "actor": "...",
  "before": { "code", "name", "partnerType", "paymentTerms", "status",
              "effectivePeriod" } | null,
  "after":  { ... } | null,
  "reason": "...?" }
```

`before` is `null` for `CREATED`; `after` is `null` for `RETIRED`;
both populated for `UPDATED` and `PARENT_MOVED`.

---

## Consumer rules

- Dedupe on `eventId` (at-least-once). Per-`aggregateId` ordering only.
- These events are the integration point for v2 `read-model-service`
  (integrated read model — per E5 the consumer projects events read-only;
  it MUST NOT re-emit authoritative master facts), `approval-service`
  (resolves master references at submission/approval time), and
  `notification-service` (master-change fan-out). None of those exist in
  v1; this contract is the v1 forward interface.
- Consumers MUST NOT treat `RETIRED` as a structural delete — the row's
  history remains for point-in-time reads (E2). A retired master is
  retained with `status = RETIRED` + `effectiveTo` set; queries with an
  `asOf` before the retirement date still see it as `ACTIVE`.
- `PARENT_MOVED` carries both the prior and new effective periods +
  parentIds; consumers updating denormalized hierarchy projections rebuild
  the affected subtree.
- Consumers that need cross-aggregate consistency (e.g. an Employee
  revision that references a Department revision) MUST resolve via the
  REST read API with `asOf=<event.occurredAt>` — events do not carry the
  referenced aggregates' full revisions to avoid contract bloat.

---

## Relationship to platform / rules

- Envelope + outbox + dedupe = `rules/traits/transactional.md` T2 / T3 +
  `libs/java-messaging` standard (TASK-MONO-049 `BaseEventPublisher`).
- Event-catalog naming follows `rules/domains/erp.md` § Internal Event
  Catalog (`<prefix>.masterdata.*`), prefix = `erp`. The `created` /
  `updated` / `deactivated` decomposition is consolidated into
  `…changed.v1` with a `changeKind` field per § v1 publication decision
  above (architecture-level expression, identical semantics).
- No event carries regulated PII (`rules/traits/audit-heavy.md` audit
  detail lives in the `audit_log` table, not the bus).
- v1 does NOT emit approval / read-model / permission events
  (`erp.approval.*` / `erp.readmodel.*` / `erp.permission.*` are
  forward-declared in `rules/domains/erp.md` § Internal Event Catalog
  but owned by v2 services — see `PROJECT.md` Service Map v2).
