# Task ID

TASK-BE-570

# Title

ADR-MONO-058 D7 (ResilienceClientFactory) — adopt `libs/java-common.ResilienceClientFactory` for outbound HTTP clients missing timeouts (search, review, order, product, batch-worker)

# Status

done

# Owner

backend

# Task Tags

- code
- reliability
- bugfix

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

`ADR-MONO-058` § 2 D7 found `libs/java-common.ResilienceClientFactory` already exists
and several non-adopters have **zero read timeout** on outbound HTTP calls — "a live
production-risk gap (a hung downstream call blocks the calling thread indefinitely),
not a style preference." Grepping ecommerce's outbound HTTP client bootstrap code
confirms:

| Service | File | Timeout state |
|---|---|---|
| `search-service` | `config/RestClientConfig.java` (bean feeding `ProductCatalogHttpAdapter`) | **`RestClient.builder()` with no request factory at all — zero connect/read timeout.** |
| `review-service` | `infrastructure/client/OrderServiceClient.java` | **`RestClient.builder().baseUrl(baseUrl).build()` — zero timeout.** Calls order-service's `verify-purchase` endpoint synchronously from the review-creation path. |
| `order-service` | `infrastructure/config/StandaloneConfig.java` (`@Profile("standalone")` only) | **`RestClient.builder().baseUrl(...).build()` — zero timeout**, but gated to the `standalone` profile (not the default Kafka-based deployment). |
| `product-service` | `infrastructure/client/AccountServiceSellerProvisioner.java` | Has its **own** hand-rolled `JdkClientHttpRequestFactory` with explicit connect/read timeout config — not a zero-timeout risk, but a duplicated mechanism `ResilienceClientFactory` should replace. |
| `batch-worker` | `infrastructure/client/RestClients.java` | Has its **own** hand-rolled `SimpleClientHttpRequestFactory`-based timeout helper (`RestClients.timed(...)`) used by `ProductServiceClient`, `SearchServiceClient` — not a zero-timeout risk, but a duplicated mechanism.

`shipping-service` is **already a `ResilienceClientFactory` adopter**
(`HttpCarrierTrackingAdapter`, `DeliveryTrackerCarrierTrackingAdapter`,
`DeliveryTrackerTokenProvider`) — use it as ecommerce's own concrete adoption
reference, no need to look only at other projects.

After this task, `search-service` and `review-service` (the two confirmed
**zero-timeout** cases — the live production risk the ADR calls out specifically) and
`order-service`'s standalone-profile client are on `ResilienceClientFactory` with
explicit timeouts; `product-service` and `batch-worker` (which already have their own
non-zero but duplicated timeout mechanisms) are migrated to the shared factory to
close the duplication, sequenced after the zero-timeout fixes since they carry less
urgency.

---

# Scope

## In Scope

- `search-service`: replace `RestClientConfig`'s bare `RestClient.builder()` bean with
  one built via `ResilienceClientFactory`, giving `ProductCatalogHttpAdapter` explicit
  connect/read timeouts for the first time.
- `review-service`: replace `OrderServiceClient`'s bare `RestClient.builder()...build()`
  with a `ResilienceClientFactory`-built client, giving the purchase-verification call
  explicit timeouts for the first time (this call sits synchronously in the
  review-creation request path — an unbounded hang here currently blocks a user-facing
  request indefinitely).
- `order-service`: replace `StandaloneConfig`'s bare `RestClient.builder()...build()`
  with a `ResilienceClientFactory`-built client (lower urgency — `standalone` profile
  only, not the default deployment topology, but still a real gap if that profile is
  ever used in anger).
- `product-service`: migrate `AccountServiceSellerProvisioner`'s hand-rolled
  `JdkClientHttpRequestFactory` + explicit timeout wiring to
  `ResilienceClientFactory`, preserving its existing configurable
  `iam.downstream.connect-timeout-ms`/`iam.downstream.read-timeout-ms` property keys
  if `ResilienceClientFactory`'s API allows external timeout values to be supplied
  (verify at implementation time; do not silently hardcode away an existing
  operator-configurable knob).
- `batch-worker`: migrate `RestClients.timed(...)` call sites
  (`ProductServiceClient`, `SearchServiceClient`, and any other consumer of this
  helper) to `ResilienceClientFactory`; remove `RestClients.java` once its last
  consumer is migrated (do not leave a dead, unused internal helper class behind).

## Out of Scope

- `shipping-service` — already adopted, no change needed (reference only).
- `gateway-service` — reactive edge router; grep found no direct `RestTemplate`/
  `WebClient`/`RestClient` bean matching this pattern in its codebase (it routes via
  Spring Cloud Gateway's own mechanism, a different layer entirely).
- `payment-service`, `promotion-service`, `settlement-service`, `notification-service`,
  `user-service`, `web-store` — grepped, no outbound `RestTemplate`/`WebClient`/
  `RestClient`/raw `HttpClient` bootstrap found in `src/main`. If any of these
  services is later found to make an outbound HTTP call some other way (e.g. a Feign
  client, a payment-gateway SDK with its own client), that is a new finding requiring
  its own follow-up, not silently folded into this task.
- `product-service`'s and `batch-worker`'s `IamClientCredentialsTokenProvider`
  internal HTTP calls — covered by `TASK-BE-568` (D6), not this task, even though
  both touch timeout configuration; keep the two tasks' diffs separable.
- Any change to `libs/java-common.ResilienceClientFactory` itself.

---

# Acceptance Criteria

- [x] `search-service`'s `ProductCatalogHttpAdapter` outbound calls now have explicit,
      non-zero connect and read timeouts via `ResilienceClientFactory`. — Confirmed
      **zero-timeout** case per the audit (`RestClientConfig`'s bare
      `RestClient.builder()`, no request factory). `RestClientConfig.java` deleted (its
      sole consumer now builds directly); `ProductCatalogHttpAdapter` now built via
      `ResilienceClientFactory.buildRestClient(productServiceUrl, connectTimeoutMs,
      readTimeoutMs)` with `catalog.connect-timeout-ms:3000`/`catalog.read-timeout-ms:5000`
      defaults. New `ProductCatalogHttpAdapterTest` proves a hung product-service now
      fails within ~300ms instead of hanging forever (real `MockWebServer`, no response
      enqueued).
- [x] `review-service`'s `OrderServiceClient` (purchase-verification call) now has
      explicit, non-zero connect and read timeouts via `ResilienceClientFactory`. —
      Confirmed **zero-timeout** case (bare `RestClient.builder().baseUrl(baseUrl)
      .build()`). Now built via `ResilienceClientFactory.buildRestClient(...)` with new
      `order-service.connect-timeout-ms:3000`/`order-service.read-timeout-ms:5000` keys
      added to `application.yml` (env override
      `ORDER_SERVICE_CONNECT_TIMEOUT_MS`/`ORDER_SERVICE_READ_TIMEOUT_MS`). New
      `OrderServiceClientTimeoutTest` proves the purchase-verification call — which sits
      synchronously in the review-creation request path — now fails within ~300ms against
      a hung order-service instead of blocking the request indefinitely (the primary
      live-defect closure this task exists for).
- [x] `order-service`'s `StandaloneConfig` outbound client now has explicit timeouts
      via `ResilienceClientFactory`. — Was zero-timeout, `@Profile("standalone")`-gated
      (not the default deployment topology). Now built via
      `ResilienceClientFactory.buildRestClient(...)` with new
      `services.payment-service.connect-timeout-ms:3000`/`read-timeout-ms:5000`
      `@Value` defaults. New `StandaloneConfigTest` calls the `@Bean` method directly and
      proves a hung payment-service fails within ~300ms.
- [x] `product-service`'s `AccountServiceSellerProvisioner` uses
      `ResilienceClientFactory` instead of its own `JdkClientHttpRequestFactory`
      wiring, preserving existing configurable timeout property keys if the factory's
      API supports externally-supplied values. — `ResilienceClientFactory
      .buildRestClient(String, int, int)` accepts millisecond ints directly, so the
      existing `iam.downstream.connect-timeout-ms:3000`/`iam.downstream
      .read-timeout-ms:10000` `@Value` keys and their default values are preserved
      byte-for-byte (constructor signature unchanged — existing
      `AccountServiceSellerProvisionerTest` compiles/passes unmodified). New
      `AccountServiceSellerProvisionerTimeoutTest` proves the fail-soft provisioning
      call still returns `failed()` within ~300ms against a WireMock 5s-delayed stub
      (no regression from the mechanism swap).
- [~] `batch-worker`'s `RestClients.java` helper is removed once all its consumers
      (`ProductServiceClient`, `SearchServiceClient`, others found at implementation
      time) are migrated to `ResilienceClientFactory`. — **Partially done, by design.**
      `ProductServiceClient`, `SearchServiceClient`, and batch-worker's own (internal)
      `OrderServiceClient` are migrated to `ResilienceClientFactory` (same 5s/10s
      values preserved). Grepping the whole module found a 4th call site —
      `IamClientCredentialsTokenProvider` — which is explicitly Out of Scope for this
      task (covered by `TASK-BE-568`/D6, "keep the two tasks' diffs separable"); it was
      NOT touched. Because that class remains a live consumer of `RestClients.timed(...)`,
      `RestClients.java` was **not deleted** — deleting it would break the build
      (Failure Scenarios explicitly warns against this: "migrate every call site first,
      verify zero remaining references, then delete"). `RestClients.java`'s javadoc was
      updated to document this partial-migration state and point at the remaining
      consumer, so a future `TASK-BE-568`-style follow-up for `batch-worker` knows to
      delete it once that migration lands.
- [x] Every migrated client's timeout is verified by a unit test (either asserting
      the configured request factory's timeout values, or — for a stronger guard — a
      test simulating a hung endpoint and asserting the call fails within the
      configured bound rather than hanging). — Used the stronger guard everywhere: real
      `MockWebServer`/`WireMock` hung-endpoint tests for all 5 migrated clients
      (`ProductCatalogHttpAdapterTest`, `OrderServiceClientTimeoutTest` [review],
      `StandaloneConfigTest`, `AccountServiceSellerProvisionerTimeoutTest`,
      `ProductServiceClientTest`/`SearchServiceClientTest`/`OrderServiceClientTest`
      [batch-worker]), each asserting the call fails within a generous (10x configured
      timeout) bound rather than hanging.
- [x] No existing outbound call's base URL, headers, or business logic changes as a
      side effect of the client-bootstrap swap — this task is timeout/mechanism-only. —
      Verified: all pre-existing tests (`OrderServiceClientUnitTest` [review, updated
      only for the new constructor timeout params],
      `AccountServiceSellerProvisionerTest`, and the batch-worker
      `SearchIndexConsistencyIntegrationTest`/`StalePaidOrderConfirmationIntegrationTest`
      — both `@Tag("integration")`, compile-verified) pass/compile unchanged; no URI,
      header, or request-body construction code was touched in any migrated class.
- [x] `./gradlew :projects:ecommerce-microservices-platform:apps:<service>:test`
      GREEN for all 5 touched services. — All 5 run locally, `BUILD SUCCESSFUL`, 0
      failures (see Definition of Done for per-service confirmation).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and
> `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a
> Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2
  D7, § 6 item 2
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `rules/traits/integration-heavy.md` (circuit breaker / retry / timeout expectations
  for external integrations — this project declares `integration-heavy`, see
  `PROJECT.md`)

---

# Related Contracts

- None — internal outbound-client bootstrap change, no published API/event contract
  affected. If `ResilienceClientFactory` adds retry/circuit-breaker behavior beyond
  plain timeouts, verify it does not change observable latency/retry behavior in a
  way that a downstream contract (e.g. `review-api.md`'s purchase-verification
  latency expectations, if documented) would need to reflect.

---

# Target Service

- `search-service`, `review-service`, `order-service`, `product-service`,
  `batch-worker`

---

# Architecture

Follow, per touched service:

- `specs/services/search-service/architecture.md`
- `specs/services/review-service/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/services/batch-worker/architecture.md`

---

# Implementation Notes

- `shipping-service`'s `HttpCarrierTrackingAdapter`/`DeliveryTrackerCarrierTrackingAdapter`/`DeliveryTrackerTokenProvider`
  (`apps/shipping-service/src/main/java/com/example/shipping/infrastructure/carrier/`)
  are this project's own live `ResilienceClientFactory` adopters — read these as the
  concrete in-repo reference before implementing the 5 migrations, rather than
  designing the wiring from `libs/java-common`'s source alone.
- Prioritize `search-service` and `review-service` first — these are the two
  confirmed **zero-timeout** cases matching the ADR's specific "live
  production-risk gap" framing; `order-service` (standalone-profile-only),
  `product-service`, and `batch-worker` (both already have *some* non-zero timeout,
  just a duplicated mechanism) are lower urgency and can follow in the same PR or a
  fast-follow, at the implementer's discretion.
- `review-service`'s `OrderServiceClient` implements `PurchaseVerificationPort` — its
  current `catch (Exception e) { throw new RuntimeException(...) }` swallow-and-rewrap
  behavior on failure is unrelated to this task's scope (timeout wiring only); do not
  change its error-handling semantics unless the `ResilienceClientFactory`-built
  client changes what exception types are thrown on timeout in a way that breaks this
  catch clause — verify and adjust the catch type only if required, not opportunistically.

---

# Edge Cases

- `order-service`'s `StandaloneConfig` is `@Profile("standalone")`-gated — confirm at
  implementation time whether this profile is actually exercised in any CI lane or
  deployment (if it is genuinely dead/unused, this specific fix carries no live risk,
  but should still be done for consistency and because a future re-activation of that
  profile would otherwise reintroduce the gap silently).
- `product-service`'s existing `iam.downstream.connect-timeout-ms`/`read-timeout-ms`
  properties are operator-facing config today — if `ResilienceClientFactory` only
  accepts a fixed/hardcoded timeout rather than an injectable one, this is a real
  design conflict (losing operator configurability) that must be resolved explicitly,
  not silently dropped. Check `ResilienceClientFactory`'s actual API before assuming
  it is a drop-in replacement.
- `batch-worker`'s `RestClients.timed()` helper may have call sites beyond
  `ProductServiceClient`/`SearchServiceClient` not yet enumerated — grep the whole
  `batch-worker` module for `RestClients.timed` before deleting the helper, to avoid
  leaving a dangling consumer.

---

# Failure Scenarios

- Migrating `review-service`'s `OrderServiceClient` without adding a timeout (or
  adding one but leaving it effectively unbounded via a misconfigured default) would
  leave the exact live risk this task exists to close — verify the actual configured
  duration, not just that a request factory object now exists.
- Deleting `batch-worker`'s `RestClients.java` before all consumers are migrated
  would break the build — migrate every call site first, verify zero remaining
  references, then delete.
- Silently dropping `product-service`'s operator-configurable timeout properties in
  favor of a hardcoded `ResilienceClientFactory` default would be a regression in
  operational flexibility, even though the timeout itself would still be non-zero.

---

# Test Requirements

- Unit test per migrated client asserting a configured, non-zero connect/read
  timeout is present on the built `RestClient` (or an equivalent verification the
  `ResilienceClientFactory`'s own test suite pattern uses —
  `libs/java-common/src/test/java/com/example/common/resilience/ResilienceClientFactoryTest.java`
  is the reference for how the library itself expects to be tested/verified).
- No behavior-change tests needed for base URL, headers, or business logic (unchanged
  by this task).

---

# Definition of Done

- [x] All 5 services' outbound clients migrated to `ResilienceClientFactory`
      (`search-service.ProductCatalogHttpAdapter`, `review-service.OrderServiceClient`,
      `order-service.StandaloneConfig`, `product-service.AccountServiceSellerProvisioner`,
      `batch-worker.ProductServiceClient`/`SearchServiceClient`/`OrderServiceClient`)
- [x] Zero-timeout gap closed for `search-service` and `review-service` specifically
      (the confirmed live-risk cases) — both now have explicit non-zero connect/read
      timeouts, proven by hung-endpoint unit tests failing fast instead of hanging.
- [~] `batch-worker`'s `RestClients.java` removed once dead — NOT removed; see AC
      note above. It still has one live consumer (`IamClientCredentialsTokenProvider`,
      explicitly Out of Scope / `TASK-BE-568` territory), so deleting it now would break
      the build. Its 3 in-scope consumers were migrated off it.
- [x] Operator-configurable timeout properties preserved where they existed
      (`product-service`) — `iam.downstream.connect-timeout-ms`/`read-timeout-ms`
      keys and default values (3000/10000) unchanged.
- [x] Tests passing for all 5 services — ran locally on this Windows host (unit tests
      only; Testcontainers `@Tag("integration")` suites are compile-verified but
      excluded from the default `test` task per each service's existing
      Windows/Docker convention, unaffected either way since this task did not touch
      Testcontainers-backed integration tests):
      - `./gradlew :projects:ecommerce-microservices-platform:apps:search-service:test` → BUILD SUCCESSFUL
      - `./gradlew :projects:ecommerce-microservices-platform:apps:review-service:test` → BUILD SUCCESSFUL
      - `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:test` → BUILD SUCCESSFUL
      - `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:test` → BUILD SUCCESSFUL
      - `./gradlew :projects:ecommerce-microservices-platform:apps:batch-worker:test` → BUILD SUCCESSFUL
- [x] Ready for review
