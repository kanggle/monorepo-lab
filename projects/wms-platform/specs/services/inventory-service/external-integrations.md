# inventory-service — External Integrations

External vendor catalog for `inventory-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

**Zero-state declaration**: `inventory-service` has **no direct external
vendor integrations** in v1. All external traffic enters the WMS via the
sibling services (`inbound-service`, `outbound-service`, `gateway-service`)
and reaches `inventory-service` only as internal Kafka events. This file
exists to make that boundary explicit and to set the entry point for any
future external integration that lands directly in inventory.

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| _(none in v1)_ | — | — | — | — |

Sibling integration surfaces (where the WMS does talk to external systems):

- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — ERP ASN webhook (HMAC-SHA256), Kafka cluster, infrastructure.
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — ERP order webhook (HMAC-SHA256), external TMS push (API key, the marquee `integration-heavy` exercise), Kafka cluster, infrastructure.

`inventory-service` consumes the internal events those services publish (via Kafka) and publishes its own reservation / confirmation / adjustment events — see [`architecture.md`](architecture.md) § Dependencies and [`../../contracts/events/inventory-events.md`](../../contracts/events/inventory-events.md).

---

## Why Zero Direct Integrations

`inventory-service` is the **core domain service** for stock state; it is deliberately shielded from external protocol concerns:

- External actors (ERP, TMS, scanners, future vendors) never call `inventory-service` directly. They reach inventory state only indirectly — by triggering an ASN-receive in `inbound-service`, an order in `outbound-service`, or by issuing a REST mutation that the `gateway-service` routes to inventory's internal REST API (`/api/v1/inventory/**`).
- The internal REST surface (`inventory-service-api.md`) is consumed by **internal operators only** (admin UI, support tooling) — there is no external client identity exchanged at this boundary; gateway-level auth + per-route rate limit (per `gateway-service/application.yml`) is the entirety of the protection layer.
- Cross-service communication uses Kafka (master snapshots in, reservation/confirmation events out) — Kafka is project infrastructure, not an external vendor (see § Internal vs External Boundary below).

This boundary keeps the `transactional` trait's invariants (T2 no-XA, T3 idempotency, T8 outbox) local to one DB + one Kafka cluster, and lets the `integration-heavy` posture concentrate in the sibling services where the actual vendor surface lives.

---

## Internal vs External Boundary

| Component | Classified as | Rationale |
|---|---|---|
| Kafka cluster | **infrastructure** (project-shared) | Same cluster as every other WMS service, accessed via SASL/SCRAM (dev/stg) or mTLS (prod); not a "vendor" in the I1–I10 sense |
| PostgreSQL (`inventory_service_db`) | **infrastructure** (service-owned) | Logical DB owned exclusively by inventory; no other service connects |
| Redis | **infrastructure** (project-shared, REST `Idempotency-Key` store) | Same Redis as other services; fails closed on outage (matches inbound/outbound convention) |
| `master-service` Kafka topics (in) | **internal service event** | Consumed via `wms.master.{warehouse,zone,location,sku,lot,partner}.v1`; cross-link in `architecture.md § Dependencies` |
| `inbound-service` / `outbound-service` Kafka topics (in) | **internal service event** | Reservation triggers, ASN-driven inventory updates — internal-event contracts |

I9 bulkhead targets — dedicated thread pool / connection pool per external HTTP vendor — are **not applicable** to `inventory-service` v1 because there is no outbound HTTP call to bulkhead. HikariCP for Postgres and Lettuce for Redis remain in their service-default sizing per [`architecture.md`](architecture.md) § Dependencies.

---

## Required-Artifact Compliance Map

`rules/traits/integration-heavy.md § Required Artifacts` (1–6) under zero-state:

| # | Artifact | inventory-service applicability |
|---|---|---|
| 1 | 외부 연동 카탈로그 | **This file** — explicit zero-state catalog above |
| 2 | Circuit/Retry 정책 표 | N/A — no outbound vendor HTTP calls to govern |
| 3 | Webhook 인증 규약 | N/A — no inbound webhooks (sibling-only) |
| 4 | DLQ 재처리 절차 문서 | Internal Kafka DLT covered by [`idempotency.md`](idempotency.md) § Dedupe + Saga + `<topic>.DLT` route; no vendor-specific DLQ |
| 5 | Adapter 레이어 구조 | Hexagonal ports/adapters in [`architecture.md`](architecture.md) § Architecture Style — but adapter-out side has no vendor adapter, only Kafka producer + Postgres + Redis |
| 6 | WireMock 테스트 스위트 | N/A — no vendor HTTP path to mock. Internal-event tests use Testcontainers Kafka (per `architecture.md` § Testing Requirements) |

If a future external integration enters scope (see § Evolution Paths), this map flips from "N/A" to concrete per-row content; that's the editing trigger for the next revision of this file.

---

## Evolution Paths (Not In v1)

Candidate integrations that, if scoped, would migrate this file out of zero-state:

| Candidate | Trigger | New required content |
|---|---|---|
| **Direct cycle-count / scanner adapter** | Cycle-count workflow promoted from "outside" (today only `INVENTORY_ADMIN` manual write-off, [`architecture.md`](architecture.md) § Evolution Paths) to an external scanner / RFID device pushing counts | I1 timeout, I2 circuit, I3 retry, I6 webhook auth + signature, I9 dedicated bulkhead |
| **Serial-number traceability** (제약 / 식품) | `compliance: []` field in `PROJECT.md` gains a regulated tag (e.g., `FDA`, `KFDA`, `EU-GDPR-traceability`); new aggregate `SerialNumberInventory` materializes | External traceability registry vendor (push) + lot-level audit hook |
| **Lot strategy externalization (FEFO/FIFO via vendor optimizer)** | v2 picking pushes allocation decision to a vendor optimizer instead of outbound-service deciding | I7 vendor SDK isolation, I8 internal-model translation, I9 bulkhead |

Until one of these triggers fires, this file remains zero-state.

---

## References

- [`architecture.md`](architecture.md) — § Identity, § Dependencies, § Open Items
- [`domain-model.md`](domain-model.md) — entities and invariants (no external dependency)
- [`idempotency.md`](idempotency.md) — REST + event dedupe (internal only)
- [`../../contracts/events/inventory-events.md`](../../contracts/events/inventory-events.md) — published event schemas
- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — sibling non-zero reference
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — sibling non-zero reference (TMS marquee)
- `rules/traits/integration-heavy.md` — Required Artifacts + I1–I10
- `platform/observability.md` — required metrics (inventory-side metrics are internal-event focused)
- `platform/error-handling.md` — error code catalog (inventory error codes registered: `RESERVATION_NOT_FOUND`, `RESERVATION_QUANTITY_MISMATCH`, `LOCATION_INACTIVE`, `SKU_INACTIVE`, `LOT_INACTIVE`, `LOT_EXPIRED`)
