# master-service — External Integrations

External vendor catalog for `master-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

**Zero-state declaration**: `master-service` is the **upstream anchor** of
the WMS — sibling services consume its events, but `master-service` itself
has **no outbound dependency on any external vendor** in v1. This file
exists to make that boundary explicit and to set the entry point for any
future external master-data integration (ERP / PIM sync — see § Evolution
Paths).

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| _(none in v1)_ | — | — | — | — |

Sibling integration surfaces (for cross-reference; not consumed by master itself):

- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — ERP ASN webhook (HMAC), Kafka, infrastructure.
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — ERP order webhook + TMS push (marquee).
- [`../notification-service/external-integrations.md`](../notification-service/external-integrations.md) — Slack Incoming Webhooks (marquee, BE-158).

`master-service` publishes 6 aggregate snapshot topics (`master.warehouse.*` / `.zone.*` / `.location.*` / `.sku.*` / `.partner.*` / `.lot.*`) that the 5 sibling services consume — see [`../../contracts/events/master-events.md`](../../contracts/events/master-events.md). The relationship is publisher-only: master never reads sibling events.

---

## Why Zero Direct Integrations

`master-service` owns the single system of record for 6 WMS reference data aggregate types (Warehouse / Zone / Location / SKU / Partner / Lot identity). By design, this data has **one mutation path**: the master-service REST API (`/api/v1/master/**`), invoked by operators (admin UI) or batch importers internal to the WMS. There is no external master-data feed in v1:

- ERP / PIM systems do not push master records into the WMS — operators rekey or use internal import tooling.
- No external lookup is performed during master mutation — uniqueness / referential checks are local to `master_service_db`.
- Outbox publication targets the project Kafka cluster (infrastructure, not vendor).

This keeps the W3 (location uniqueness) and W6 (referential integrity before delete) invariants under master's exclusive control. Promoting master data to "synced with ERP" is a v2 trait change, not a v1 deferral.

---

## Internal vs External Boundary

| Component | Classified as | Rationale |
|---|---|---|
| Kafka cluster | **infrastructure** (project-shared) | Same cluster as every other WMS service; outbox publishes master events to it |
| PostgreSQL (`master_service_db`) | **infrastructure** (service-owned) | Logical DB owned exclusively by master |
| Operator UI (admin frontend) | **internal client** | Reaches master only via `gateway-service` JWT-validated REST; not an "integration" |
| Sibling services (5) | **internal event consumers** | Consume master event topics; read-only from master's perspective |

I9 bulkhead targets — dedicated thread pool / connection pool per external HTTP vendor — are **not applicable** to `master-service` v1 because there is no outbound HTTP call to bulkhead. HikariCP for Postgres remains in service-default sizing per [`architecture.md`](architecture.md) § Dependencies.

---

## Required-Artifact Compliance Map

`rules/traits/integration-heavy.md § Required Artifacts` (1–6) under zero-state:

| # | Artifact | master-service applicability |
|---|---|---|
| 1 | 외부 연동 카탈로그 | **This file** — explicit zero-state catalog above |
| 2 | Circuit/Retry 정책 표 | N/A — no outbound vendor HTTP calls to govern |
| 3 | Webhook 인증 규약 | N/A — no inbound webhooks (sibling-only ERP webhooks live in inbound/outbound) |
| 4 | DLQ 재처리 절차 문서 | Internal Kafka publisher is outbox-based; broker-side retries are infinite (`acks=all`, `enable.idempotence=true`). No vendor-specific DLQ |
| 5 | Adapter 레이어 구조 | Hexagonal ports/adapters in [`architecture.md`](architecture.md) § Architecture Style — adapter/out has `idempotency` + `messaging` (Kafka) + `persistence` (Postgres). No vendor adapter |
| 6 | WireMock 테스트 스위트 | N/A — no vendor HTTP path to mock. Internal-event tests use Testcontainers Kafka (per `architecture.md` § Testing Requirements) |

---

## Evolution Paths (Not In v1)

Candidate integrations that would migrate this file out of zero-state:

| Candidate | Trigger | New required content |
|---|---|---|
| **ERP master-data sync adapter** | Operator UX gains "import from ERP" (or scheduled batch pull from SAP / Oracle) | I1 timeout, I2 circuit, I3 retry, I4 ERP-side idempotency, I7 vendor SDK isolation |
| **PIM product-info sync** | Product information management system becomes the SKU upstream (instead of master being the SoT) | I7-I8 vendor model translation + master refactor (master becomes downstream consumer of PIM, inverting current role) |
| **External lot-traceability registry** (제약 / 식품 compliance) | `compliance: []` in `PROJECT.md` gains `FDA` / `KFDA` / `EU-GDPR-traceability`; lot identity gains audit-trail push | External traceability vendor (push) + per-lot audit hook |

Until one of these triggers fires, this file remains zero-state.

---

## References

- [`overview.md`](overview.md) — Service identity + Dependent Systems
- [`architecture.md`](architecture.md) — § Architecture Style (Hexagonal), § Dependencies
- [`domain-model.md`](domain-model.md) — 6 aggregate types (W/Z/L/S/P/Lot)
- [`idempotency.md`](idempotency.md) — REST + outbox dedupe
- [`../../contracts/events/master-events.md`](../../contracts/events/master-events.md) — 6 published event schemas
- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — sibling non-zero reference (ERP ASN)
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — sibling non-zero reference (TMS marquee)
- [`../inventory-service/external-integrations.md`](../inventory-service/external-integrations.md) — sibling zero-state (BE-156, primary template)
- [`../notification-service/external-integrations.md`](../notification-service/external-integrations.md) — sibling non-zero reference (Slack, BE-158)
- `../../../../../rules/traits/integration-heavy.md` — Required Artifacts + I1–I10
- `../../../../../rules/domains/wms.md` — W3, W6 (master invariants)
- `../../../../../platform/observability.md` — required metrics (master metrics are internal-event focused)
