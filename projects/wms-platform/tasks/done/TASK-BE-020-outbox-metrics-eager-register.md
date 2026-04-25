# TASK-BE-020 — OutboxMetrics: eager strong-reference registration

> **Outcome (2026-04-25):** RESOLVED via two complementary changes.
> 1. PR #59 — `Gauge.builder().strongReference(true)` + `@Autowired OutboxMetrics` field on
>    `MasterServiceIntegrationBase` (kept; correct on their own merits, but not the actual fix).
> 2. Follow-up PR — extracted the prometheus scrape assertion into a new
>    `OutboxPrometheusScrapeIntegrationTest` class with `@DirtiesContext(BEFORE_CLASS)` so it
>    runs against a fresh Spring context that did not share micrometer-kafka client meter state
>    with `PublisherResilienceIntegrationTest`'s Kafka pause/unpause cycle. The
>    `@DisabledIfEnvironmentVariable("CI")` guard is now removed.
> See the "Outcome" section at the bottom for full diagnosis.

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
the `@Autowired` test-base reference both in place, `WarehouseIntegrationTest.prometheusEndpoint`
still fails on GitHub-hosted runners with `AssertionFailedError` after the 30 s Awaitility retry.
The scrape returns HTTP 200 but the three outbox meter family lines (`outbox_pending_count`,
`outbox_publish_success_total`, `outbox_publish_failure_total`) are absent from the response body
during the window between `PublisherResilienceIntegrationTest`'s Kafka pause/unpause and the next
scrape — the gauge function itself is alive (verified by unit test), but the Prometheus exporter
fails to include the meter family in the scrape output.

**Decision:**

- Keep the `OutboxMetrics` `Gauge.builder().strongReference(true)` change. It is the correct
  registration idiom in its own right, and removes the (real, just non-causal) WeakReference
  collection failure mode.
- Keep the `@Autowired OutboxMetrics outboxMetrics` field on `MasterServiceIntegrationBase`. It
  costs nothing and does provide the belt-and-suspenders guarantee originally intended.
- **Restore the `@DisabledIfEnvironmentVariable("CI")` guard on
  `WarehouseIntegrationTest.prometheusEndpoint_exposesOutboxMetrics`** with an updated
  `disabledReason` that points at scrape-body composition (not gauge GC) as the actual cause.

**Real follow-up (separate task, deferred):** characterize the scrape-body race. Likely
candidates:

1. `micrometer-registry-prometheus` collecting from a Kafka-client `MeterRegistry` view that is
   transiently empty during broker reconnect, suppressing the rest of the family list.
2. `CompositeMeterRegistry` composition order — if Kafka client meters and our outbox meters
   live in the same composite, a partial collection failure could omit later families.
3. Test-suite ordering: moving `prometheusEndpoint_exposesOutboxMetrics` to a separate
   integration suite that does not share Spring context with the Kafka pause/unpause tests
   would sidestep the race entirely.

The follow-up is non-blocking for the ecommerce import or any current PR; the CI guard keeps
the wms baseline green.

## Resolution (2026-04-25, follow-up)

Picked option 3 from the candidate list — context isolation via `@DirtiesContext` rather than
trying to pin down which exact step of the prometheus collection drops the outbox family in
mid-Kafka-reconnect. Concretely:

- Created `OutboxPrometheusScrapeIntegrationTest` (new file, same package) holding only the
  prometheus scrape assertion.
- Annotated the new class with `@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)`. JUnit 5
  reads this before the class runs; Spring's test context cache evicts the shared context and
  rebuilds a fresh one — Postgres + Kafka + Redis containers stay running (they are static),
  but the Spring `ApplicationContext` is new and micrometer-kafka client meters are in their
  initial, healthy state, regardless of whether `PublisherResilienceIntegrationTest` ran first.
- Removed the assertion + `@DisabledIfEnvironmentVariable` from `WarehouseIntegrationTest`.
  Cleaned up the now-unused `ADMIN_ROLE` constant (only the prometheus test had used it).
- Cost: one extra context refresh per integration suite run (~10–30 s on local, similar on CI
  runners). Acceptable for the determinism gain.

The `OutboxMetrics.java` `Gauge.builder().strongReference(true)` change and the
`MasterServiceIntegrationBase` `@Autowired OutboxMetrics` field stay in place — they are still
the right registration idiom for an outbox-pending gauge whose function captures `this`, and
they remove a real (just non-causal in this incident) WeakReference-collection failure mode.

Why not the other candidates:

- (1) and (2) would require either a custom `MeterFilter` or a `CompositeMeterRegistry` split
  across contexts. Both are bigger surgeries against framework internals; option (3) sidesteps
  the unknown by isolating the test rather than fighting the platform. If a future test needs
  the same isolation, we just annotate it the same way.
