---
id: TASK-FIN-BE-041
title: "FX rate poller ShedLock single-leader (ADR-002 deferred — multi-instance safety)"
status: done
service: ledger-service
tags: [code, test, migration, scheduled-task, shedlock, infra]
analysis_model: "Opus 4.8"
impl_model: "Sonnet 4.6"
created: 2026-06-19
---

# TASK-FIN-BE-041 — FX rate poller ShedLock single-leader

## Goal

Realize the deferred ADR-002 (`projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md`
D4) item: guard the scheduled FX-rate poller with a **ShedLock single-leader** so that in a
multi-instance deployment only ONE instance polls the external FX provider + upserts
`fx_rate_quote` per interval. Today the poller has **no single-leader guard** (the code +
`architecture.md` both note "ShedLock is a sketch; deferred") → N instances would each hammer the
external API and race the upsert. Production-hardening, behavior-preserving for single-instance.

## Reference pattern (mirror exactly)

ecommerce **shipping-service** already does this — copy its shape:
- deps: `apps/shipping-service/build.gradle` →
  `net.javacrumbs.shedlock:shedlock-spring:6.2.0` + `shedlock-provider-jdbc-template:6.2.0`
- config: `apps/shipping-service/.../infrastructure/config/SchedulerConfig.java`
  (`@EnableSchedulerLock(defaultLockAtMostFor=...)` + `JdbcTemplateLockProvider` `.usingDbTime()`)
- annotated method: `AutoCollectTrackingScheduler.runSweep()` —
  `@SchedulerLock(name=..., lockAtMostFor="PT5M", lockAtLeastFor="PT5S")`
- migration: `apps/shipping-service/.../db/migration/V6__create_shedlock_table.sql`
- `.claude/skills/backend/scheduled-tasks/SKILL.md` (the scheduled-task conventions)

## Target (current state)

- Poller: `projects/finance-platform/apps/ledger-service/src/main/java/com/example/finance/ledger/infrastructure/fxrate/FxRateFeedPoller.java` — `@Scheduled(fixedDelayString=...)` `poll()` (gated by `financeplatform.ledger.fxrate.enabled`). The code comment + `specs/services/ledger-service/architecture.md` (~line 1847) say ShedLock is deferred.
- ledger-service uses **MySQL 8**; current Flyway max = **V13** → this task uses **V14**.

## Scope

1. **Dependencies** — add the two ShedLock deps to
   `apps/ledger-service/build.gradle` (same versions as shipping-service).
2. **SchedulerConfig** — add a config class: `@EnableSchedulerLock(defaultLockAtMostFor="PT5M")` +
   a `LockProvider` bean (`JdbcTemplateLockProvider` over the ledger `DataSource`, `.usingDbTime()`).
   Mirror shipping-service's `SchedulerConfig`.
3. **V14 migration** — `V14__create_shedlock_table.sql` (the standard ShedLock table; copy
   shipping-service's V6 verbatim, MySQL/InnoDB/utf8mb4).
4. **`@SchedulerLock`** on `FxRateFeedPoller.poll()` — `name="ledger-fx-rate-poll"` (or similar),
   `lockAtMostFor` ≥ the max expected poll duration, `lockAtLeastFor` a few seconds. Keep the
   existing `@Scheduled` + `@ConditionalOnProperty` behavior unchanged.
5. **Spec** — update `specs/services/ledger-service/architecture.md` FX-feed section: change the
   "No ShedLock single-leader guard … deferred" note to "single-leader guard via ShedLock
   (TASK-FIN-BE-041)". Append an ADR-002 §3 (roadmap/log) note that the ShedLock single-leader
   deferred item is now realized. **NOTE**: a sibling task (TASK-FIN-BE-042) edits the SAME
   `architecture.md` FX section on its own branch — touch only the poller/ShedLock lines; the merge
   will reconcile (precedent: this is a known, small spec overlap).
6. **Tests** — unit/wiring test (no multi-instance/Testcontainers needed locally): assert the
   poller method carries `@SchedulerLock` with a non-blank name (reflection), and that
   `SchedulerConfig` exposes a `LockProvider` bean. (True single-leader behavior is a multi-instance
   property validated in deployment, not a unit test — note this.)

## Acceptance Criteria

- AC-1: `FxRateFeedPoller.poll()` is annotated `@SchedulerLock` with a stable lock name; the
  `@Scheduled`/`@ConditionalOnProperty` behavior is otherwise unchanged.
- AC-2: `SchedulerConfig` provides a `JdbcTemplateLockProvider` `LockProvider` (`.usingDbTime()`),
  `@EnableSchedulerLock` present.
- AC-3: `V14__create_shedlock_table.sql` creates the standard ShedLock table (MySQL/InnoDB/utf8mb4).
- AC-4: build + unit tests GREEN —
  `./gradlew :projects:finance-platform:apps:ledger-service:test`.
- AC-5: single-instance behavior unchanged (the lock is a no-contention pass-through with one
  instance); `architecture.md` updated; ADR-002 deferred item marked realized.

## Related Specs / Contracts

- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` D4 (the ShedLock decision/deferral)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (FX-feed section)
- `.claude/skills/backend/scheduled-tasks/SKILL.md`
- Reference impl: ecommerce shipping-service `SchedulerConfig` + `AutoCollectTrackingScheduler` + V6

## Edge Cases

- **Single instance** (demo/standalone): no lock contention; `poll()` runs every interval as today
  (net-zero).
- **Poller disabled** (`fxrate.enabled=false`): `@ConditionalOnProperty` already prevents the bean;
  ShedLock wiring must not force the poller on.
- **Lock held by a dead instance**: `lockAtMostFor` bounds the stale lock; pick a value ≥ poll
  duration so a crashed leader's lock auto-expires.

## Failure Scenarios

- **lockAtMostFor too short** → a slow poll could let a second instance acquire mid-run: set it
  generously above the realistic poll duration.
- **ShedLock table missing** → ShedLock errors at runtime: V14 must land with the code (same PR).
- **`usingDbTime()` omitted** → clock-skew between instances breaks the lock window: include it
  (matches shipping-service).
- **Flyway version clash**: this task owns **V14**; the sibling TASK-FIN-BE-042 owns **V15** — do
  NOT use V15 here.
