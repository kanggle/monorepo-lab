# TASK-PC-BE-015 — console-bff per-leg circuit-breaker + bounded retry (close the spec-vs-reality resilience gap)

**Status:** done

**Type:** TASK-PC-BE
**Owner:** backend
**Analysis model:** Opus 5 / **Recommended impl model:** Opus (resilience wiring + failure-injection tests, cross-cutting through the fan-out engine)

**Task Tags:** code, test

> Filed from a monorepo-wide spec-vs-reality audit. `console-bff` is the most fan-out-heavy service in the
> repo, and both its own `architecture.md` and its own in-code javadoc assert a circuit-breaker that does
> not exist. This ticket makes the assertion true rather than deleting it.

---

# Goal

`console-bff` declares `implementation project(':libs:java-common')` and **imports nothing from it**.
`RestClientConfig` hand-rolls a `SimpleClientHttpRequestFactory` connect/read timeout pair and nothing else —
there is **no circuit breaker and no retry anywhere in `src/main`** (`git grep -l resilience4j
projects/platform-console` = 0 hits). Meanwhile three documents claim otherwise:

| Claim site | Text | Reality |
|---|---|---|
| `specs/services/console-bff/architecture.md` § Resilience (D5.A) | "per outbound domain (circuit-breaker + retry + timeout) via `libs/java-web` Resilience4j primitives" / "**Per-leg circuit-breaker keyed by `(domain, route)`** — a wms outage does not open the breaker for scm" | only the timeout exists |
| `specs/contracts/console-integration-contract.md` §§ 2.4.9 / 2.4.9.1 / 2.4.9.2 | `bff_fanout_errors_total` `code ∈ {…, circuit_open, …}`; `reason ∈ { DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN }` | `circuit_open` / `CIRCUIT_OPEN` has **no emitter anywhere in the codebase** |
| `infrastructure/config/RestClientConfig.java` javadoc | "The per-leg circuit-breaker / retry primitives from `libs/java-web` (Resilience4j) are applied at the call site (see the composition use case)" | the composition use case applies neither |

The `libs/java-web` citation is additionally **wrong on its own terms** — that module contains four files
(`ErrorResponse`, `AccessDeniedException`, `BearerJwtOpenApi`, `RequiredScopeValidator`) and zero
resilience code. The Resilience4j wiring lives in **`libs/java-common`**
(`com.example.common.resilience.ResilienceClientFactory`), the module console-bff already depends on.

The `CIRCUIT_OPEN` classification is a **documented-but-dead outcome with a live consumer**: `console-web`'s
zod schemas (`features/operator-overview/api/operator-overview-types.ts` `DEGRADED_REASONS`,
`features/domain-health/api/types.ts` `DEGRADED_REASONS`) already accept `'CIRCUIT_OPEN'`, and
`DomainCardStates.tsx` / `DomainHealthCard.tsx` already render a Korean message for it
(`서비스가 일시적으로 응답할 수 없습니다.`). The FE has been ready for a value the BE never produces.

**After this task**: every composition fan-out leg is executed behind a Resilience4j circuit breaker + bounded
retry keyed by `(domain, route)` built from `ResilienceClientFactory`; an open breaker fails the leg fast with
`degraded / CIRCUIT_OPEN` and increments `bff_fanout_errors{code="circuit_open"}`; and the three claim sites
above describe what the code does.

---

# Scope

## In Scope

`projects/platform-console/apps/console-bff/` only.

1. **`build.gradle`** — declare `io.github.resilience4j:resilience4j-circuitbreaker` +
   `resilience4j-retry` (2.2.0), mirroring `iam-platform/apps/account-service/build.gradle`.
   `libs/java-common` declares them `implementation`, so they are **not** on console-bff's compile classpath
   transitively; the existing `implementation project(':libs:java-common')` line stays untouched.
2. **New outbound port** `application/port/outbound/LegResiliencePort` —
   `<T> T execute(DomainTarget domain, String route, Supplier<T> call)`. The application layer states the
   *policy* ("this leg is gated"); it does not import Resilience4j.
3. **New domain exception** `domain/composition/CircuitOpenException` — framework-free, so the application-layer
   leg classifiers can branch on "breaker open" without importing `io.github.resilience4j`.
4. **New adapter** `adapter/outbound/resilience/Resilience4jLegResilienceAdapter` — one
   `(CircuitBreaker, Retry)` gate per `(domain, route)` key, lazily created in a `ConcurrentHashMap`, both built
   via `ResilienceClientFactory` (**adopted as-is** — customizer overloads only, no fork). Translates
   `CallNotPermittedException` → `CircuitOpenException`.
5. **New config** `infrastructure/config/ResilienceProperties` (`consolebff.resilience.*`) + `application.yml`
   defaults; registered on `@EnableConfigurationProperties`.
6. **`application/composition/CompositionEngine`** — takes the port; `time(...)` runs the leg body through
   `resilience.execute(domain, routeLabel, call)` so the breaker key is exactly the `(domain, route)` pair the
   spec names, and `CircuitOpenException` reaches the existing per-use-case `LegErrorClassifier` chain.
7. **`domain/composition/LegOutcome`** — `circuitOpen(domain)` factory + `REASON_CIRCUIT_OPEN` constant +
   `isCircuitOpen()`. **`Status` stays a 3-value enum** (`OK/DEGRADED/FORBIDDEN`) — see AC-5.
8. **The 3 fan-out use-cases** (`OperatorOverviewCompositionUseCase`, `DomainHealthCompositionUseCase`,
   `NotificationAggregationUseCase`) — inject the port, and add a first `CircuitOpenException` arm to each
   `classifyError` emitting `circuit_open` + `LegOutcome.circuitOpen(domain)`.
9. **Doc correction** — `RestClientConfig` javadoc, `architecture.md` § Resilience + § Tech Stack, and the two
   `console-integration-contract.md` resilience sub-sections that cite `libs/java-web`: corrected to
   `libs/java-common` and to where the primitives are actually applied. Non-decision-changing clarification
   recorded in `architecture.md`'s `> Provenance` block per its § Change Rule.
10. **Tests** — see § Test Requirements.

## Out of Scope

- **`libs/java-common/ResilienceClientFactory` is not modified.** It is adopted through its documented
  customizer overloads (`buildCircuitBreaker(name, cfg -> …)` / `buildRetry(name, cfg -> …)`). If it turns out
  it cannot serve the per-`(domain,route)` requirement → STOP and report, do not fork.
- **No contract change.** `console-integration-contract.md` already lists `circuit_open` / `CIRCUIT_OPEN` in
  both the metric `code` enum and the card `reason` union — this task supplies the missing *emitter*, it does
  not widen the contract. The `libs/java-web` → `libs/java-common` edits in that file are citation fixes only.
- **`ErpNotificationsReadAdapter.markRead` (`POST /api/erp/notifications/{id}/read`) is deliberately NOT gated.**
  It is a mutating proxy invoked outside the fan-out engine, so bounded retry would be an unsafe re-POST and
  there is no composition leg to degrade. It stays timeout-bounded only. Stated here so the next reader does not
  have to rediscover it — this ticket exists *because* a doc over-claimed coverage.
- No new metric family beyond an additive circuit-state gauge (AC-7); the 3 mandatory families keep their exact
  names and tags.
- `console-web` changes — none needed (the FE already parses and renders `CIRCUIT_OPEN`).
- Per-vendor thread-pool bulkheads (trait I9) — the fan-out already gives each leg its own virtual thread, so
  legs cannot exhaust a shared pool. Not re-litigated here.

---

# Acceptance Criteria

- [ ] **AC-0 (gate — re-measure, code wins)** — Before implementing, re-confirm on current `main`:
      (a) `git grep -l "resilience4j\|ResilienceClientFactory" projects/platform-console/apps/console-bff/src/main`
      returns **0**; (b) `git grep -rn "circuit_open\|CIRCUIT_OPEN" .../console-bff/src/main` returns only the
      `LegOutcome` javadoc mention, no emitter; (c) `libs/java-web` still has no resilience class. Any
      disagreement → adjust the ticket, do not implement against a stale premise.
- [ ] **AC-1 (leg population — count it, do not inherit it)** — Enumerate the outbound legs from the code and
      record the number in the PR body. The audit note said "12"; the tree has **13** outbound HTTP adapter
      classes = **13 distinct `(domain, route)` keys** (6 × `operator-overview` + 6 × `domain-health` +
      1 × `notification-aggregator`) plus one non-fan-out mark-read surface on the 13th adapter. All 13 keys
      are gated.
- [ ] **AC-2 (breaker opens)** — A failure-injection test drives N consecutive **5xx** responses at one
      `(domain, route)` key and asserts the breaker transitions to `OPEN`.
- [ ] **AC-3 (fail-fast, no network)** — Once OPEN, the next call for that key throws `CircuitOpenException`
      **without invoking the leg body** (assert an invocation counter is unchanged / the WireMock-equivalent stub
      records no further request), and the card renders `status=degraded, reason=CIRCUIT_OPEN`.
- [ ] **AC-4 (isolation, both axes)** — With one key OPEN: (a) a **different domain** on the same route stays
      CLOSED and returns `ok`; (b) the **same domain on a different route** (`(wms,"domain-health")` vs
      `(wms,"operator-overview")`) stays CLOSED — this is the sibling-instance independence
      `console-integration-contract.md` § 2.4.9.2 Resilience states verbatim.
- [ ] **AC-5 (wire shape unchanged)** — `LegOutcome.Status` remains `{OK, DEGRADED, FORBIDDEN}`; `CIRCUIT_OPEN`
      is a `reason` on a `degraded` card, exactly the shape `console-web`'s `DEGRADED_REASONS` zod enum already
      accepts. No FE change is required, and the existing envelope assertions in the console-bff IT suite stay
      GREEN.
- [ ] **AC-6 (`circuit_open` is live)** — `bff_fanout_errors{domain,route,code="circuit_open"}` is incremented
      on a fail-fast leg and is visible on `/actuator/prometheus`. `git grep "circuit_open"` in `src/main` now
      returns an emitter, not only a comment.
- [ ] **AC-7 (4xx does not open the breaker)** — A burst of `403`/`404` from a leg leaves the breaker CLOSED and
      is not retried (`ResilienceClientFactory.standard*Config()` already `ignoreExceptions(HttpClientErrorException)`;
      this AC pins that console-bff inherits it rather than overriding it). Guards the cross-leg-401 collapse and
      the per-leg-403 `forbidden` classification against becoming breaker fuel.
- [ ] **AC-8 (latency budget arithmetic)** — Retry is bounded so `attempts × per-leg-timeout + backoff` stays
      under `CompositionEngine.COMPOSITION_TIMEOUT` (5s). With per-leg 2s: max 2 attempts and ≤ 450ms backoff
      ⇒ worst case ≈ 4.45s < 5s. The arithmetic is asserted by a test on the resolved config, not only commented.
- [ ] **AC-9 (docs now true)** — `architecture.md`, `console-integration-contract.md` and the `RestClientConfig`
      javadoc name `libs/java-common` and describe where the primitives are actually applied. A reader
      following any of the three lands on real code.
- [ ] **AC-10** — `./gradlew :projects:platform-console:apps:console-bff:test` and `:integrationTest` GREEN.

---

# Related Specs

> **Before reading Related Specs**: follow `platform/entrypoint.md` Step 0 — `projects/platform-console/PROJECT.md`
> (`domain: saas`; `traits: [multi-tenant, integration-heavy, audit-heavy]`), then `rules/common.md` and
> `rules/traits/integration-heavy.md`.

- `projects/platform-console/PROJECT.md` — § Trait Rationale, **integration-heavy**: "circuit breaker · retry ·
  timeout 을 `platform/` 베이스라인대로 적용 — 한 도메인 장애가 콘솔 전체 장애가 되지 않아야 한다".
- `rules/traits/integration-heavy.md` — **I1** (explicit timeouts), **I2** (circuit breaker, fast-fail),
  **I3** (exponential backoff + jitter, never retry 4xx), **I7** (adapter isolation), **I10** (fake-server
  failure-mode tests). Forbidden pattern: "Circuit breaker 없이 직접 외부 호출".
- `projects/platform-console/specs/services/console-bff/architecture.md` — § Resilience (D5.A), § Tech Stack,
  § Observability (D7.A), § Test Pyramid, § Change Rule.
- `platform/service-types/rest-api.md` (console-bff's declared Service Type), `platform/testing-strategy.md`,
  `platform/observability.md`.
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — D5.A (per-domain CB + partial degrade);
  D5.B (all-or-nothing 503) is the rejection this must not regress into.

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/testing/` (`testing-backend`)

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md`
  - § 2.4.9 Resilience (D5.A) + Observability — `code ∈ {5xx, timeout, circuit_open, tenant_forbidden, permission_denied}`
  - § 2.4.9.1 response schema — `degraded → reason ∈ { DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN }`
  - § 2.4.9.2 Resilience — "Per-leg circuit-breaker keyed by `(domain, route="domain-health")` … sibling circuit
    instance to § 2.4.9.1's `(domain, "operator-overview")` (independent state, so one dashboard's circuit trip
    does not bleed into the other)"
  - § 2.5 Resilience (project-wide baseline)
- **No contract edit** other than the `libs/java-web` → `libs/java-common` citation fix.

---

# Target Service

- `console-bff` (Service Type: `rest-api`; Architecture Style: Hexagonal)

---

# Architecture

Follow `projects/platform-console/specs/services/console-bff/architecture.md`.

Layer placement (hexagonal — the reason the gate is a port, not a static call):

```
application/composition/CompositionEngine   → calls LegResiliencePort   (policy, no Resilience4j import)
application/port/outbound/LegResiliencePort → the seam
adapter/outbound/resilience/Resilience4jLegResilienceAdapter → Resilience4j + ResilienceClientFactory
domain/composition/CircuitOpenException     → framework-free signal the classifiers branch on
```

Adoption reference (one of the 18 existing `ResilienceClientFactory` call sites; the one that uses **all three**
primitives rather than only `buildRestClient`):
`projects/iam-platform/apps/account-service/src/main/java/com/example/account/infrastructure/client/AuthServiceClient.java`
— `buildCircuitBreaker(name)` + `buildRetry(name)` + `CircuitBreaker.decorateRunnable(cb, Retry.decorateRunnable(retry, op))`,
i.e. **CB outermost, retry inner** (one breaker call records one logical operation, not one per attempt).
`scm-platform/.../InventoryVisibilityRestAdapter.java` is the timeouts-only shape and is *not* the model here.

Isolation-test reference: `libs/java-common/src/test/java/com/example/common/resilience/ResilienceClientFactoryTest.java`
(`driveFailures(...)` → assert `State.OPEN` → assert next call throws `CallNotPermittedException`).

---

# Implementation Notes

- **Why the engine and not the RestClient.** A `RestClient` bean is per-**domain**; the spec keys the breaker by
  `(domain, route)`. Only `CompositionEngine` holds both (`domain` argument + `routeLabel` field), so gating in
  `time(...)` is the single wiring point that produces exactly the specified key and automatically covers any
  future leg. A `ClientHttpRequestInterceptor` cannot see the route.
- **COUNT_BASED window, deliberately.** `ResilienceClientFactory.standardCircuitBreakerConfig()` defaults to a
  `TIME_BASED` 10s window with `minimumNumberOfCalls=5`. A BFF leg fires **once per operator dashboard load**, so
  a time-based window would need 5 loads inside 10 seconds and would essentially never trip. Override to
  `COUNT_BASED` (window 10 calls, min 5) via the factory's customizer overload — the failure-rate threshold, the
  4xx-ignore and the retry policy are all inherited unchanged.
- **CB outermost, retry inner** (`AuthServiceClient` order) so one dashboard request = one recorded breaker call.
- **Retry budget.** `standardRetryConfig()` is 3 attempts / 500ms exponential-random base — worst case
  `2s + ~0.75s + 2s + ~1.5s + 2s ≈ 8.2s`, which **overruns** the 5s composition deadline and would convert
  per-leg degrades into whole-composition timeouts. Override to 2 attempts / 150ms base (⇒ ≤ ~450ms with the
  ±50% jitter) — AC-8.
- **Existing IT fixtures must be re-armed for the retry.** `MockWebServer`'s default `QueueDispatcher` *blocks*
  when its queue is empty, so a leg stubbed with a single `503` will, on its retry attempt, hang until the 2s read
  timeout and reclassify `DOWNSTREAM_ERROR → TIMEOUT`. Failure scenarios must use a repeating dispatcher
  (`respondAlways(...)`) rather than a one-shot `enqueue`, so the assertion is retry-count-independent.
- **Breaker state is process-global and outlives a test method.** The adapter must expose a `reset()` used by the
  IT `@BeforeEach`, otherwise failures accumulated by one test open a breaker inside an unrelated later test
  (JUnit method order is not part of the contract).
- **Do not add a 4th latency metric family.** `CompositionEngine`'s javadoc pins `bff_fanout_latency` as the sole
  latency metric; the circuit-state gauge (AC-7 companion) is a *state* gauge, not a timing metric.
- A leg body that returns a failure `CompositionLeg` **without throwing** (finance Option (b)
  `MISSING_PREREQUISITE` short-circuit) is a breaker **success** — no outbound call was made. Do not "fix" this.

---

# Edge Cases

- **E1 — 4xx must not be breaker fuel.** A tenant-scoped `403 TENANT_FORBIDDEN` or a cross-leg `401` is a
  contract/authorization outcome, not a downstream availability fault. Inherited
  `ignoreExceptions(HttpClientErrorException)` covers both; AC-7 pins it. Regression here would let a
  mis-scoped operator trip a real breaker for every other operator.
- **E2 — same domain, two routes.** `wms` appears on both `operator-overview` and `domain-health`. The keys must
  stay independent (contract § 2.4.9.2 says so verbatim); a domain-keyed-only breaker would let the health
  dashboard's trip blank the overview card.
- **E3 — HALF_OPEN probe returns.** After `waitDurationInOpenState`, the breaker admits
  `permittedNumberOfCallsInHalfOpenState` probes. Those probes *do* hit the network — a leg can therefore
  recover on its own without a redeploy, and a still-broken domain re-opens without a storm.
- **E4 — finance Option (b) short-circuit.** Returns `forbidden / MISSING_PREREQUISITE` without an HTTP call;
  counted as a breaker success (nothing failed downstream). See Implementation Notes.
- **E5 — composition-level timeout still wins.** A leg pending at the 5s deadline is resolved by
  `CompositionEngine.resolve(...)` as `timeout`, independent of breaker state. Retry must not push a leg past
  that deadline (AC-8) or every degrade would be misattributed as `TIMEOUT`.
- **E6 — mark-read is not gated.** `POST /api/erp/notifications/{id}/read` does not flow through the engine.
  Documented in § Out of Scope, in the adapter javadoc, and in the PR body — an ungated surface that a doc claims
  is gated is the exact defect this ticket closes.
- **E7 — breaker registry growth.** Keys are `(DomainTarget, routeLabel)`; `DomainTarget` is a closed 6-value
  enum and route labels are 3 compile-time constants ⇒ the map is bounded at 18 entries, no eviction needed.
- **E8 — `enabled=false` escape hatch.** `consolebff.resilience.enabled=false` degrades `execute(...)` to a plain
  `call.get()`. Present so an operator can disable the gate in an incident without a redeploy; **must default to
  `true`** and a test must pin the default (a disabled-by-default guard is the same defect in a new costume).

---

# Failure Scenarios

- **F1 — behaviour change during a real outage (accepted, and the point).** Before: a wms outage cost every
  dashboard load a full 2s per-leg timeout, forever. After: the first ~5 loads still pay it, then the breaker
  opens and the wms card degrades in microseconds for `waitDurationInOpenState` (10s) before a HALF_OPEN probe
  retries. This is an **improvement** but it is a behaviour change: (a) the operator sees `CIRCUIT_OPEN` instead
  of `DOWNSTREAM_ERROR`/`TIMEOUT` copy; (b) a *recovered* domain can still show a degraded card for up to the
  open-state wait; (c) the fail-fast path performs **no outbound request at all**, so producer-side access logs
  lose those probes. All three are per-card only — the composition still returns 200 with every responsive leg's
  data (D5.A), and D5.B (all-or-nothing 503) is not reintroduced.
- **F2 — retry overruns the composition deadline** → every degrade misreports as `TIMEOUT` and the dashboard
  latency doubles. Guarded by AC-8's arithmetic assertion, not by a comment.
- **F3 — retry storms a struggling producer.** Bounded to 1 extra attempt with jittered backoff (I3); 4xx never
  retried; the breaker then removes load entirely once the domain is confirmed down.
- **F4 — a shared breaker instance across domains** would make one domain's outage blank every card — the exact
  D5.B failure the ADR rejects. Guarded by AC-4's two-axis isolation test.
- **F5 — leaked test state.** A breaker left OPEN by an earlier test method silently degrades an unrelated later
  test into a fail-fast path, producing a confident wrong assertion. Guarded by the `reset()` + `@BeforeEach`
  discipline in Implementation Notes.
- **F6 — "the tests pass so it works" on an unreachable guard.** A breaker exercised only through a unit double
  proves the double. AC-2/3/4 must be exercised at least once through the **booted application** with real
  stub servers, so the assertion covers the wiring (`@Component` registered, port injected into all 3 use-cases)
  and not just the class.

---

# Test Requirements

- **Unit — `Resilience4jLegResilienceAdapterTest`** (mirrors `ResilienceClientFactoryTest`'s `driveFailures`
  shape): 5xx burst opens the gate (AC-2); the next call fails fast with `CircuitOpenException` and the supplier
  invocation counter does not move (AC-3); a different domain on the same route and the same domain on a
  different route both stay CLOSED and still execute (AC-4); 4xx burst leaves it CLOSED and unretried (AC-7);
  transient-then-success proves the retry actually retries; `enabled=false` is pass-through, and the default is
  `true` (E8); resolved config satisfies the budget arithmetic (AC-8).
- **Unit — `CompositionEngineTest`**: a gate that throws `CircuitOpenException` reaches the injected
  `LegErrorClassifier`; the existing all-success / partial-failure / timeout / context-propagation scenarios stay
  GREEN through a pass-through gate.
- **Unit — the 3 use-case tests**: `CircuitOpenException` from a leg maps to `degraded / CIRCUIT_OPEN` **and**
  increments `bff_fanout_errors{code="circuit_open"}` (AC-6), with the other legs unaffected.
- **Integration (`@Tag("integration")`, booted context + `MockWebServer` domain stubs)** — the AC-2/3/4 evidence
  that satisfies F6: repeated 5xx on one domain until the card flips to
  `"status":"degraded","reason":"CIRCUIT_OPEN"`, then a snapshot-and-diff on that stub's request count proving
  the fail-fast load performed **zero** outbound requests, while a sibling domain's card is still `"ok"` in the
  same envelope. Plus `/actuator/prometheus` carrying `code="circuit_open"`.
- Existing console-bff `test` + `integrationTest` suites stay GREEN (fixtures re-armed for the retry per
  Implementation Notes — a fixture edit is allowed, an assertion weakening is not).

---

# Definition of Done

- [ ] Implementation completed (13 `(domain, route)` keys gated)
- [ ] Tests added (unit + failure-injection integration)
- [ ] `./gradlew :projects:platform-console:apps:console-bff:test :projects:platform-console:apps:console-bff:integrationTest` GREEN
- [ ] Contracts: citation fix only, no surface change
- [ ] `architecture.md` + `RestClientConfig` javadoc corrected; `> Provenance` note added
- [ ] Ready for review
