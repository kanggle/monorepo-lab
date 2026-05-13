# notification-events — Published Event Contract

`notification-service` publishes a single audit-trail event in v1.

| Topic | Event type | Trigger | Schema version |
|---|---|---|---|
| `wms.notification.delivered.v1` | `notification.delivered` | Every terminal delivery transition (`SUCCEEDED` or `FAILED`) | v1 |

Authoritative producer code: `apps/notification-service/src/main/java/com/wms/notification/adapter/outbound/messaging/OutboxAdapter.java`

Authoritative spec cross-link: `specs/services/notification-service/architecture.md` § Event Publication.

---

## Envelope

All `notification-service` published events use the standard WMS envelope:

```json
{
  "eventId": "<uuidv7 string>",
  "eventType": "notification.delivered",
  "eventVersion": 1,
  "aggregateId": "<delivery_id uuid string>",
  "occurredAt": "<RFC 3339 instant>",
  "payload": { ... }
}
```

The outbox publisher uses the source event's id (`payload.sourceEventId`) as the
Kafka **partition key** so all delivery events for the same source event land
on the same partition — admin-service v2 dashboards can rely on per-source
ordering.

---

## Payload (v1)

```json
{
  "deliveryId": "<delivery_id uuid string>",
  "sourceEventId": "<source-event uuid string — eventId from the consumed envelope>",
  "sourceTopic": "wms.inventory.alert.v1",
  "channelId": "wms-alerts",
  "status": "SUCCEEDED | FAILED",
  "attemptCount": 1,
  "outcome": "SUCCEEDED | FAILED_PERMANENT | FAILED_RETRY_EXHAUSTED | FAILED_CHANNEL_NOT_CONFIGURED",
  "lastError": "<optional vendor error string, ≤500 chars>"
}
```

### Field semantics

- **`status`** — derived from the `notification_delivery.status` column (terminal value).
- **`outcome`** — finer-grained reason code; admin-service v2 dashboards group `FAILED_*` to drive operator alerting:
  - `SUCCEEDED` — Slack 2xx accepted.
  - `FAILED_PERMANENT` — Slack 4xx (404 channel-not-found, 410 token-revoked).
  - `FAILED_RETRY_EXHAUSTED` — 5 transient attempts all failed.
  - `FAILED_CHANNEL_NOT_CONFIGURED` — webhook env var blank — fail-closed contract (architecture.md § Edge Cases).
- **`lastError`** — present iff non-`SUCCEEDED`. Vendor error trimmed to 500 chars; never includes secrets (the webhook URL is NOT logged).

### Schema versioning

- `eventVersion` is an **integer** matching the 5 sibling WMS event contracts (master/inventory/inbound/outbound/admin); v1 schema = `1`, future v2 = `2`. (Pre-2026-05-13 the wire format was string `"v1"` — TASK-BE-144 aligned this with siblings on refactor-spec audit finding.)
- v1 covers single-channel terminal deliveries. Future evolutions (multi-channel fanout in v2, retry-budget metadata) increment `eventVersion` to `2` and remain backward-compatible by additive payload fields only.

---

## Consumers

- **`admin-service`** (v2) — read-model projection for ops dashboards (delivery rate per channel, failure mix).

---

## Out of Scope (v1)

- `notification.delivery.scheduled` is **NOT** published to Kafka in v1 — the outbox writes the row but the publisher's topic resolver only forwards `notification.delivered`. (Reduces topic noise — admin only needs terminal events.) This may be relaxed in v2.

---

## DLT

Per the platform `event-driven-policy.md`: Spring Kafka's `DefaultErrorHandler` routes failed records to `wms.notification.delivered.v1.DLT`. The outbox publisher does its own retry-with-backoff before resorting to the DLT, so that path is rare.

---

## References

- `architecture.md` — service-level decisions
- `domain-model.md` — aggregate + persistence
- `notification-subscriptions.md` — sibling consumer-side contract
