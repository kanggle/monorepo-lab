# Task ID

TASK-BE-049

# Title

outbound-service TMS real adapter — Resilience4j circuit breaker + retry + timeout + bulkhead + WireMock IT

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

---

# Goal

Replace `StubTmsClientAdapter` (which currently logs and returns success) with a real `TmsClientAdapter` that performs HTTP `POST /shipments` to the external TMS, wired with Resilience4j patterns mandated by `outbound-service/architecture.md` §TMS Integration and the `integration-heavy` trait rules I1–I4 / I7–I9.

After this task is complete, the saga `SHIPPED → COMPLETED` transition is driven by a real HTTP roundtrip with vendor failure modes covered.

---

# Scope

## In Scope

- New `TmsClientAdapter` (HTTP) implementing `ShipmentNotificationPort`, replacing the stub.
- Resilience4j wiring:
  - Connect timeout 5s, read timeout 30s (I1)
  - Circuit breaker: 50% threshold over 20 calls, open 60s (I2)
  - Retry: 3 attempts, exponential backoff 1s/2s/4s ±200ms jitter (I3)
  - Idempotency-Key header populated with `shipment_id` (I4); fallback `tms_request_dedupe` table when vendor doesn't honor
  - Bulkhead: dedicated thread pool + connection pool, size 10 (I9)
- Internal-model translation `Shipment → TmsShipmentRequest`; response → `TmsAcknowledgement` (I8)
- Manual ops endpoint `POST /api/v1/outbound/shipments/{id}/retry-tms-notify` (already in spec — implement if not present)
- WireMock IT (I10) covering: success, timeout (3 retries → `SHIPPED_NOT_NOTIFIED`), 5xx (3 retries → `SHIPPED_NOT_NOTIFIED`), 4xx (no retry, `SHIPPED_NOT_NOTIFIED`), circuit open fast-fail, manual retry success.
- Metrics: `outbound.tms.request.count{result}`, `outbound.tms.request.duration.seconds`, `outbound.tms.circuit.state{vendor}`, `outbound.tms.retry.count{attempt}`.

## Out of Scope

- Carrier rating / TMS quote API (v2)
- Multiple TMS vendor fanout (v2)
- Webhook from TMS for delivery confirmation (v2)
- Saga-recovery sweeper (separate task — also surfaced from outbound dry-run)

---

# Acceptance Criteria

- [ ] `StubTmsClientAdapter` replaced by `TmsClientAdapter`; standalone profile retains a stub fallback per `backend/standalone-profile/SKILL.md`.
- [ ] All 6 WireMock scenarios above pass in `:integrationTest`.
- [ ] On TMS retry exhaustion, saga state transitions `SHIPPED → SHIPPED_NOT_NOTIFIED`, alert metric increments, stock remains consumed.
- [ ] Manual retry endpoint succeeds: `SHIPPED_NOT_NOTIFIED → COMPLETED` on next-attempt success.
- [ ] Circuit-breaker open state surfaces `outbound.tms.circuit.state=2` and forces fast-fail (no HTTP call).
- [ ] Idempotency-Key header carries `shipment_id`; double-fire of `confirmShipping` for the same shipment results in single TMS-side effect (vendor honors it OR our `tms_request_dedupe` covers).
- [ ] All Micrometer metrics enumerated above are present and tagged correctly.
- [ ] `outbound-service/architecture.md` §TMS Integration matches implementation; if any gap, spec updated first per `platform/spec-first-rule`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md` and `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`.

- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`
- `rules/domains/wms.md` (Outbound bounded context, W4/W5 saga steps)
- `rules/traits/integration-heavy.md` (I1–I10)
- `rules/traits/transactional.md` (T2 distributed-tx forbidden, T6 compensation)
- `specs/services/outbound-service/architecture.md` §TMS Integration, §Saga, §Observability
- `specs/services/outbound-service/external-integrations.md` (Open Item — author or update)

# Related Skills

- `.claude/skills/cross-cutting/resilience4j` (if present)
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/backend/standalone-profile/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/outbound-service-api.md` §Manual TMS Retry
- `specs/services/outbound-service/external-integrations.md` (TMS request/response schema; if not present, author as part of this task)

---

# Target Service

- `outbound-service`

---

# Architecture

Follow:

- `specs/services/outbound-service/architecture.md` (Hexagonal — `TmsClientAdapter` lives in `adapter/out/tms/`, implements `ShipmentNotificationPort`)

---

# Implementation Notes

- Use Spring's `RestClient` (Java 21) over `WebClient` for synchronous outbound — saga step is synchronous after `SHIPPED` transition.
- TMS API key per environment loaded via `@Value("${outbound.tms.api-key}")` from Spring property source backed by Secret Manager (`platform/security-rules.md`).
- Bulkhead: configure `Resilience4j` `ThreadPoolBulkhead` (size 10) and `ConnectionPool` (Apache HttpClient 5 with same size); annotate adapter method with `@Bulkhead(type = THREADPOOL)`.
- `TmsAcknowledgement` is the internal type; do NOT leak `TmsShipmentResponse` (vendor DTO) into the application/domain layer (I8).
- The `tms_request_dedupe(request_id PK, sent_at, response_snapshot)` Flyway migration is required if not already present — schema is in spec but not yet committed.

---

# Edge Cases

- TMS returns 422 with body claiming "shipment already exists" → treat as success (vendor-side dedupe worked); record `TmsAcknowledgement` from cached body.
- Network partition during retry → connection-pool starvation → bulkhead rejects with `BulkheadFullException`; treat as `EXTERNAL_SERVICE_UNAVAILABLE`.
- Circuit half-open during a manual-retry call → single probe attempt; on failure, circuit reopens 60s.
- Vendor returns 200 but body indicates business failure → translate to `EXTERNAL_DEPENDENCY_BUSINESS_ERROR`, do NOT retry.
- Same `shipment_id` POSTed concurrently from two `confirmShipping` retries → `tms_request_dedupe` UNIQUE on `request_id` rejects the second; the first wins.

---

# Failure Scenarios

- All 3 retries time out → saga `SHIPPED_NOT_NOTIFIED`, alert fires (`outbound.alert.tms.notify.failure`), stock remains consumed, ops manually retries.
- Circuit opens during peak load → callers receive `EXTERNAL_SERVICE_UNAVAILABLE` immediately; no HTTP call leaves the JVM until timer elapses.
- Manual retry after circuit opens → fast-fail; ops waits for circuit to close before re-clicking.
- Vendor-side outage extends beyond 1 hour → repeated alerts; the existing saga sweeper (separate task) does NOT re-emit `outbound.shipping.confirmed` for sagas in `SHIPPED_NOT_NOTIFIED` (they require manual re-trigger because stock is already consumed).

---

# Test Requirements

- Unit: `TmsClientAdapter` against fake `RestClient` — payload translation, header population, idempotency-key population.
- Integration (WireMock + Testcontainers): all 6 scenarios in Acceptance Criteria.
- Contract test: `Shipment → TmsShipmentRequest` schema matches `external-integrations.md`.
- Failure-mode (per `transactional` Required Artifact 5): `confirmShipping` POST twice → single TMS call.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (unit + IT + contract)
- [ ] Tests passing locally and in CI Linux Integration job
- [ ] Contracts updated (`outbound-service-api.md` if needed)
- [ ] Spec updated first (`external-integrations.md` if absent)
- [ ] Architecture diagram updated if vendor-fanout topology changed
- [ ] Resilience4j config externalised to `application.yml` (not hardcoded)
- [ ] Manual retry endpoint smoke-tested via gateway
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-code wms outbound-service` dry-run (Manual Finding #2). PRs #309–#313 closed the structural sweep; this task closes the **feature gap** between current stub and the spec-mandated production behaviour.
