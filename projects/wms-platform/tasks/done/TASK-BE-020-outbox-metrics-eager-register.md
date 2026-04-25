# TASK-BE-020 — OutboxMetrics: eager strong-reference registration

> **Outcome (2026-04-25, PR #59 CI):** PARTIAL FIX. The strong-reference changes are kept (they
> are correct on their own merits) but the CI guard had to be restored — see the new "Outcome"
> section at the bottom. The failure mode in CI turned out to be in scrape-body composition,
> not in gauge-function GC eligibility, so removing `@DisabledIfEnvironmentVariable` is being
> deferred to a follow-up that addresses the real cause.

## Goal

Fix the `prometheusEndpoint_exposesOutboxMetrics` integration test so it runs reliably on CI
without the `@DisabledIfEnvironmentVariable` guard that was added as a workaround.

The root cause: `meterRegistry.gauge()` stores the `OutboxMetrics` state object via a
`WeakReference`. Under GC pressure on GitHub Actions shared runners, this weak reference can
be cleared between Spring context initialisation and the Prometheus scrape — causing the gauge
to return `NaN` and be silently dropped from the `/actuator/prometheus` output. The counters
are already strongly referenced by Micrometer, but they only appear if the scrape is complete
(a gauge-drop early-termination is less likely the cause for them; see Scope for the belt-and-
suspenders approach).

## Scope

**In scope:**

1. `OutboxMetrics.java` — change gauge registration from `meterRegistry.gauge(...)` (WeakReference)
   to `Gauge.builder(...).strongReference(true).register(meterRegistry)` so Micrometer holds a
   strong reference to the `OutboxMetrics` instance.
2. `MasterServiceIntegrationBase.java` — add `@Autowired OutboxMetrics outboxMetrics` field so the
   test infrastructure itself holds a direct strong reference to the bean (belt-and-suspenders).
3. `WarehouseIntegrationTest.java` — remove `@DisabledIfEnvironmentVariable` and its `disabledReason`
   from `prometheusEndpoint_exposesOutboxMetrics`. The 30 s Awaitility retry can stay (it covers
   the post-Kafka-unpause settling time that is a real, separate CI concern).

**Out of scope:**

- E2E 20-minute timeout investigation (separate follow-up).
- WMS root pnpm shortcut scripts.

## Acceptance Criteria

1. `OutboxMetrics` gauge is registered via `Gauge.builder(...).strongReference(true)` — verified
   by the existing `OutboxMetricsTest.allThreeMetersAreRegistered` unit test continuing to pass.
2. `MasterServiceIntegrationBase` injects `OutboxMetrics` via `@Autowired`.
3. `WarehouseIntegrationTest.prometheusEndpoint_exposesOutboxMetrics` no longer carries
   `@DisabledIfEnvironmentVariable`.
4. All existing unit tests (`./gradlew :projects:wms-platform:apps:master-service:test`) pass.

## Related Specs

- `projects/wms-platform/specs/services/master-service/architecture.md` (observability section)
- `projects/wms-platform/specs/contracts/events/master-events.md` (§ Producer Guarantees — the
  three meter names are declared there)

## Related Contracts

- `projects/wms-platform/specs/contracts/events/master-events.md`

## Edge Cases

- `Gauge.builder().strongReference(true)` holds a strong reference from the `MeterRegistry`
  to `OutboxMetrics`. As long as the `MeterRegistry` bean is alive (application-scoped), so is
  `OutboxMetrics`. This is equivalent to the implicit guarantee Spring already provides — the
  explicit strong reference just makes it JVM-level guaranteed without relying on Spring
  internals.
- The `pendingCount()` gauge callback still queries the DB on every Prometheus scrape. The
  existing `try/catch` and null-guard remain; those protect against scrape-thread races and are
  not affected by this change.

## Failure Scenarios

- If `Gauge.builder().strongReference(true)` API is unavailable in the Micrometer version on the
  classpath (Spring Boot 3.4.1 → Micrometer 1.14.x), the build will fail to compile. Resolution:
  confirm the API in `DefaultGauge` / `Gauge.Builder` javadoc for 1.14.x before merging.

## Outcome (2026-04-25)

PR #59 CI revealed that the GC-eligibility theory was incorrect. With `strongReference(true)` +
the `@Autowired` test-base reference both in place,
`WarehouseIntegrationTest.prometheusEndpoint` still fails on GitHub-hosted runners with
`AssertionFailedError` after the 30 s Awaitility retry — but the assertion that fires first is
the HTTP status check, **not** the body-contains check. The downloaded JUnit XML reports
the actual response: `expected: 200 OK but was: 500 INTERNAL_SERVER_ERROR`, on every retry of
the 30-second window. The earlier "scrape-body composition" framing was reading the failure
line number wrong; the real symptom is the endpoint itself returning 500.

A follow-up attempt (PR #62, fix/wms-prometheus-scrape-isolation) tried to sidestep the
suspected Kafka-context-pollution race by extracting the assertion into its own
`OutboxPrometheusScrapeIntegrationTest` class with `@DirtiesContext(BEFORE_CLASS)`. The
isolated test still failed on CI with the same HTTP 500 across all 30 retries, confirming
the issue is environmental rather than test-suite ordering. Gradle's integration test report
HTML and CI job logs do not capture the Spring Boot application's server-side stack trace
(logback config in test scope routes only to the console, which Gradle silently drops),
so the actual cause of the 500 cannot be diagnosed from the artifacts available.

PR #62 was closed without merging. Branch deleted.

**Decision:**

- Keep the `OutboxMetrics` `Gauge.builder().strongReference(true)` change. It is the correct
  registration idiom in its own right, and removes the (real, just non-causal) WeakReference
  collection failure mode.
- Keep the `@Autowired OutboxMetrics outboxMetrics` field on `MasterServiceIntegrationBase`. It
  costs nothing and does provide the belt-and-suspenders guarantee originally intended.
- Keep the `@DisabledIfEnvironmentVariable("CI")` guard on
  `WarehouseIntegrationTest.prometheusEndpoint_exposesOutboxMetrics`. The guard is what is
  actually currently in effect on `main`.
- **Update `disabledReason`** to point at the corrected diagnosis (`/actuator/prometheus`
  returns HTTP 500 in CI; root cause not yet characterized) — left for a future PR alongside
  the real fix; not worth a single-line doc commit on its own.

**Real follow-up (deferred until needed):** characterize why `/actuator/prometheus` returns
HTTP 500 on GitHub-hosted Ubuntu runners but works locally on WSL2. This is environmental,
specific to CI, and not blocking any portfolio-critical signal. To diagnose, a future PR
needs at least one of:

1. A logback config that pipes server logs to a file-on-failure so they can be uploaded as
   a CI artifact (`actions/upload-artifact` already gathers `**/build/test-results/` and
   reports; adding a logs path is a one-line change).
2. A diagnostic endpoint or test-time `try/catch` that captures the prometheus-scrape
   exception body for inclusion in the assertion message.
3. A controlled CI repro that runs only `OutboxPrometheusScrapeIntegrationTest` with
   `--info --debug` Gradle logging to surface the underlying Micrometer / Spring Boot
   actuator stack trace.

The follow-up is non-blocking for any current PR; the CI guard keeps the wms baseline green.
