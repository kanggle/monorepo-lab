# TASK-MONO-307 — Rehab the ecommerce integration-test harness for CI, then enable a Testcontainers integration lane

**Status:** done

**Type:** TASK-MONO (monorepo-level — shared `.github/workflows/ci.yml` + ecommerce IT harness)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (uncertain-depth IT-harness rehab — container lifecycle / resource / context-init across ~13 IT classes; not a mechanical config add)

> **Reframed 2026-06-26 after a diagnostic CI run (parked PR #1956).** Originally "just add an ecommerce integration CI job (Phase 1: order + payment)." A draft lane was built and pushed; its **first-ever CI execution of these ITs** proved the ecommerce integration **harness has never been CI-viable**, so the lane cannot go green by config alone. The real work is **rehabbing the harness first**, then enabling the (already-drafted) lane. The draft lane YAML is preserved in § Appendix below (and on the parked branch `task/mono-307-ecommerce-it-ci-lane` / closed PR #1956).

---

## Goal

Give ecommerce `@Tag("integration")` ITs a real automation gate. Today they run **nowhere**: ecommerce `:check` carries `excludeTags 'integration'` unless `-PrunIntegration` ([`projects/ecommerce-microservices-platform/build.gradle`](../../projects/ecommerce-microservices-platform/build.gradle) ~L42), **and** — unlike wms / iam / fan / scm / finance / erp / platform-console, which each have a dedicated `Integration (…, Testcontainers)` CI job — there is **no ecommerce integration CI job**. With the local Windows Testcontainers blocker ([`project_testcontainers_docker_desktop_blocker`]), ecommerce ITs only ever **compile**.

This task (1) **rehabilitates the ecommerce IT harness** so the integration-tagged ITs actually pass on a Docker-capable Linux runner, then (2) **enables the integration CI lane** (draft in § Appendix).

## Background — the 2026-06-26 diagnostic run (PR #1956, parked)

A draft lane running `:order-service:test :payment-service:test -PrunIntegration` was pushed. The CI run (`Integration (ecommerce, Testcontainers)`, run `28188815126`) **failed at first execution** — the value of the lane: it surfaced the latent rot. Failure signatures (NOT BE-435 defects — BE-435 is unit-verified and merged in #1954; its stuck IT fails at container **setup**, not at its assertions):

1. **`org.postgresql.util.PSQLException: Connection to localhost:NNNNN refused … postmaster … accepting TCP/IP`** — the per-class Postgres container connection is refused. With ~14 order-service IT classes each spinning their own Postgres + Kafka sequentially on one `ubuntu-latest` runner, this reads as a **container lifecycle / resource-exhaustion** problem (no singleton-container reuse, no resource tuning).
2. **`java.lang.IllegalStateException` (`Assert.state`, `initializationError`)** across ~9 IT classes (`주문 생성/조회/취소/확정/Outbox/Optimistic-Locking 통합 테스트`, `결제 완료/환불 이벤트 통합 테스트`) — **ApplicationContext fails to load** for those classes.
3. **`DataIntegrityViolationException`** at `ConfirmPaidStaleIT.java:273` and `OrderStuckRecoveryIT.java:171` (manual-INSERT setup) — possibly **schema drift** (the ITs' hand-rolled INSERTs predate later NOT-NULL migrations such as multi-tenant `tenant_id` / `seller_id` / `idempotency_key`), OR a downstream symptom of (1).
4. Noise: `[scheduling-1] … Unexpected error occurred in scheduled task` (`SQLState 08001`) — the `OrderStuckDetector` `@Scheduled` sweep firing during context teardown after the DB container is gone. Benign teardown race, but worth silencing in the IT profile.

## Scope

**In scope:**

1. **Diagnose & fix the harness** (the bulk). Likely levers (verify against the reports artifact + a local Docker-capable host, since local Windows cannot run Testcontainers):
   - **Singleton container pattern** — share one Postgres + one Kafka across IT classes (Testcontainers singleton / `@Testcontainers` static + `withReuse`, or an abstract base) instead of N independent container sets, to cut resource pressure and the connection-refused storm.
   - **Context-init failures** — find the shared cause of the ~9 `initializationError`s (a base-class `@DynamicPropertySource`, a missing bean/property, a `@BeforeAll` container pitfall — see `feedback_spring_boot_diagnostic_patterns`).
   - **Schema-drift INSERTs** — align the manual-INSERT IT helpers (`ConfirmPaidStaleIT`, `OrderStuckRecoveryIT`, others) with the current Flyway schema (populate `tenant_id`/`seller_id`/`idempotency_key`/etc.).
   - **Teardown race** — disable the `OrderStuckDetector` `@Scheduled` in the IT profile (or guard it) so teardown is clean.
   - **Gradle invocation** — decide `test -PrunIntegration` (re-runs units) vs a dedicated `integrationTest` source set/task mirroring the sibling projects (cleaner; sibling jobs all call `:…:integrationTest`). A real `integrationTest` task is likely the better long-term shape.
2. **Enable the lane** (§ Appendix draft) once the order + payment ITs are genuinely green on CI.
3. **Phase boundary** — Phase 1 = order-service + payment-service only (the BE-435 money-safety ITs). The other 11 ecommerce services (~54 more IT files) are **separate follow-up phases**; do not attempt all 13 services here.

**Out of scope:** product-code changes (a genuine bug surfaced by an IT → separate fix task referencing it); nightly-e2e.yml; the other 11 services' ITs; any `continue-on-error` soft-fail (F1).

## Acceptance Criteria

- **AC-1** — The order-service + payment-service `@Tag("integration")` ITs **pass on a Docker-capable runner** (CI Linux is the verification environment — local Windows cannot run Testcontainers). The three failure classes above are resolved (container lifecycle, context-init, schema-drift) or the offending IT is explicitly quarantined with a tracked follow-up (F2).
- **AC-2** — The `Integration (ecommerce, Testcontainers)` CI job (§ Appendix) is added and observed **genuinely green** on its PR. **No `continue-on-error`** (F1).
- **AC-3** — `build-and-test` (Docker-free `:check`) unchanged and still green; the new lane is additive.
- **AC-4** — Any quarantined IT references a fix task and states what it suppresses (F2). Any product bug surfaced spins a separate fix task.
- **AC-5** — The harness fix is documented (singleton pattern / base class / schema alignment) so the remaining 11 services' phases can reuse it.

## Related Specs / References

- Parked draft lane: branch `task/mono-307-ecommerce-it-ci-lane`, closed PR **#1956**, CI run `28188815126` (the diagnostic evidence). § Appendix preserves the YAML.
- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) — sibling integration jobs (scm ~L1091, finance ~L1143, erp ~L1195, console-bff ~L1248) are the template; all call a dedicated `:…:integrationTest` task.
- [`projects/ecommerce-microservices-platform/build.gradle`](../../projects/ecommerce-microservices-platform/build.gradle) ~L35–48 — `-PrunIntegration` gating; no `integrationTest` task today.
- [`platform/testing-strategy.md`](../../platform/testing-strategy.md) — Testcontainers-only IT policy.
- `feedback_spring_boot_diagnostic_patterns` (auto-memory) — `@BeforeAll`-container pitfall, context-wiring traps.

## Related Contracts

- None (CI / test infrastructure).

## Dependencies / Prior Work

- **TASK-BE-435** (merged #1954) — authored the order/payment money-safety ITs this lane is meant to gate; their first CI attempt is the diagnostic evidence here.
- **TASK-MONO-048 / TASK-MONO-115 / TASK-MONO-124** — the scm / finance / erp integration-lane additions (each with a real `integrationTest` task) this should mirror.

## Edge Cases

- **Resource pressure on `ubuntu-latest`** — ~14 order-service IT classes × (Postgres+Kafka) is the prime suspect for the connection-refused storm; a singleton container is the likely fix. Validate container count/peak memory.
- **`-PrunIntegration` re-runs units** — a dedicated `integrationTest` source set (sibling pattern) avoids this and is the cleaner gate.
- **Local verification impossible** — the Windows host cannot run Testcontainers; iteration is via CI (or a Docker-capable host). Budget for CI-round-trip debugging.

## Failure Scenarios

- **F1 — green theatre** — never add the lane with `continue-on-error`; it must be a genuine gate (AC-2).
- **F2 — masking a real bug** — a quarantine must reference a fix task and name what it suppresses (AC-4).
- **F3 — scope creep into all 13 services** — Phase 1 is two services; the other 11 are separate phases (AC-5 makes the fix reusable for them).

## Phase 1 — implementation outcome (2026-06-26)

The lane was built and run on its PR. **First CI execution diagnosis** (the value of the lane — it surfaced the rot):

**Harness fixes (this task, AC-5 reusable for the other 11 services):**
1. **Docker-env override** (the dominant root of the diagnostic's class 1+2). The order/payment `build.gradle` `test {}` blocks **unconditionally** set a local-Windows `DOCKER_HOST=npipe:////./pipe/docker_engine_linux` + `-Dapi.version=1.44`, sabotaging Testcontainers on a Linux runner. Fix: a dedicated `integrationTest` `Test` task (mirrors scm/finance/erp/console-bff) which does **not** inherit that override and instead picks up the root `tasks.withType(Test).configureEach` env (conditional `DOCKER_HOST` + `DOCKER_API_VERSION=1.45`). The lane calls `:order-service:integrationTest :payment-service:integrationTest` (not `test -PrunIntegration`). Containers then started and tests executed (3 min run).
2. **Schema drift** — `ConfirmPaidStaleIT` + `OrderStuckRecoveryIT` manual `INSERT INTO orders` omitted `tenant_id` (NOT NULL, no default, Flyway V8) → fixed with `tenant_id='ecommerce'`.
3. **`@SpringBootConfiguration` ambiguity** (the diagnostic's class 2 — ~10 `initializationError`s). Bare `@SpringBootTest` ITs (no `classes=`) auto-detected BOTH `OrderServiceApplication` (main) and `TestOrderServiceApplication` (a `@WebMvcTest` slice helper) → `IllegalStateException: Found multiple @SpringBootConfiguration`. Fix: pinned `classes = OrderServiceApplication.class` on the 10 bare order ITs (matching the 4 that already did).

**Latent product bugs surfaced → quarantined (AC-1/F2) + separate fix tasks (AC-4):**
- **TASK-BE-439** (order-service) — `OrderStuckDetector`/confirm-paid-stale read paths map **detached** entities outside a tx; `OrderJpaMapper.toDomain` touches lazy `items` → `LazyInitializationException`; the sweeper's `catch` swallows it → **never recovers any stuck order** in production. Quarantined: `OrderStuckRecoveryIT` (class), `ConfirmPaidStaleIT` (`noBearer_returns401`, `validBearer_confirmsOnlyPaidUnconfirmed`).
- **TASK-BE-440** (payment-service, money-safety) — the `OrderCancelled`→refund/void consumer path persists **without an active transaction** (`No EntityManager with actual transaction available`) → a captured payment for a cancelled order is not refunded via the event path. Quarantined: `PaymentRefundIntegrationTest` (`orderCancelled_refundsPayment`, `completedThenCancelled_paymentTimeout_refunds`) + `PaymentRefundStrandedDurabilityIntegrationTest` (class — likely a downstream symptom).

**Second CI round** (after the config-ambiguity fix) ran the full suite — **64 tests** (was 29) — and surfaced the residual long tail: the lazy-init (BE-439) proved **pervasive** (also `OrderPlacement`, `OrderEventPublish`, `OrderOptimisticLock`), plus assertion/behavioural drift unrelated to the two systemic bugs → **TASK-BE-441** (triage: 403 error-code `UNAUTHORIZED`↔`ACCESS_DENIED`, seller-scope ABAC AC-3, `@Version` optimistic-lock not raised, `saveAll` not raised, refund-PENDING DLQ not propagated). All residuals are **method-level `@Disabled`** (class-level only where every test failed — `OrderStuckRecoveryIT`, `OrderPlacement`, `OrderOptimisticLock`), preserving the passing tests in each class.

After the fixes + quarantines (~16 of 64 quarantined, all ticket-referenced), the lane gates the **~48 genuinely-passing** order + payment ITs (incl. the BE-438 reconciliation IT, multi-tenant isolation, event-dedup, payment processing/refund-happy-path, etc.). The singleton-container refactor was **not** needed for Phase 1 (tests run single-fork/sequential; the connection-refused signal was the Docker-env override, not simultaneous-container exhaustion) — left as an optional future optimisation. AC-4: every quarantine references its fix task ([[TASK-BE-439]], [[TASK-BE-440]], [[TASK-BE-441]]); AC-2: the lane is a genuine gate (no `continue-on-error`). The three follow-up fix tasks lift their quarantines and bring the lane to full coverage.

## Appendix — drafted lane YAML (preserve; insert after the `platform-console-bff-integration-tests` job in `.github/workflows/ci.yml`)

```yaml
  ecommerce-integration-tests:
    name: Integration (ecommerce, Testcontainers)
    runs-on: ubuntu-latest
    needs: [changes, build-and-test]
    timeout-minutes: 30
    # ecommerce integration suite boots Postgres + Kafka via Testcontainers.
    # ecommerce has no separate `integrationTest` task — @Tag("integration") ITs
    # live in src/test and are excluded from the Docker-free `check` unless
    # `-PrunIntegration`. Phase 1 = order-service + payment-service (gates the
    # TASK-BE-435 money-safety ITs). Other ecommerce services = phased follow-ups.
    if: >-
      github.repository == 'kanggle/monorepo-lab' &&
      (
        github.event_name == 'push' ||
        needs.changes.outputs.libs == 'true' ||
        needs.changes.outputs.workflows == 'true' ||
        needs.changes.outputs.ecommerce == 'true'
      )
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 21 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Verify Docker (Testcontainers prerequisite)
        run: docker info
      - name: Run ecommerce integration suites (order-service + payment-service)
        # NOTE (rehab): prefer a dedicated `integrationTest` task over
        # `test -PrunIntegration` (which re-runs units); harden container reuse
        # before relying on this. See task body § Scope.
        run: >-
          ./gradlew
          :projects:ecommerce-microservices-platform:apps:order-service:test
          :projects:ecommerce-microservices-platform:apps:payment-service:test
          -PrunIntegration --no-daemon --stacktrace
      - name: Upload ecommerce integration test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: ecommerce-integration-test-reports
          path: |
            projects/ecommerce-microservices-platform/apps/order-service/build/reports/tests/
            projects/ecommerce-microservices-platform/apps/order-service/build/test-results/
            projects/ecommerce-microservices-platform/apps/payment-service/build/reports/tests/
            projects/ecommerce-microservices-platform/apps/payment-service/build/test-results/
          retention-days: 7
```
