# Task ID

TASK-SCM-BE-051

# Title

logistics-service: collapse the copy-pasted EasyPost/Goodsflow vendor-adapter hierarchy

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Remove the copy-paste parallel hierarchy between the EasyPost (`TASK-SCM-BE-042`) and 굿스플로/Goodsflow (`TASK-SCM-BE-043`) dispatch adapters by extracting the shared HTTP-dispatch template, the shared failure translation, the shared `RestClient`/pool construction, and the shared client-properties base — **without** merging any vendor's Resilience4j instances, connection pool, DTOs, or 4xx semantics. Behaviour and wire bytes are unchanged. The motivating evidence is not line count (~150-170 duplicated lines) but that the same two retry-configuration defects were fixed twice — `GoodsflowDispatchAdapter.java:76` and `GoodsflowDispatchClientConfig.java:57-58` explicitly document re-deriving the two `TASK-SCM-BE-042` retry lessons (`fallbackMethod` must sit on `@Retry`, not `@CircuitBreaker`; `.disableAutomaticRetries()` on the Apache HC5 client). A third vendor should not have to pay that cost again.

---

# Scope

## In Scope

- New package-private `AbstractHttpDispatchAdapter` (in `adapter/outbound/dispatch/`) holding the dedupe short-circuit → `toRequest` → POST → 429 mapping → tracking-code guard → dedupe save → `toAck` template, parameterised by vendor hooks (rest client, mapper, carrier, vendor label, extra-headers customiser, 429 exception supplier, "missing tracking id" message).
- `EasyPostDispatchAdapter` and `GoodsflowDispatchAdapter` keep their own `dispatch()`/`dispatchFallback()` method declarations carrying their own `@CircuitBreaker`/`@Retry`/`@Bulkhead` instance names — only the method **bodies** delegate to the base template.
- `VendorShipmentMapper<REQ, RES>` base (generic `serialize`/`ackFromSnapshot`); vendor mappers keep their own `toRequest`/`toAck`.
- `VendorHttpClientFactory` static helper in `config/` building the pooled Apache HC5 `RestClient` (connection config, pool sizing, `disableAutomaticRetries()`, request config) with a `Consumer<RestClient.Builder>` hook for vendor auth. Each `@Bean` still calls it separately, producing a distinct `PoolingHttpClientConnectionManager` per vendor.
- `AbstractVendorClientProperties` holding `baseUrl`/`apiKey`/`connectTimeoutSeconds`/`readTimeoutSeconds`/`poolMaxTotal`/`poolMaxPerRoute`; the two `@ConfigurationProperties` classes extend it and keep their own prefixes/defaults (Goodsflow's `apiKeyHeaderName` stays Goodsflow-only).
- Deleting the now-duplicate constant/field/accessor bodies from both concrete classes once delegated.

## Out of Scope

- Merging `EasyPostShipmentRequest/Response` with `GoodsflowShipmentRequest/Response` — vendor-shaped by contract (I8, `external-integrations.md` §1.3/§2.3); identical only by Phase-1 coincidence, and §1.9/§2.9 field mappings differ.
- Any `resilience4j.*` change in `application.yml`, including introducing a `configs.default` base-config — per-instance duplication there is deliberate vendor isolation (I9).
- Unifying 4xx handling — `external-integrations.md` states 4xx handling is per-adapter, not a shared invariant; the base exposes it as a hook only.
- `StandaloneDispatchAdapter` (no HTTP, no pool) — untouched.
- `EasyPostDispatchIntegrationTest` / `GoodsflowDispatchIntegrationTest` — also near-duplicates, but test-structure changes are a separate refactoring category/change per `platform/refactoring-policy.md`.
- `libs/java-common`'s `ResilienceClientFactory` — it uses JDK `HttpClient` with no pool sizing or `disableAutomaticRetries` support, so it cannot serve this need; no shared-library change, keeping this scm-only.
- `CarrierRouter`/`FulfillmentRouter` selection logic — unrelated to adapter internals, unchanged.

---

# Acceptance Criteria

- [ ] Baseline recorded before any edit: `logistics-service` `test` + `integrationTest` GREEN, pre-change test count written into the PR body.
- [ ] `EasyPostDispatchAdapter` and `GoodsflowDispatchAdapter` each still declare `dispatch()` annotated `@CircuitBreaker(name=<vendor>Dispatch)` + `@Retry(name=<vendor>Dispatch, fallbackMethod="dispatchFallback")` + `@Bulkhead(name=<vendor>Dispatch)`, with `fallbackMethod` on `@Retry` (the outermost aspect) — verified by reading the post-change source.
- [ ] `GoodsflowDispatchIntegrationTest.rateLimited_429_retriedExactlyMaxAttempts_thenFailed` and the EasyPost equivalent pass **unmodified**, still asserting exactly 3 vendor calls.
- [ ] `repeated5xx_opensGoodsflowCircuit_withoutTrippingEasyPost` (I9 isolation) passes unmodified.
- [ ] `idempotencyReplay_secondSendReturnsCachedAck_noSecondVendorCall` and `bulkheadFull_underConcurrency_rejectsSomeCalls` pass unmodified for both vendors.
- [ ] Two distinct `PoolingHttpClientConnectionManager` instances still exist at runtime (each vendor's config bean invoked once, producing independent instances).
- [ ] The Goodsflow API-key header is sent on every Goodsflow POST and never on an EasyPost POST; EasyPost still sends HTTP Basic — verified via existing WireMock header matching (add an assertion only if none exists; do not weaken an existing one).
- [ ] Zero test files modified in the same commit as the production change (`git diff --stat` shows no `src/test` entries).
- [ ] `StandaloneProfileBootIntegrationTest` passes unmodified.
- [ ] scm Build & Test + scm Integration (Testcontainers) CI lanes GREEN; test count identical to the recorded baseline.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` (Reduce Duplication category; § Prohibited — no test edits in the same change)
- `platform/testing-strategy.md`
- `projects/scm-platform/specs/services/logistics-service/architecture.md` (§ Layer Structure — `config/` = "@Configuration beans only (per-vendor RestClient + Resilience4j)"; § Service Type Compliance; § Failure Modes)
- `projects/scm-platform/specs/services/logistics-service/external-integrations.md` (§1.3/§1.5/§1.6/§1.8/§1.9 EasyPost, §2.3/§2.5/§2.6/§2.8 Goodsflow, § Test Suite, I9 "no pool shared across vendors", "4xx handling is per-adapter" guard)
- `projects/scm-platform/specs/services/logistics-service/iam-integration.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- `projects/scm-platform/specs/contracts/events/logistics-dispatch-subscriptions.md` — consumer seam + `CarrierRouter` selection. Unchanged by this task.
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (dispatch inspect/`:retry` routes). Unchanged.
- No vendor-facing contract exists to change — vendor DTOs are package-private by design.

---

# Target Service

- `logistics-service`

---

# Architecture

Follow:

- `projects/scm-platform/specs/services/logistics-service/architecture.md`

---

# Implementation Notes

- Spring AOP self-invocation: the annotated entry point must remain the externally-called `dispatch()`; the base-class template call is an ordinary intra-object call and must not itself be annotated, or the retry/circuit-breaker aspects would double-apply.
- Resilience4j resolves `fallbackMethod` by name **on the target class** — keep `dispatchFallback(Dispatch, Throwable)` declared on each concrete adapter (body delegating to the base), not resolved via inheritance.
- The mapper base's `ackFromSnapshot` needs the concrete response `Class<RES>` passed explicitly (generic erasure), not inferred from a type parameter.
- `@ConfigurationProperties` relaxed binding must still populate inherited setters — both `logistics.easypost.*` and `logistics.goodsflow.*` prefixes must bind every inherited field.

---

# Edge Cases

- Goodsflow's `apiKeyHeaderName` is configurable and may be set to `Authorization` (current default) — the header-injection hook must not collide with or reorder `Idempotency-Key` / `Content-Type` headers.
- `@ConfigurationProperties` on a subclass — verify relaxed binding still populates every inherited field for both vendor prefixes, not just the vendor-specific additions.

---

# Failure Scenarios

- **Isolation quietly collapsed** — someone "simplifies" by sharing one `RestClient` bean or one Resilience4j instance name across vendors; a Goodsflow outage would then open EasyPost's circuit. Guard: `repeated5xx_opensGoodsflowCircuit_withoutTrippingEasyPost` must stay unmodified and green.
- **Retry count collapses again** — moving `fallbackMethod` onto the base method (or onto `@CircuitBreaker`) converts the vendor exception per-attempt and drops the retry count from 3 to 1, reproducing the exact `TASK-SCM-BE-042` CI-RED. Guard: the `exactly(3)` assertions, unmodified.
- **Test edits smuggled in** — adjusting an assertion to make the refactor pass converts a behaviour change into a green build; the zero-test-file-diff AC exists specifically to block this.
- **Verified only by the implementer's own completion note** — annotation placement, header routing, and bean count are contract-shaped facts; verify against the actual diff, not a report describing it.

---

# Test Requirements

- No new test scenarios required — this is a behaviour-preserving structural refactor. All existing `logistics-service` unit/integration tests must pass unmodified with an identical test count to the recorded baseline.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing unmodified (same count as baseline)
- [ ] Contracts unchanged (verified)
- [ ] Specs updated only if a genuine deviation is found during implementation (none expected)
- [ ] Ready for review
