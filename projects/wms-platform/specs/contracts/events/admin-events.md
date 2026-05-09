# Event Contract — admin-service Domain Events

Authoritative schemas for events **published** by `admin-service`, and the
catalog of events **consumed** by the read-model projection consumers.

`admin-service` is dual-typed (`rest-api` + `event-consumer`):

- It **publishes** a small surface — User / Role / Assignment / Settings
  mutations only — via outbox (T3).
- It **consumes** every other WMS service's event stream to maintain the CQRS
  read-model. Consumed schemas are owned by the producing services; this
  document only links to them and declares the projection effect.

Changes here precede code changes (per `CLAUDE.md` Contract Rule).

---

## Delivery Semantics

### Publishing

- **Transactional outbox** (trait `transactional` T3). State change and outbox
  row written in the same DB transaction; a separate publisher process
  forwards outbox rows to Kafka. Guarantees **at-least-once** delivery.
- **Partition key**: aggregate id — `userId` for user events, `roleId` for role
  events, `assignmentId` for assignment events, `key` (the setting key) for
  settings events. Ensures ordered delivery per aggregate.
- **No cross-topic ordering**. A consumer correlating
  `admin.user.deactivated` with the cascade `admin.assignment.revoked` events
  must use `userId`, not arrival order.

### Consuming

- All consumers in `admin-service` share the consumer group
  `admin-projection`.
- **EventId dedupe** (T8). See
  [`specs/services/admin-service/idempotency.md`](../../services/admin-service/idempotency.md).
- **Last-write-wins** projection: each read-model row carries `last_event_at`;
  events older than the persisted value are dropped (recorded as
  `outcome=IGNORED_DUPLICATE` in the dedupe table).
- **At-least-once** redelivery semantics — the dedupe table is the safety net.

---

## Global Envelope

Every event shares this outer envelope. The `payload` field carries
event-specific data.

```json
{
  "eventId": "0191d8f0-1f0e-7c40-9d13-4a2c9e3f5678",
  "eventType": "admin.user.created",
  "eventVersion": 1,
  "occurredAt": "2026-05-09T10:00:00.123Z",
  "producer": "admin-service",
  "aggregateType": "user",
  "aggregateId": "uuid-of-user",
  "traceId": "abc-456",
  "actorId": "admin-uuid-or-system:bootstrap",
  "payload": { /* event-specific */ }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUIDv7 string | Unique per event. Consumers dedupe on this (T8) |
| `eventType` | string | `admin.<aggregate>.<action>` |
| `eventVersion` | int | Schema version for that `eventType`. v1 is the baseline |
| `occurredAt` | ISO-8601 UTC, ms precision | DB transaction commit time |
| `producer` | string | Always `admin-service` |
| `aggregateType` | string | `user` \| `role` \| `assignment` \| `setting` |
| `aggregateId` | UUID string for user/role/assignment; setting `key` (string) for settings | Per `aggregateType` |
| `traceId` | string | OTel trace id; propagated from the originating REST request |
| `actorId` | string or null | JWT subject for REST-driven; `system:bootstrap` for seed migration; null if unavailable |
| `payload` | object | Defined per `eventType` below |

Serialization: JSON. Future Avro/Protobuf migration possible but not v1.

---

## Topic Layout (Published by admin-service)

| Topic | Event types | Partition key |
|---|---|---|
| `wms.admin.user.v1` | `admin.user.created`, `admin.user.updated`, `admin.user.deactivated`, `admin.user.reactivated` | `userId` |
| `wms.admin.role.v1` | `admin.role.created`, `admin.role.updated`, `admin.role.deactivated`, `admin.role.reactivated` | `roleId` |
| `wms.admin.assignment.v1` | `admin.assignment.granted`, `admin.assignment.revoked` | `assignmentId` |
| `wms.admin.settings.v1` | `admin.settings.changed` | `key` |

- `v1` in the topic name: contract version. Breaking schema changes require a
  parallel `v2` topic with coexistence period (per
  `.claude/skills/cross-cutting/api-versioning/SKILL.md`).
- Retention: minimum 7 days. 30-day preferred for DLQ replay windows.
- Partitions: 3 per topic. Admin events are very low throughput — start small.
- Dead-letter topic: `<topic>.DLT` — for consumers, not the producer.

---

## Published Events (admin-service → consumers)

### 1. `admin.user.created`

Triggered by `POST /api/v1/admin/users`. One event per user creation.

Topic: `wms.admin.user.v1`
`aggregateType`: `user`
`aggregateId`: `User.id`

```json
"payload": {
  "userId": "uuid",
  "userCode": "USR-0001",
  "email": "alice@example.com",
  "name": "Alice",
  "phone": "+82-10-1234-5678",
  "defaultWarehouseId": "uuid-or-null",
  "status": "ACTIVE"
}
```

Consumer expectations:

- `notification-service` (future): may send a welcome email
- `admin-service` itself does NOT consume its own user events (write side is
  the source of truth)

### 2. `admin.user.updated`

Triggered by `PATCH /api/v1/admin/users/{id}`.

Topic: `wms.admin.user.v1`
`aggregateType`: `user`
`aggregateId`: `User.id`

```json
"payload": {
  "userId": "uuid",
  "changedFields": ["name", "phone", "defaultWarehouseId", "email"],
  "user": {
    "userCode": "USR-0001",
    "email": "alice.l@example.com",
    "name": "Alice Liddell",
    "phone": "+82-10-1234-5679",
    "defaultWarehouseId": "uuid",
    "status": "ACTIVE"
  }
}
```

`changedFields` lists only the fields whose values actually changed (no-op
mutations are filtered out before publication). `user` carries the full new
state — consumers may use it without re-fetching.

### 3. `admin.user.deactivated`

Triggered by `POST /api/v1/admin/users/{id}/deactivate`.

Topic: `wms.admin.user.v1`
`aggregateType`: `user`
`aggregateId`: `User.id`

```json
"payload": {
  "userId": "uuid",
  "userCode": "USR-0001",
  "force": false,
  "cascadeRevokedAssignmentIds": []
}
```

When `force=true`, `cascadeRevokedAssignmentIds` lists the assignment ids
revoked atomically as part of the deactivation. Each revoked assignment also
emits its own `admin.assignment.revoked` event in the same outbox transaction.
The two surfaces (user event + assignment events) carry overlapping
information intentionally — consumers may rely on whichever is more
convenient.

### 4. `admin.user.reactivated`

Triggered by `POST /api/v1/admin/users/{id}/reactivate`.

Topic: `wms.admin.user.v1`

```json
"payload": {
  "userId": "uuid",
  "userCode": "USR-0001"
}
```

Note: reactivation does **not** restore previously-revoked assignments.

### 5. `admin.role.created`

Triggered by `POST /api/v1/admin/roles`.

Topic: `wms.admin.role.v1`
`aggregateType`: `role`
`aggregateId`: `Role.id`

```json
"payload": {
  "roleId": "uuid",
  "roleCode": "WMS_SHIFT_LEAD",
  "name": "Shift Lead",
  "description": "Floor supervisor",
  "permissionsJson": ["INVENTORY_READ", "ALERT_ACKNOWLEDGE"],
  "status": "ACTIVE"
}
```

### 6. `admin.role.updated`

Triggered by `PATCH /api/v1/admin/roles/{id}`.

Topic: `wms.admin.role.v1`

```json
"payload": {
  "roleId": "uuid",
  "roleCode": "WMS_SHIFT_LEAD",
  "changedFields": ["permissionsJson", "name"],
  "role": {
    "name": "Shift Lead (Updated)",
    "description": "...",
    "permissionsJson": ["INVENTORY_READ", "INBOUND_READ", "ALERT_ACKNOWLEDGE"],
    "status": "ACTIVE"
  }
}
```

> **Permission propagation note**: this event surfaces the new permission set,
> but does **not** rotate JWTs of users who already hold this role. Token
> re-mint is a separate v2 concern; the contract intentionally does not
> require consumers to invalidate sessions.

### 7. `admin.role.deactivated` / `admin.role.reactivated`

Triggered by the corresponding endpoints.

Topic: `wms.admin.role.v1`

```json
"payload": {
  "roleId": "uuid",
  "roleCode": "WMS_SHIFT_LEAD",
  "force": false,
  "cascadeRevokedAssignmentIds": []
}
```

(`reactivated` payload omits `force` / `cascadeRevokedAssignmentIds`.)

### 8. `admin.assignment.granted`

Triggered by `POST /api/v1/admin/assignments` (only when a new row is
inserted; the idempotent grant of an already-active assignment does NOT emit
a duplicate event).

Topic: `wms.admin.assignment.v1`
`aggregateType`: `assignment`
`aggregateId`: `UserRoleAssignment.id`

```json
"payload": {
  "assignmentId": "uuid",
  "userId": "uuid",
  "userCode": "USR-0001",
  "roleId": "uuid",
  "roleCode": "WMS_OPERATOR",
  "warehouseId": "uuid-or-null",
  "grantedAt": "2026-05-09T10:00:00.123Z",
  "grantedBy": "admin-uuid"
}
```

### 9. `admin.assignment.revoked`

Triggered by `DELETE /api/v1/admin/assignments/{id}`, **and** by cascade
revocation during force-deactivation of a User or Role. Cascade events share
the same envelope and payload — a consumer cannot tell them apart from the
event alone (and is not expected to).

Topic: `wms.admin.assignment.v1`

```json
"payload": {
  "assignmentId": "uuid",
  "userId": "uuid",
  "userCode": "USR-0001",
  "roleId": "uuid",
  "roleCode": "WMS_OPERATOR",
  "warehouseId": "uuid-or-null",
  "revokedAt": "2026-05-09T10:05:00.123Z",
  "revokedBy": "admin-uuid",
  "cascadeReason": "USER_DEACTIVATED"
}
```

`cascadeReason` ∈ `null | USER_DEACTIVATED | ROLE_DEACTIVATED`. `null` for a
direct revocation; the other two values for force-cascade revocations. The
field is informational only — consumers must not treat cascade revocations
differently from direct revocations.

### 10. `admin.settings.changed`

Triggered by `PUT /api/v1/admin/settings/{key}`.

Topic: `wms.admin.settings.v1`
`aggregateType`: `setting`
`aggregateId`: `key` (e.g., `inventory.reservation.ttl_hours`)

```json
"payload": {
  "key": "inventory.reservation.ttl_hours",
  "scope": "GLOBAL",
  "warehouseId": null,
  "valueJson": 36,
  "previousValueJson": 24,
  "version": 4
}
```

`previousValueJson` is included so consumers can react to the delta (e.g.,
adjust a counter, log a change reason). Consumers must treat this as
informational — the authoritative value is `valueJson`.

Consumer expectations:

- `inventory-service`: re-reads `inventory.reservation.ttl_hours` and adjusts
  the reservation sweeper interval at the next scheduled tick (no immediate
  cancellation of in-flight reservations)
- `inbound-service`: re-reads `inbound.asn.auto_close_delay_hours`
- `outbound-service`: re-reads `outbound.saga.sweeper_interval_seconds`
- `admin-service`: ignores its own emitted `admin.settings.changed`

---

## Consumed Events (Read-Model Projection)

`admin-service` subscribes to the following topics, all in consumer group
`admin-projection`. Schemas are owned by the producing services. This section
declares the **projection effect** only.

| Topic | Source contract | Read-model effect |
|---|---|---|
| `wms.master.warehouse.v1` | [`master-events.md`](master-events.md) | Upsert `warehouse_ref` |
| `wms.master.zone.v1` | [`master-events.md`](master-events.md) | Upsert `zone_ref` |
| `wms.master.location.v1` | [`master-events.md`](master-events.md) | Upsert `location_ref` |
| `wms.master.sku.v1` | [`master-events.md`](master-events.md) | Upsert `sku_ref` |
| `wms.master.partner.v1` | [`master-events.md`](master-events.md) | Upsert `partner_ref` |
| `wms.master.lot.v1` | [`master-events.md`](master-events.md) | Upsert `lot_ref` |
| `wms.inbound.asn.v1` [^split-asn] | [`inbound-events.md`](inbound-events.md) | Update `asn_summary` (received / cancelled / closed) |
| `wms.inbound.inspection.completed.v1` | [`inbound-events.md`](inbound-events.md) | Insert / replace `inspection_summary` (1:1 per ASN) |
| `wms.inbound.putaway.completed.v1` | [`inbound-events.md`](inbound-events.md) | Increment `throughput_inbound_daily` |
| `wms.outbound.order.v1` [^split-order] | [`outbound-events.md`](outbound-events.md) | Update `order_summary` |
| `wms.outbound.shipping.confirmed.v1` | [`outbound-events.md`](outbound-events.md) | Append `shipment_summary`, increment `throughput_outbound_daily` |
| `wms.inventory.received.v1` | [`inventory-events.md`](inventory-events.md) | Update `inventory_snapshot` |
| `wms.inventory.adjusted.v1` | [`inventory-events.md`](inventory-events.md) | Update `inventory_snapshot`, append `adjustment_audit` |
| `wms.inventory.transferred.v1` | [`inventory-events.md`](inventory-events.md) | Update both source/target `inventory_snapshot` rows; append two `adjustment_audit` rows |
| `wms.inventory.reserved.v1` | [`inventory-events.md`](inventory-events.md) | Update `inventory_snapshot.reserved_qty` |
| `wms.inventory.released.v1` | [`inventory-events.md`](inventory-events.md) | Update `inventory_snapshot.reserved_qty` |
| `wms.inventory.confirmed.v1` | [`inventory-events.md`](inventory-events.md) | Update `inventory_snapshot.reserved_qty` (decrease) and `available_qty` indirectly via shipping flow |
| `wms.inventory.alert.v1` | [`inventory-events.md`](inventory-events.md) | Append `alert_log` |

[^split-asn]: **Logical aggregate.** `inbound-service` actually publishes to
three split topics — `wms.inbound.asn.received.v1`,
`wms.inbound.asn.cancelled.v1`, `wms.inbound.asn.closed.v1` — see
[`inbound-events.md § Topic Layout`](inbound-events.md). The consumer-side
view in this table folds those into the single conceptual aggregate
`wms.inbound.asn.v1` for projection bookkeeping. The ProjectionConsumer in
`admin-service` listens on all three split topics; the rolled-up name is a
documentation convenience. No production change is implied by this entry —
producer-side topic split remains authoritative (TASK-BE-048 #7).

[^split-order]: **Logical aggregate.** Mirrors the inbound ASN pattern.
`outbound-service` publishes to two split topics —
`wms.outbound.order.received.v1` and `wms.outbound.order.cancelled.v1` —
see [`outbound-events.md § Topic Layout`](outbound-events.md). The
consumer-side view here folds those into `wms.outbound.order.v1`. Same
documentation-only convention as the ASN row above (TASK-BE-048 #7).

### Projection Idempotency Pattern

For each consumed event the projection handler runs in a single
`@Transactional` boundary:

```
1. INSERT INTO admin_event_dedupe(event_id, event_type, processed_at, outcome)
   VALUES (?, ?, now(), 'APPLIED')
   ON CONFLICT (event_id) DO NOTHING
   RETURNING event_id;

2a. Row inserted:
    - Look up the affected read-model row(s)
    - If event.occurredAt > row.last_event_at: apply the projection mutation
      (upsert / append / increment) and set last_event_at = event.occurredAt
    - Else: skip (record outcome IGNORED_DUPLICATE_LATE — set via UPDATE on
      the just-inserted dedupe row before commit)

2b. No row returned (duplicate eventId):
    - Skip mutation
    - Commit no-op TX (the existing dedupe row already reflects the prior
      outcome)
```

For details on dedupe-table schema, retention, replay risk, and DLT handling
see [`idempotency.md`](../../services/admin-service/idempotency.md).

### Out-of-Order Event Handling

Single-partition-per-aggregate topics make in-aggregate ordering nearly
deterministic. Cross-aggregate ordering (e.g., a `master.location.created`
arriving after an `inventory.received` that references the location) is not
guaranteed and is tolerated by:

- Read-model rows allow nullable `*_code` denormalised fields. If the
  reference event has not yet been projected, the dependent row is still
  inserted with the FK ids (`location_id`, `sku_id`) but `location_code` /
  `sku_code` may be null until the reference catches up.
- A nightly reconciliation job (out of v1 scope; tracked in `architecture.md
  § Extensibility`) backfills missing denormalised fields. Until then,
  dashboards display ids when codes are absent.

---

## DLT and Replay

- DLT topic: `<topic>.DLT` (Spring Kafka `DeadLetterPublishingRecoverer`
  default suffix `.DLT`).
- Retry: 3 attempts, exponential backoff with jitter (1s / 2s / 4s).
  Permanent failures (unparseable JSON, unknown `eventType`) → DLT
  immediately.
- DLT replay: an ops-only endpoint or kafka-cli operation re-publishes DLT
  records to the original topic. The dedupe table catches still-known events
  (`outcome=IGNORED_DUPLICATE`); events older than 30 days re-apply per the
  accepted-risk note in `idempotency.md`.

---

## Backwards Compatibility

- **Adding fields** to a payload: backwards compatible if consumers use
  permissive parsing (Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`). No `v2`
  topic required.
- **Removing or renaming fields, changing types, semantic changes**:
  breaking. Require parallel `v2` topic with coexistence per
  `.claude/skills/cross-cutting/api-versioning/SKILL.md`.
- **eventType renames**: breaking. Same coexistence requirement.

---

## References

- [`specs/services/admin-service/architecture.md`](../../services/admin-service/architecture.md)
- [`specs/services/admin-service/domain-model.md`](../../services/admin-service/domain-model.md)
- [`specs/services/admin-service/idempotency.md`](../../services/admin-service/idempotency.md)
- [`specs/contracts/http/admin-service-api.md`](../http/admin-service-api.md)
- [`specs/contracts/events/inventory-events.md`](inventory-events.md)
- [`specs/contracts/events/inbound-events.md`](inbound-events.md)
- [`specs/contracts/events/outbound-events.md`](outbound-events.md)
- [`specs/contracts/events/master-events.md`](master-events.md)
- `rules/traits/transactional.md` — T3 (outbox), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I3 (retry), I5 (DLQ)
- `platform/service-types/event-consumer.md`
