# TASK-BE-020 — OutboxMetrics: eager strong-reference registration

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
