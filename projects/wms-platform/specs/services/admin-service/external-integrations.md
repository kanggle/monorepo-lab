# admin-service — External Integrations

External vendor catalog for `admin-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

**Zero-state declaration**: `admin-service` has **no direct external vendor
integrations** in v1. It owns operator user / role / settings as authoritative
data, projects 5 sibling-service event streams into a CQRS read-model for
KPI / dashboard queries, and publishes its own user/role/settings events —
all internal to the WMS. External traffic enters via `gateway-service` JWT-
validated REST calls; outbound traffic is exclusively to project
infrastructure (Postgres + Kafka).

This file exists to make that boundary explicit and to set the entry point
for any future external integration (external IdP / SAML / SCIM — see
§ Evolution Paths).

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| _(none in v1)_ | — | — | — | — |

Sibling integration surfaces (for cross-reference; not consumed by admin itself):

- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — ERP ASN webhook reference.
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — TMS marquee reference.
- [`../notification-service/external-integrations.md`](../notification-service/external-integrations.md) — Slack Incoming Webhooks (BE-158).
- [`../inventory-service/external-integrations.md`](../inventory-service/external-integrations.md) — sibling zero-state (BE-156, primary template).

`admin-service` consumes 5 sibling event streams (master / inventory / inbound / outbound / notification) for read-model projection and publishes `admin.user.*` / `admin.role.*` / `admin.settings.*` audit events.

---

## Why Zero Direct Integrations

`admin-service` is a CQRS read-side projector + operator-data authority. By design:

- **Read path**: dashboard / KPI queries hit the **denormalised read-model** (built from sibling events) — never cross-service joins, never external API calls. The architecture explicitly forbids cross-service joins at query time per [`architecture.md`](architecture.md) § Architecture Style Rationale.
- **Write path**: user / role / settings mutations are persisted locally and published as events for downstream consumers (sibling services apply role / settings changes to their own config).
- **Authentication delegated to gateway-service**: JWT issuance / validation lives in `gateway-service`; `admin-service` owns the user record only. No IdP / OAuth2 outbound call from admin.

The Layered architecture exception ([`architecture.md`](architecture.md) § Architecture Style Rationale — "read-heavy CQRS, minimal domain logic") follows from this absence of external coordination: with no vendor SDK to isolate, the Hexagonal port/adapter ceremony adds cost without buying isolation.

---

## Internal vs External Boundary

| Component | Classified as | Rationale |
|---|---|---|
| Kafka cluster | **infrastructure** (project-shared) | Consumes 5 sibling topics + publishes own audit topics |
| PostgreSQL (`admin_service_db`) | **infrastructure** (service-owned) | User / role / settings + denormalised projection tables |
| `gateway-service` (JWT validation) | **internal sibling** | Gateway reads admin user records (cross-service DB read or admin event consume) — admin itself does not call gateway |
| Sibling services (5) | **internal event publishers** | Admin reads their events; never calls them via REST |

I9 bulkhead targets — dedicated thread pool / connection pool per external HTTP vendor — are **not applicable** to `admin-service` v1 because there is no outbound HTTP call to bulkhead. HikariCP for Postgres remains in service-default sizing per [`architecture.md`](architecture.md) § Dependencies.

---

## Required-Artifact Compliance Map

`rules/traits/integration-heavy.md § Required Artifacts` (1–6) under zero-state:

| # | Artifact | admin-service applicability |
|---|---|---|
| 1 | 외부 연동 카탈로그 | **This file** — explicit zero-state catalog above |
| 2 | Circuit/Retry 정책 표 | N/A — no outbound vendor HTTP calls to govern |
| 3 | Webhook 인증 규약 | N/A — no inbound webhooks (admin REST is JWT-only via gateway) |
| 4 | DLQ 재처리 절차 문서 | Internal Kafka consumer DLT covered by [`idempotency.md`](idempotency.md); no vendor-specific DLQ |
| 5 | Adapter 레이어 구조 | Layered (not Hexagonal — deliberate exception per `architecture.md` § Architecture Style Rationale); no adapter-out vendor surface |
| 6 | WireMock 테스트 스위트 | N/A — no vendor HTTP path to mock. Internal-event tests use Testcontainers Kafka (per `architecture.md` § Testing Requirements) |

---

## Evolution Paths (Not In v1)

Candidate integrations that would migrate this file out of zero-state:

| Candidate | Trigger | New required content |
|---|---|---|
| **External IdP (SAML / SCIM)** | Operator base needs SSO against corporate AD / Okta / Azure AD; admin becomes consumer of SCIM provisioning push or SAML assertion | I1 timeout, I2 circuit, I3 retry, I6 webhook auth (SCIM POST receiver), I7 IdP SDK isolation, user record sync state-machine |
| **External KPI / BI sink** (Snowflake / BigQuery / Tableau) | Real-time dashboard demand outgrows the local read-model; admin pushes projection slices to a vendor warehouse | I1-I4 outbound, I8 internal-model translation, possibly real-time trait promotion |
| **External audit-log shipper** (Splunk / Datadog audit) | Compliance requires immutable audit-trail export | Outbound push adapter, I4 vendor-side idempotency |
| **Notification preference UI** (cross-vendor delivery routing) | Admin gains per-user preference management for `notification-service` channels | Mostly notification-service work; admin only exposes preference REST + publishes preference events |

Until one of these triggers fires, this file remains zero-state.

---

## References

- [`overview.md`](overview.md) — Service identity + Dependent Systems
- [`architecture.md`](architecture.md) — § Architecture Style Rationale (Layered exception), § Dependencies, § CQRS read-side
- [`domain-model.md`](domain-model.md) — User / Role / UserRoleAssignment / OperationalSettings + projection tables
- [`idempotency.md`](idempotency.md) — Projection-side T8 dedupe
- [`../../contracts/events/admin-events.md`](../../contracts/events/admin-events.md) — admin.user.* / admin.role.* / admin.settings.* schemas
- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — sibling non-zero reference
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — sibling non-zero reference (TMS marquee)
- [`../inventory-service/external-integrations.md`](../inventory-service/external-integrations.md) — sibling zero-state (BE-156)
- [`../notification-service/external-integrations.md`](../notification-service/external-integrations.md) — sibling non-zero reference (Slack, BE-158)
- `../../../../../rules/traits/integration-heavy.md` — Required Artifacts + I1–I10
- `../../../../../platform/security-rules.md` — JWT validation rules (gateway-owned)
- `../../../../../platform/observability.md` — required metrics
