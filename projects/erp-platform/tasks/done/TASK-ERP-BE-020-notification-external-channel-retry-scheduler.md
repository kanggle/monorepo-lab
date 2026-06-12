# TASK-ERP-BE-020 — notification external channel + DeliveryRetryScheduler (v2 external delivery)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (external integration + Category C retry scheduler + atomic gated dispatch)

---

## Goal

Realise the notification-service **v2 external-channel delivery** that the architecture
reserves as deferred (`architecture.md` § Out-of-Scope "External channels … the real
adapter + the **exercised** Category C `DeliveryRetryScheduler` … are v2"; Failure Mode 7;
Category C table "v2 (external channel): `DeliveryRetryScheduler` poll + exponential
backoff ±20% jitter, **cap 5** → terminal `FAILED` + `DELIVERY_RETRY_EXHAUSTED`"). v1 ships
an IN_APP synchronous-DELIVERED path + a green-wash-safe `NoopExternalChannelAdapter` stub;
the domain `NotificationDelivery` already carries the full Category C machine
(`markRetryable`/`markFailed`/`scheduledRetryAt`/`attemptCount`/`maxAttempts`), and the
schema already reserves `SLACK`/`SMTP` + the Category C columns.

This increment **exercises** that reserved path: a **real Slack-webhook channel adapter**
(best-effort, HTTP) + a **`DeliveryRetryScheduler`** that polls due PENDING external
deliveries and drives them DELIVERED / retried / FAILED through the existing domain machine.
A v1.3 `architecture.md` amendment is authored **before** implementation (HARDSTOP-09; mirrors
the v1.1/v1.2 delegation amendments) — it **executes** the already-recorded ADR-MONO-005
Category C + ADR-MONO-016 § D3 forward-declaration, introducing **no new ADR-level decision**.

## Scope

**In scope (notification-service only):**
1. **`NotificationDelivery.createPendingExternal(...)`** (domain) — a PENDING delivery on an
   external channel with `scheduledRetryAt = now` (immediately due). Additive — the existing
   `createPending` (IN_APP) + the state machine are byte-unchanged.
2. **Gated external-delivery creation** in `NotifyOnApprovalEventUseCase.dispatch` — when
   `erpplatform.notification.external.enabled=true`, **additionally** persist one PENDING
   `SLACK` delivery (alongside the unchanged IN_APP DELIVERED row) in the **same** consume
   transaction (A7 atomicity). **Default `false` ⇒ net-zero**: dispatch creates only the
   IN_APP delivery exactly as v1 (existing behaviour + tests byte-unchanged).
3. **`SlackWebhookChannelAdapter`** (infrastructure/channel) — `@ConditionalOnProperty(mode=slack)`;
   POSTs a rendered Slack message to the configured webhook via a `ResilienceClientFactory`
   RestClient; **best-effort** — returns `DeliveryOutcome.ofDelivered()` only on a 2xx, else a
   non-delivered outcome (carrying the error detail); **never throws** (green-wash discipline:
   no false DELIVERED). `NoopExternalChannelAdapter` becomes `@ConditionalOnProperty(mode=noop,
   matchIfMissing=true)` — exactly one `SLACK` `NotificationChannelPort` bean per mode.
4. **`DeliveryRetryScheduler`** (infrastructure) — `@ConditionalOnProperty(retry-enabled)` +
   `@Scheduled(fixedDelay)`; single-instance, non-reentrant; calls the application service.
5. **`RetryDeliveryService`** (application orchestrator) — finds due delivery ids
   (`status=PENDING ∧ scheduled_retry_at ≤ now`) and processes each via
   **`DeliveryAttemptProcessor`** (`@Transactional` per delivery — load → deliver (best-effort)
   → `markDelivered` / `markRetryable(backoff)` → save; terminal-`FAILED` at cap 5).
6. **`RetryBackoffPolicy`** (application) — exponential `initial·2^(n-1)` capped at `max`, with
   **±20% jitter** (injectable `RandomGenerator` for deterministic tests).
7. **Repository reads**: `NotificationDeliveryRepository.findById` + `findDueDeliveryIds(now, limit)`
   (port + impl + `@Query`); `NotificationRepository.findByIdInternal(tenantId, id)` (the retry
   processor loads the Notification to render the external message — a system-internal read, not
   the recipient-scoped inbox read).
8. **Config**: `ExternalNotificationProperties` (`erpplatform.notification.external.*`) +
   `application.yml` defaults (everything OFF / net-zero) + `SchedulingConfig`
   (`@EnableScheduling` + a `RandomGenerator` bean). **No Flyway migration** — the schema
   already reserves `SLACK`/`SMTP` + the Category C columns + the CHECK allow-lists.
9. **`architecture.md` v1.3 amendment** (authored before impl) + Out-of-Scope / Failure Mode 7 /
   Category C status update to "external = DONE (TASK-ERP-BE-020)".
10. Tests: `SlackWebhookChannelAdapterTest` (MockWebServer 2xx→delivered+auth/payload, 5xx/down→
    not-delivered+no-throw), `RetryBackoffPolicyTest` (exponential growth + jitter bounds + cap),
    `DeliveryAttemptProcessorTest` (delivered→DELIVERED+metric; transient→PENDING+scheduledRetryAt;
    cap→FAILED), `RetryDeliveryServiceTest` (due ids → per-id process; empty → no-op),
    `NotificationDelivery` createPendingExternal unit, `NotifyOnApprovalEventUseCaseTest`
    (external-enabled → second PENDING SLACK delivery; default → IN_APP only, unchanged).

**Out of scope (still deferred):**
- **SMTP** channel (the enum reserves it; only SLACK is realised here), push/APNs.
- **Multi-instance** retry concurrency — the single-instance `fixedDelay` scheduler is
  non-reentrant; the persisted `version` (T5) column is the seam for a future
  `@Version`/conditional-update (or ShedLock) enforcement, **not wired here** (documented follow-on).
- Notification **preferences/routing** (which recipients get external delivery), masterdata/
  permission notifications, digest/batching, display-name enrichment, console bell UI — all v2 per
  the existing Out-of-Scope.
- Any change to the inbox REST surface, the 6 approval/delegation consumers, the dedupe/idempotency,
  or the IN_APP delivery path (beyond the gated additive external-delivery creation).

## Acceptance Criteria

- **AC-1 (real delivery)** — with `external.enabled=true` + `mode=slack`, a dispatched approval
  event creates an IN_APP DELIVERED row **and** a PENDING SLACK row (due now); the scheduler's
  next tick POSTs to the webhook and, on 2xx, transitions the SLACK delivery
  `PENDING → DELIVERED` (`attempt_count=1`); the IN_APP row is unaffected.
- **AC-2 (retry + backoff)** — a transient webhook failure (5xx / timeout / connection refused)
  leaves the SLACK delivery `PENDING` with `attempt_count++` and `scheduled_retry_at = now +
  backoff` (exponential, ±20% jitter); a later tick (after the backoff) re-attempts. **No throw**;
  the IN_APP delivery and the inbox are unaffected.
- **AC-3 (exhaustion)** — after **5** failed attempts the SLACK delivery is terminal `FAILED`
  with `DELIVERY_RETRY_EXHAUSTED` recorded in `last_error`; no further attempt; a
  `notification_delivery_status_total{status=FAILED}` signal is emitted. Terminal rows are never
  re-attempted (the due query excludes terminal status).
- **AC-4 (net-zero default)** — `external.enabled` unset/false ⇒ dispatch creates **only** the
  IN_APP delivery (no SLACK row), the scheduler bean is absent, the `NoopExternalChannelAdapter`
  is the (unused) SLACK bean; v1 behaviour + every existing test byte-unchanged. Exactly one
  `SLACK` `NotificationChannelPort` bean in each mode.
- **AC-5 (best-effort / green-wash)** — the Slack adapter never throws and never reports a false
  DELIVERED; a non-2xx / transport error returns a non-delivered outcome → `markRetryable`. A
  notification missing at attempt time → permanent `FAILED` (cannot render), not a crash.
- **AC-6** — `:notification-service:check` BUILD SUCCESSFUL (unit + slice); CI "Integration
  (erp-platform, Testcontainers)" GREEN; default config = net-zero (no regression).

## Related Specs

- `projects/erp-platform/specs/services/notification-service/architecture.md` (v1.3 amendment here — external channel + exercised Category C `DeliveryRetryScheduler`; Failure Mode 7; Category C table; Out-of-Scope "External channels" → DONE)
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` (§ D5 Category C — cap 5 + structured terminal outcome; notification-service is the canonical Category C reference)
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D3 (notification-service v2 forward-declaration — executed, not reopened)

## Related Contracts

- No event/HTTP contract change — notification-service is a terminal consumer (no `erp.notification.*`); the inbox REST surface is untouched. The Slack webhook is an outbound integration (no published interface).

## Edge Cases

- **Default OFF** — no SLACK delivery created; scheduler absent; net-zero.
- **Webhook 2xx** — DELIVERED, attempt_count=1, scheduled_retry_at cleared.
- **Webhook 5xx / timeout / connection refused** — markRetryable → PENDING + backoff; re-attempted next due tick.
- **5th failure** — terminal FAILED + `DELIVERY_RETRY_EXHAUSTED`; never re-attempted.
- **Notification row missing at attempt time** — permanent FAILED (cannot render), no crash.
- **Concurrent tick** — single-instance `fixedDelay` is non-reentrant → no overlap (multi-instance enforcement = documented follow-on via `version`).
- **Terminal delivery in the due window** — excluded by the `status=PENDING` due query.

## Failure Scenarios

- **F1 — green-wash** — a stub/real adapter reporting DELIVERED without an actual 2xx. Guarded by AC-5 (delivered only on 2xx) + the port's green-wash discipline.
- **F2 — consume-tx coupling** — a slow/failed webhook call inside the Kafka consume transaction rolling back the in-app notification. Guarded by the async split: dispatch only **persists** a PENDING external row; all external I/O happens in the scheduler's own per-delivery transaction (AC-1/AC-2).
- **F3 — infinite retry** — a permanently-failing webhook retried forever. Guarded by AC-3 (cap 5 → terminal FAILED).
- **F4 — net-zero regression** — a default-on external channel delivering unbidden. Guarded by AC-4 (`external.enabled=false` default; mock adapter `matchIfMissing`).
- **F5 — double delivery** — a delivered row re-attempted. Guarded by the `status=PENDING` due query + terminal immutability (`DeliveryStateTransitionInvalidException`).

---

## Closure

- **Impl PR**: #1368 — squash `fc1439a652d2435ccfa96e4934b2b675c2978a8c` (merged 2026-06-12). 3-dim verified: (a) state=MERGED; (b) `origin/main` tip = the squash commit; (c) pre-merge checks = **20 SUCCESS + 1 SKIPPED, 0 failing required** (mergeState CLEAN — disjoint from the concurrent FIN-BE-011 merge, no conflict).
- **Delivered**: `SlackWebhookChannelAdapter` (real, `@ConditionalOnProperty mode=slack`, `ResilienceClientFactory` RestClient, best-effort/never-throw, green-wash-safe) + `NoopExternalChannelAdapter` gated default (`mode=noop`/`matchIfMissing`) + `DeliveryRetryScheduler` (`@ConditionalOnProperty retry.enabled` + `@Scheduled fixedDelay`, non-reentrant) → `RetryDeliveryService` → `DeliveryAttemptProcessor` (`@Transactional` per delivery) + `RetryBackoffPolicy` (exponential + ±20% jitter, cap 5) + gated external-delivery creation in `NotifyOnApprovalEventUseCase` + `NotificationDelivery.createPendingExternal` + repo reads (`findById`/`findDueDeliveryIds`/`findByIdInternal`) + `ExternalNotificationProperties` + `SchedulingConfig` + `application.yml` + `architecture.md` v2.0 amendment.
- **Verification**: `:notification-service:check` BUILD SUCCESSFUL — **84 tests** (6 new test classes/cases; existing suite byte-unchanged under default-OFF). **Testcontainers integrationTest validated GREEN** (full ApplicationContext; locally + CI "Integration (erp-platform, Testcontainers)" 2m6s). No Flyway migration (schema already reserved `SLACK`/`SMTP` + Category C columns + CHECK allow-lists).
- **CI-caught wiring fix**: `RetryBackoffPolicy`'s two constructors made Spring unable to auto-select one (UnsatisfiedDependencyException at full-context load) — invisible to the Docker-free `:check`, surfaced only by the Testcontainers IT (the `feedback_spring_boot_diagnostic_patterns` §19/§20 lesson). Fixed with `@Autowired` on the injection constructor; re-validated by a local IT run.
- **AC**: AC-1…AC-6 satisfied. Default config = net-zero; **no domain/contract change, no vendor SDK**.
- **Deferred (follow-on)**: multi-instance retry concurrency (the `version` optimistic-lock / ShedLock enforcement); SMTP / push channels; notification preferences/routing; console bell UI.
