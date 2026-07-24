# TASK-SCM-BE-043 — 굿스플로 dispatch adapter + CarrierRouter (multi-vendor routing)

**Status:** review
**Type:** TASK-SCM-BE
**Depends on / 전제:** [TASK-SCM-BE-042](../done/TASK-SCM-BE-042-logistics-service-bootstrap.md) **done** (the service skeleton + `ShipmentDispatchPort` + EasyPost adapter this task extends) · [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** §D3 (CarrierRouter). Reads: [ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) §D5.
**후속 / blocks:** TASK-SCM-BE-044 (`FulfillmentRouter` self-branch + `outbound.shipping.confirmed` consumer — the live trigger that feeds the routing signal this task consumes). BE-044 requires this router.

> **Phase-1 slice (2 of 3).** BE-042 stood up the service with **one** vendor (EasyPost), so the use case calls the port directly (`DispatchShipmentUseCase` comment: *"With one vendor there is no CarrierRouter — introduced in BE-043/044"*). This task adds the **second aggregator (굿스플로)** and the **`CarrierRouter`** that selects exactly one vendor per shipment — the moment two `!standalone` `ShipmentDispatchPort` beans exist, direct injection is ambiguous and the router is mandatory. It does **not** wire the Kafka seam consumer (BE-044); routing is exercised via seeded dispatch rows + the `:retry` endpoint + WireMock.

---

## Goal

Extend `logistics-service` to **multi-vendor carrier dispatch** exactly as declared in
`specs/services/logistics-service/external-integrations.md` §2 (굿스플로) and
`architecture.md` § Multimodal seam (`CarrierRouter`): add the 굿스플로 aggregator
adapter behind the existing `ShipmentDispatchPort`, and introduce `CarrierRouter`
which resolves the vendor from the shipment's routing signal (`carrierCode`) —
present → owning vendor, null/unmapped → the configured **default vendor** with a
`CARRIER_UNROUTABLE` degrade log (never a silent drop). Realises **ADR-MONO-053 §D3**.

## Scope

**In scope** (under `projects/scm-platform/apps/logistics-service/` unless noted):

1. **굿스플로 dispatch adapter** — `GoodsflowDispatchAdapter` (`@Profile("!standalone")` implements `ShipmentDispatchPort`) + package-private `GoodsflowShipmentRequest` / `GoodsflowShipmentResponse` / `GoodsflowShipmentMapper` (vendor DTOs never cross the port, I8). Mirrors the EasyPost adapter's shape (`external-integrations.md` §2). Auth = **API key header** (vendor header name confirmed against the 굿스플로 OPEN API at implementation); base = test env `https://test-api.goodsflow.io` for stg/dev.
2. **Dedicated 굿스플로 client + resilience** — `goodsflow-client` RestClient + **its own** Apache HttpClient 5 pool (`maxTotal=10`, `defaultMaxPerRoute=10`) + independent Resilience4j instances `goodsflowDispatchCircuit` / `goodsflowDispatchRetry` / `Bulkhead(10)` (same policy *shape* as EasyPost §1.5/1.6/1.8, **separate instances** — I9 "no pool shared across vendors"). Apply the two BE-042 retry lessons: fallback on the **outermost** `@Retry` aspect; `.disableAutomaticRetries()` on the HttpClient so Resilience4j is the sole retry authority (→ exactly `maxAttempts` vendor calls).
3. **`CarrierRouter`** (framework-free application/domain service) — `select(carrierCode) → ShipmentDispatchPort`: a carrier→vendor registry (domestic carriers e.g. `CJ-LOGISTICS`/`HANJIN`/`LOTTE`/`KOREA-POST` → 굿스플로; international carriers → EasyPost) driven by config; `carrierCode` **null or unmapped** → the **configured default vendor** + a `CARRIER_UNROUTABLE` degrade log (metric/log, **not** a thrown error that drops the shipment). Exactly one vendor per shipment. Fully unit-tested.
4. **Routing-signal persistence** — add a **`requested_carrier_code`** column to `dispatch` (Flyway **`V2`**, additive nullable — V1 checksum untouched) + thread it through `Dispatch.create`/`reconstitute`, the JPA entity, and `DispatchPersistenceAdapter`. This is the *requested* carrier from the seam event (distinct from the existing `carrier_code`, which is the **confirmed** carrier set from the vendor ack on `DISPATCHED`). Storing it is what lets `:retry` re-route a `DISPATCH_FAILED` dispatch (which never reached `DISPATCHED`, so `vendor` is null) back to the correct vendor. Populated by the consumer in BE-044; nullable here → default-vendor route.
5. **Route via `CarrierRouter`** — refactor `DispatchShipmentUseCase` to resolve the port through `CarrierRouter.select(dispatch.requestedCarrierCode)` instead of the single injected `ShipmentDispatchPort`, and update the stale "one vendor, no router" javadoc. `RetryDispatchUseCase` is unchanged (it delegates to `DispatchShipmentUseCase`, which now routes). The `Idempotency-Key = {shipment.id}` and `dispatch_request_dedupe` short-circuit are preserved **per selected vendor** (the snapshot records which vendor a request went to, so a shipment cannot be double-dispatched across vendors — §2.7).
6. **Config** — carrier→vendor map + default-vendor property (e.g. `logistics.carrier-router.default-vendor=EASYPOST`, `logistics.carrier-router.domestic-carriers=[...]`); 굿스플로 `@ConfigurationProperties` (`GoodsflowClientProperties`: base-url, api-key, header-name, timeouts, pool). Standalone profile: the single `StandaloneDispatchAdapter` still answers every route (no vendor split under `standalone`).
7. **Secret Manager / env fallback** — `goodsflow-{prod,test}` key retrieval mirroring EasyPost (`GOODSFLOW_API_KEY_<ENV>` v1 dev fallback); a missing 굿스플로 key fails **only** 굿스플로 dispatches → `DISPATCH_FAILED` + ops alert, EasyPost unaffected (keys/pools per-vendor, §6).
8. **Observability** — per-vendor metric labels already declared (`logistics_dispatch_*{vendor}`); ensure 굿스플로 emits with `vendor="goodsflow"` and the circuit-state gauge is wired for both vendors.

**Out of scope** (named follow-ups):
- `ShippingConfirmedConsumer` / Kafka seam consumption + the live `carrierCode` signal source → **BE-044**.
- `FulfillmentRouter` (even the self-branch) → **BE-044**.
- 3PL (`ThirdPartyFulfillmentPort`, `THIRD_PARTY_LOGISTICS` node) → Phase 2 (ADR-052 §D8-3).
- Destination-address enrichment / full label buy → separate Phase-1 follow-up (the documented "known input gap", subscriptions contract § Known input gap).
- wms TMS-adapter retirement (D8) → separate cross-project TASK-MONO-XXX.

## Acceptance Criteria

- [ ] `./gradlew :projects:scm-platform:apps:logistics-service:build` succeeds; two `!standalone` `ShipmentDispatchPort` beans coexist **without** an `@Autowired`/injection ambiguity (they are held by `CarrierRouter`, not injected directly into the use case).
- [ ] Flyway **`V2`** adds `requested_carrier_code` (additive, nullable); applies on a clean Testcontainers PostgreSQL with `ddl-auto=validate` green; **V1 checksum unchanged** (no edit to `V1__init.sql`).
- [ ] **`CarrierRouter` unit tests**: domestic carrierCode → 굿스플로; international carrierCode → EasyPost; **null** carrierCode → default vendor + `CARRIER_UNROUTABLE` degrade signal (log/metric, no drop); **unmapped** carrierCode → default vendor + degrade. Exactly one vendor selected.
- [ ] **WireMock 굿스플로 IT matrix** (independent stub, mirrors §8): success (→`DISPATCHED`+운송장번호/tracking), timeout, 5xx (retry→circuit), 4xx (no retry→`DISPATCH_FAILED`), 429 (retry), bulkhead-full, **idempotency replay** (2nd send same key → cached ack, **no 2nd WireMock call**). The 굿스플로 pool/circuit/retry are **independent** of EasyPost's (I9) — verify a 굿스플로 circuit-open does not trip EasyPost.
- [ ] **Routing IT / slice**: a seeded `DISPATCH_FAILED` dispatch with a domestic `requested_carrier_code` re-drives via `:retry` to **굿스플로** (WireMock 굿스플로 hit, not EasyPost); an international one routes to EasyPost; a null one routes to the default vendor.
- [ ] Cross-vendor dedupe: the same `shipment.id` cannot be dispatched to two vendors — `dispatch_request_dedupe` short-circuits regardless of which vendor the router selects (the snapshot records the vendor).
- [ ] `standalone` profile still boots with **no** vendor credentials; `StandaloneDispatchAdapter` answers every route (router degrades to the single stub).
- [ ] scm Integration + E2E CI lanes **GREEN** (CI is authority — Windows local cannot run Testcontainers; read the junit XML, do not declare green off a skipped-IT local `BUILD SUCCESSFUL`).
- [ ] No new behaviour outside scope (no consumer, no `FulfillmentRouter`, no 3PL). No new error code required (`CARRIER_UNROUTABLE` already registered in BE-042).

## Related Specs

- `projects/scm-platform/specs/services/logistics-service/external-integrations.md` **§2** (굿스플로 policy: timeouts/circuit/retry/idempotency/bulkhead) + **§7** (aggregated resilience, "no pool shared across vendors") + **§3** (both-vendor failure states, 4xx table — note the per-adapter 409 latitude the BE-042 amendment recorded)
- `projects/scm-platform/specs/services/logistics-service/architecture.md` § Multimodal seam (`CarrierRouter` selection incl. unmapped fallback) + § Failure Modes (region/carrier unmapped → default vendor)
- `projects/scm-platform/specs/services/logistics-service/external-integrations.md` §1 (EasyPost — the adapter shape 굿스플로 mirrors)

## Related Contracts

- `projects/scm-platform/specs/contracts/events/logistics-dispatch-subscriptions.md` **§ CarrierRouter selection** (`carrierCode` present → owning vendor; null → configured default + `CARRIER_UNROUTABLE`) — the authoritative routing-signal definition (the consumer that supplies it is BE-044)
- `rules/domains/scm.md` — `CARRIER_UNROUTABLE` already registered (BE-042); no new code

## Edge Cases

- **Routing signal = `carrierCode`, not a geographic `Region`.** The subscriptions contract § CarrierRouter selection makes **`payload.carrierCode`** the concrete routing signal (present → owning vendor; null → default). `architecture.md` § Multimodal seam describes this as selection "by Region" and lists a `Region` value object — treat **carrierCode as authoritative** (it is the field the seam actually carries; the seam carries **no** address/geographic region — the documented "known input gap"). Model the domestic/international split as the carrier→vendor **registry's** internal structure, not a separately-sourced `Region` field. **If `architecture.md`'s "Region" wording is inaccurate against the built router, amend that spec sentence in this PR** (specs win, but an impl-discovered spec inaccuracy is corrected in-place — the BE-042 §3 409 amendment is the precedent; do not silently diverge).
- **`requested_carrier_code` ≠ `carrier_code`.** The new column is the *requested* carrier (routing input, from the event, nullable); the existing `carrier_code` is the *confirmed* carrier (from the vendor ack, set on `DISPATCHED`). Do not conflate or reuse one column for both.
- **`:retry` on a `DISPATCH_FAILED` must re-route deterministically.** Because a failed dispatch never set `vendor`, the router must re-select from the stored `requested_carrier_code`. A dispatch with a null stored signal re-routes to the default vendor (same as first attempt) — stable, not random.
- **No pool/instance sharing (I9).** 굿스플로 and EasyPost each own a dedicated HttpClient pool + Resilience4j instance set. A shared pool or a shared circuit/retry instance is a defect — a 굿스플로 outage must not open EasyPost's circuit.
- **Repeat the BE-042 retry fixes for 굿스플로.** fallback on the outermost `@Retry` (not `@CircuitBreaker`); `.disableAutomaticRetries()` on the HttpClient — otherwise the 굿스플로 429/5xx retry count double-counts exactly as EasyPost's did (2 CI-RED rounds). The 굿스플로 WireMock retry-count assertion is the guard.
- **Windows local = not authority.** Testcontainers IT SKIPs locally on Windows; CI Linux is the gate.

## Failure Scenarios

- **A — Scope creep into BE-044.** If a `ShippingConfirmedConsumer`, `@KafkaListener` on the seam, or `FulfillmentRouter` appears in the diff, this task absorbed BE-044 — split back out (ADR-053 §D7 phasing). The live `carrierCode` source is the consumer; here the signal is seeded/stored only.
- **B — Vendor coupling leaks (I8).** If `GoodsflowShipmentRequest/Response` types cross the `ShipmentDispatchPort` boundary, reject — vendor DTOs stay package-private in the adapter.
- **C — Shared pool / shared circuit (I9).** If the 굿스플로 HttpClient pool or any Resilience4j instance is shared with EasyPost, fix — a cross-vendor circuit trip is the smell.
- **D — Silent drop on unmapped carrier.** If an unmapped/null `carrierCode` throws-and-drops (or routes to a hard-coded vendor with no degrade signal), fix — the contract mandates **configured default + `CARRIER_UNROUTABLE` degrade log**, never a silent drop (architecture.md § Failure Modes).
- **E — V1 checksum edit.** If `V1__init.sql` is modified instead of adding `V2`, Flyway validation breaks on existing DBs — additive `V2` only.
- **F — CI-RED at merge.** The 3-dim merge-verification rule applies; do not merge on a red scm Integration lane. Expect the 굿스플로 retry-count assertion to red first if the two BE-042 retry lessons are not reapplied.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): a second integration-heavy vendor adapter (resilience wiring + WireMock matrix) plus a routing domain service and a schema migration that changes the dispatch path → **Opus** (backend-engineer dispatch, `model=opus`). Not a mechanical edit.
