# Event Contract — notification-service subscriptions

`notification-service` subscribes to alert-worthy events from 3 producer
services. Authoritative envelope shapes live in each producer's
`<service>-events.md`. This file:

1. Cross-links the subscribed topics to their producers.
2. Declares which seeded routing rule fires for each event.
3. Maps the v1 payload fields the routing matcher reads.

---

## Subscribed topics (v1)

| Source service | Topic | Event type | v1 routing rule | Default channel |
|---|---|---|---|---|
| `inventory-service` | `wms.inventory.alert.v1` | `inventory.low-stock-detected` | AlwaysMatch | `wms-alerts` (WARNING) |
| `inventory-service` | `wms.inventory.adjusted.v1` | `inventory.adjusted` | `\|payload.delta\| ≥ 100` | `wms-alerts` (INFO) |
| `inbound-service` | `wms.inbound.inspection.completed.v1` | `inbound.inspection.completed` | `payload.discrepancyCount > 0` | `wms-alerts` (WARNING) |
| `inbound-service` | `wms.inbound.asn.cancelled.v1` | `inbound.asn.cancelled` | AlwaysMatch | `wms-alerts` (INFO) |
| `outbound-service` | `wms.outbound.order.cancelled.v1` | `outbound.order.cancelled` | `payload.previousStatus IN [PICKED, PACKED, SHIPPED]` | `wms-alerts` (WARNING) |
| `outbound-service` | `wms.outbound.shipping.confirmed.v1` | `outbound.shipping.confirmed` | AlwaysMatch | `wms-shipping` (INFO) |

Routing rules are seeded by `apps/notification-service/src/main/resources/db/migration/V2__seed_routing_rules.sql`. Operators may toggle `enabled` or adjust matchers via direct DB edit until the v2 admin UI ships.

---

## Required envelope shape

Every consumed envelope MUST contain:

```json
{
  "eventId":   "<uuidv7 string>",
  "eventType": "<source service event type>",
  "occurredAt": "<RFC 3339 instant>",
  "aggregateId": "<source aggregate id, optional>",
  "payload":   { ... source-specific ... }
}
```

`AlertEnvelopeParser` rejects envelopes missing `eventId` or `eventType` with `IllegalArgumentException` → `DefaultErrorHandler` routes to `<topic>.DLT` (no retry).

---

## Payload field requirements per matcher

Producers MUST emit the listed payload fields when the v1 routing rule depends on them. Adding the field after the matcher exists is contract-safe (forward compatibility); removing it breaks the alert path.

| Source event | Required payload field(s) | Used by matcher |
|---|---|---|
| `inventory.adjusted` | `delta` (signed integer) | `\|delta\| ≥ 100` |
| `inbound.inspection.completed` | `discrepancyCount` (non-negative integer) | `> 0` |
| `outbound.order.cancelled` | `previousStatus` (string in {`RECEIVED`, `PICKED`, `PACKED`, `SHIPPED`, `CANCELLED`}) | `IN [PICKED, PACKED, SHIPPED]` |

`inventory.low-stock-detected`, `inbound.asn.cancelled`, `outbound.shipping.confirmed` use `AlwaysMatch` — payload shape is opaque to the matcher; templates may still read named fields when v2 introduces operator-editable templates.

---

## Idempotency

`notification_event_dedupe(event_id PK)` provides cross-topic replay safety.
The dedupe row's `outcome` records the routing classification:

| `outcome` | When |
|---|---|
| `QUEUED` | A delivery row was created |
| `FILTERED` | The matcher predicate rejected the event |
| `NO_RULE` | No enabled rule for `eventType` |
| `ERROR` | Routing raised a domain error (e.g. `ROUTING_AMBIGUOUS`) |

Replays produce the same classification — `outcome=QUEUED` becomes `outcome=DUPLICATE` from the consumer's point of view (no second delivery).

---

## DLT

Per the platform policy each subscribed topic has a sibling `<topic>.DLT` for poison records (envelope parse failures, downstream domain errors after retry exhaustion). Operator runbook: `runbooks/dlt-replay.md`.

---

## References

- `architecture.md` § Event Consumption — full producer/consumer matrix
- `domain-model.md` § Seeded Routing Rules (v1)
- `inventory-events.md`, `inbound-events.md`, `outbound-events.md` — producer contracts
