# TASK-SCM-BE-042 — logistics-service bootstrap (skeleton + ShipmentDispatchPort + EasyPost adapter)

**Status:** done
**Type:** TASK-SCM-BE
**Depends on / 전제:** [TASK-SCM-BE-041](../done/TASK-SCM-BE-041-logistics-service-spec-suite.md) **done** (the specs this task builds against) · [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** (Phase 1). Reads: [ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) §D5/§D7.
**후속 / blocks:** TASK-SCM-BE-043 (굿스플로 adapter + `CarrierRouter`) · TASK-SCM-BE-044 (`FulfillmentRouter` self-branch + `outbound.shipping.confirmed` consumer). Both require this bootstrap.

> **Phase-1 slice.** This task stands up the service and its **outbound carrier-dispatch machinery for one vendor (EasyPost)** plus the operator `:retry` surface — the foundational skeleton. It deliberately does **not** wire the Kafka seam consumer (BE-044) or the second vendor / `CarrierRouter` (BE-043); with one vendor no router is needed, and the dispatch path is exercised via seeded dispatch rows + WireMock + the `:retry` endpoint. Precedent: TASK-SCM-BE-024 (demand-planning-service bootstrap).

---

## Goal

Bootstrap the scm `logistics-service` Spring Boot application (Hexagonal) exactly as
declared in `specs/services/logistics-service/architecture.md`, delivering a
deployable, CI-wired service whose **EasyPost dispatch path** and **operator retry**
are fully testable (WireMock + Testcontainers). Realises **ADR-MONO-053 Phase 1**,
the first task in which real code lands.

## Scope

**In scope** (new `projects/scm-platform/apps/logistics-service/` unless noted):

1. **Gradle module** — `settings.gradle` include (root, shared — bootstrap-scoped, per BE-024 precedent) + `build.gradle` (Spring Boot 3.4, Java 21, deps: web, data-jpa, kafka scaffold, flyway, postgres, resilience4j, oauth2-resource-server, `libs/` shared modules as used by demand-planning).
2. **App skeleton** — `LogisticsServiceApplication`, `application.yml` (+ `application-standalone.yml`); profiles `default` / `standalone`.
3. **Flyway `V1__init.sql`** (schema `scm_logistics`) — `dispatch` (id, shipment_id UNIQUE, shipment_no, order_id, order_no, tenant_id, carrier_code, tracking_no, status, failure_reason, vendor, created_at, updated_at, version), `dispatch_request_dedupe` (request_id PK, sent_at, response_snapshot), `processed_events` (event_id PK, …).
4. **Domain** (pure Java) — `Dispatch` aggregate + status machine `PENDING→DISPATCHED` / `PENDING→DISPATCH_FAILED→DISPATCHED`; `Carrier`, value objects (`ShipmentId`, `TrackingNo`, `CarrierCode`); `ProcessedEvent`. Framework-free, unit-tested.
5. **Application** — `DispatchShipmentUseCase` (dispatch a `PENDING`/`DISPATCH_FAILED` dispatch via the port, idempotent), `RetryDispatchUseCase`; outbound ports `ShipmentDispatchPort`, `DispatchPersistencePort`. (`CarrierRouter` / `FulfillmentRouter` are **stubs/absent** here — single vendor; introduced in BE-043/044.)
6. **Outbound dispatch adapter** — `EasyPostDispatchAdapter` (`@Profile("!standalone")` implements `ShipmentDispatchPort`) + `StandaloneDispatchAdapter` (`@Profile("standalone")` in-memory ack) + package-private `EasyPostShipmentRequest`/`Response`/`Mapper` + `dispatch_request_dedupe` persistence. Auth = HTTP Basic (API key as username, empty password); base `https://api.easypost.com/v2`; `Idempotency-Key={shipment.id}`.
7. **Config** — dedicated `easypost-client` RestClient + Apache HttpClient 5 pool (`maxTotal=10`) + Resilience4j `easyPostDispatchCircuit` / `easyPostDispatchRetry` / `Bulkhead(10)` per `external-integrations.md` §1; OAuth2 RS security (`tenant_id=scm` fail-closed + entitlement-trust dual-accept, local `isEntitled` per SCM-BE-019 blueprint).
8. **Inbound web** — `DispatchController`: `GET /api/v1/logistics/dispatches/{id}` (inspect) + `POST /api/v1/logistics/dispatches/{id}:retry` (re-drive; naturally idempotent — already-`DISPATCHED` returns cached ack, no vendor call). Standard `{code,message}` envelope; codes `DISPATCH_NOT_FOUND`, `CARRIER_UNROUTABLE`, `DISPATCH_ALREADY_COMPLETED`.
9. **Persistence adapter** — JPA entities (package-private) + Spring Data repos + adapter implementing `DispatchPersistencePort`.
10. **Infra wiring** — gateway-service route `/api/v1/logistics/**` + Traefik label + docker-compose service (`logistics.local` per ADR-MONO-001) + register in the scm CI lanes (build / Integration(scm) / E2E(scm)) as BE-024 did. Verify the scm `dorny/paths-filter` entry covers the new service dir (pure-positive; no negation).
11. **Error codes** — register `DISPATCH_NOT_FOUND` / `CARRIER_UNROUTABLE` / `DISPATCH_ALREADY_COMPLETED` in `rules/domains/scm.md` + `platform/error-handling.md` so the error-code registry guards stay green.

**Out of scope** (named follow-ups):
- `ShippingConfirmedConsumer` / Kafka seam consumption → **BE-044**.
- 굿스플로 adapter + `CarrierRouter` → **BE-043**.
- `FulfillmentRouter` (even the self-branch) → **BE-044**.
- 3PL (`ThirdPartyFulfillmentPort`, `THIRD_PARTY_LOGISTICS` node) → Phase 2 (D8-3).
- wms TMS-adapter retirement (D8) → separate cross-project TASK-MONO-XXX.

## Acceptance Criteria

- [ ] `./gradlew :projects:scm-platform:apps:logistics-service:build` succeeds; the module is in `settings.gradle` and `project-overview.md` service map (drift guards green).
- [ ] Flyway `V1__init.sql` applies on a clean Testcontainers PostgreSQL (`ddl-auto=validate` passes against the entities).
- [ ] `Dispatch` status machine unit tests: `PENDING→DISPATCHED`, `PENDING→DISPATCH_FAILED→DISPATCHED`, and rejected illegal transitions; idempotent re-dispatch is a no-op.
- [ ] **WireMock EasyPost IT matrix**: success (201→`DISPATCHED`+tracking), timeout, 5xx (retry→circuit), 4xx (no retry→`DISPATCH_FAILED`), 429 (retry), bulkhead-full, and **idempotency replay** (2nd send with same key → cached ack, **no 2nd WireMock call**).
- [ ] `@WebMvcTest DispatchController`: `GET` inspect; `:retry` re-drives a `DISPATCH_FAILED` → `DISPATCHED`; `:retry` on an already-`DISPATCHED` returns cached ack with **no** vendor call; unknown id → `DISPATCH_NOT_FOUND`.
- [ ] Tenant fail-closed IT: non-`scm` / non-entitled token → `403 TENANT_FORBIDDEN`; entitled → pass.
- [ ] `standalone` profile boots with **no** vendor credentials and `StandaloneDispatchAdapter` serves an in-memory ack (local/CI bring-up).
- [ ] gateway route + Traefik + compose added; `logistics.local` resolves the service; CI scm Integration + E2E lanes include the new module and are **GREEN** (CI is authority — Windows local cannot run Testcontainers).
- [ ] New error codes registered; error-code registry guards green.
- [ ] No behaviour outside the declared scope (no consumer, no 굿스플로, no router).

## Related Specs

- `projects/scm-platform/specs/services/logistics-service/architecture.md` — **the build target** (layers, ports, status machine, Service Type compliance)
- `projects/scm-platform/specs/services/logistics-service/external-integrations.md` §1 — EasyPost integration policy (timeouts, circuit, retry, idempotency, bulkhead)
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md` — sibling bootstrap blueprint (OAuth2 RS, tenant dual-accept, Hexagonal layout)
- `platform/service-types/event-consumer.md` + `rest-api.md` — Service Type rules (consumer scaffolding present, wiring in BE-044)

## Related Contracts

- `projects/scm-platform/specs/contracts/events/logistics-dispatch-subscriptions.md` — the seam the service will consume (consumer **wired in BE-044**; this task lays the Kafka config scaffold only)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` — reserve/wire `/api/v1/logistics/**`
- `rules/domains/scm.md` + `platform/error-handling.md` — error-code registry (add the 3 new codes)

## Edge Cases

- **Single vendor, no router.** With only EasyPost, `DispatchShipmentUseCase` calls the port directly; `CarrierRouter` is not introduced here — do not stub a fake multi-vendor selection that BE-043 must then unpick.
- **Idempotency continuity.** `Idempotency-Key={shipment.id}` + `dispatch_request_dedupe` must dedupe a repeated dispatch/`:retry` identically to the wms interim (ADR-052 §2.7) — the WireMock replay test is the guard.
- **No live trigger yet.** The only inbound in this slice is `:retry` (+ inspect); a dispatch row is created by the consumer (BE-044). Tests seed dispatch rows directly. Do not add a "create dispatch" REST endpoint to compensate (that is not in the architecture; the trigger is the event).
- **Standalone parity.** `StandaloneDispatchAdapter` must return a deterministic ack shape so slice/IT assertions hold without a vendor.
- **Windows local = not authority.** Testcontainers IT will SKIP locally on Windows; CI Linux is the authoritative gate (per project memory) — push and read the junit XML, do not declare green off a local `BUILD SUCCESSFUL` that skipped IT.

## Failure Scenarios

- **A — Scope creep.** If the consumer, 굿스플로, `CarrierRouter`, or `FulfillmentRouter` appear in the diff, this task absorbed BE-043/044 — split back out (ADR-053 §D7 phasing).
- **B — Vendor coupling leaks.** If `EasyPostShipmentRequest/Response` types cross the `ShipmentDispatchPort` boundary (I8), reject — vendor DTOs stay package-private in the adapter.
- **C — Shared pool.** If the EasyPost HttpClient/Resilience4j pool is shared with any other pool (I9), fix — dedicated `easypost-client` only.
- **D — Fail-open idempotency/tenant.** Tenant gate and dispatch dedupe must **fail closed**; a fail-open shortcut is a correctness defect (SCM-BE-019 / integration-heavy posture).
- **E — CI-RED at merge.** A new-module bootstrap commonly reds the scm Integration lane first (drift, Flyway validate, IT wiring). Do NOT merge on CI-RED — restore GREEN or split. The 3-dim merge-verification rule applies.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): full Spring Boot service bootstrap with integration-heavy resilience wiring + Testcontainers/WireMock IT → **Opus** (backend-engineer dispatch, `model=opus`). Not a mechanical edit.
