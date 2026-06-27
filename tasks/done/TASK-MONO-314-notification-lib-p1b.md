# Task ID

TASK-MONO-314

# Title

`ADR-MONO-043 P1b` — Lift the shared notification library `libs/java-notification` (D4)

# Status

done

# Owner

backend-engineer

# Task Tags

- adr
- architecture
- notification
- libs
- shared-library

---

# Goal

Second half of ADR-MONO-043 **P1**: implement the **D4** deliverable — a new shared `libs/java-notification` library that lifts the consumer/dedupe/DLT/Category-C-delivery/channel-SPI machinery the four per-domain notification-services each re-derive today, as **configurable, project-agnostic abstractions** (composition-over-inheritance, ADR-MONO-038 posture). It implements the shape pinned by the P1a contract (`platform/contracts/notification-inbox-contract.md`) and is what the **P2** per-domain conformance tasks adopt.

The lib carries **no domain content** (HARDSTOP-03): channels, recipients, topics, schema, and outbox event types are all service-supplied via the SPIs.

# Scope

## In Scope

| 산출물 | 위치 | 설명 |
|---|---|---|
| 신규 Gradle 모듈 | `libs/java-notification/` (+ `settings.gradle` 등록) | package `com.example.platform.notification` (ecommerce `com.example.notification` 충돌 회피). deps: java-common + java-messaging (EventDedupePort/EventEnvelope 재사용) + data-jpa + spring-kafka + spring-tx + jackson + validation. NO OutboxAutoConfiguration. |
| Canonical view (§1) | `view/NotificationView.java` | 계약 §1 item shape (id/sourceDomain/type/title/body/deepLink?/read/readAt?/createdAt), Jackson NON_NULL. |
| Channel SPI | `channel/{NotificationChannelAdapter,ChannelDeliveryRequest,ChannelResult}.java` | never-throw `deliver()`; ChannelResult delivered/permanent/ref/error + factories. |
| Consumer support | `consumer/NotificationConsumerSupport.java` | envelope-validate + `EventDedupePort.process` 위임 (optional helper, not a mandatory base). |
| Category-C delivery engine | `delivery/` | `DeliveryRecord`(state machine), `DeliveryStatus`, `BackoffCalculator`(list-indexed jittered backoff), `DeliveryStore`(port), `DeliveryDispatcher`(orchestrator, pure logic), `DeliveryOutcome`, `DeliveryOutcomeListener`(outbox-emit SPI), `jpa/DeliveryRecordEntity`(@MappedSuperclass, @Version, DB-portable), exceptions. |
| 단위테스트 | `src/test/...` | 40 tests, Docker-free `:test`. |

## Out of Scope

- **P2** per-domain conformance (erp/ecommerce/wms/fan adopt the lib) + the wms inbox-vs-delivery-only decision.
- **P3** console-bff aggregator.
- Any change to the four notification services (none touched).
- Outbox re-emission / @Scheduled / @Transactional(REQUIRES_NEW) / FOR UPDATE SKIP LOCKED — these stay **service-side** (the lib provides the callable + ports; the service wires the boundary). Documented in the dispatcher javadoc.

---

# Acceptance Criteria

- [x] **AC-1** — `libs/java-notification` module exists, registered in `settings.gradle`, package `com.example.platform.notification` (no `com.example.notification` collision).
- [x] **AC-2** — `NotificationView` matches contract §1 exactly with Jackson NON_NULL (readAt/deepLink omit when null).
- [x] **AC-3** — Channel SPI: never-throw `NotificationChannelAdapter` + `ChannelResult` with the permanent-vs-transient flag (the wms invariant the retry engine needs).
- [x] **AC-4** — Category-C engine faithfully generalizes the wms reference: list-indexed jittered backoff `[1,5,30,120,600]`/±0.2/max-5, PENDING→SUCCEEDED/RETRY/FAILED state machine, retry-exhaustion applies terminal FAILED then throws, permanent→immediate FAILED, terminal immutable, `@Version` on the JPA base.
- [x] **AC-5** — `EventDedupePort` (java-messaging) reused for dedupe (not re-invented).
- [x] **AC-6 (HARDSTOP-03)** — project-agnostic: no domain event types, no recipient logic, no service names, no channel credentials. DB-portable JPA base (no Postgres-only types).
- [x] **AC-7 (build)** — `./gradlew :libs:java-notification:test` + `:build` BUILD SUCCESSFUL (40 unit tests, no Docker).
- [x] **AC-8** — only `libs/java-notification/**` + the one `settings.gradle` line changed; no notification service modified.

---

# Related Specs

- [ADR-MONO-043](../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — ACCEPTED; D4 = this lib.
- [platform/contracts/notification-inbox-contract.md](../../platform/contracts/notification-inbox-contract.md) — P1a; the §1 shape this lib's `NotificationView` implements + the §4 Category-C/aggregator expectations.
- [ADR-MONO-038](../../docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md) — the lift posture (lib owns control-flow+ports, service owns adapters).
- [ADR-MONO-005 §2.5](../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) — Category-C taxonomy; wms `DeliveryDispatchPerRow` is the reference the engine generalizes.

# Related Contracts

- Implements the shared shape from `platform/contracts/notification-inbox-contract.md` (P1a). The per-domain adapters that wire this lib = P2.

---

# Edge Cases

- **Lib carries domain content** → HARDSTOP-03. Channels/recipients/topics/event-types are all SPI-supplied; the lib names no service.
- **Channel adapter throws despite never-throw contract** → `DeliveryDispatcher.safeDeliver` maps it to a transient failure (defensive).
- **Unconfigured channel** → permanent failure (the wms `ChannelNotConfiguredException` → FAILED behaviour).
- **Record already terminal** → dispatch is a no-op returning the terminal outcome.
- **OutboxAutoConfiguration pulled transitively** → excluded; the lib must not require the v1 outbox tables (same stance as all four services).

# Failure Scenarios

- **Category-C mechanics drift from the wms reference** → behaviour parity breaks (the reference is the ADR-005 named impl). The state machine + backoff arithmetic are lifted verbatim and unit-pinned.
- **REQUIRES_NEW / SKIP-LOCKED baked into the lib** → would couple the lib to Spring/DB; kept service-side (documented).

---

# Definition of Done

- [x] `libs/java-notification` implemented (view + channel SPI + consumer support + Category-C engine + JPA base + tests).
- [x] `settings.gradle` registration.
- [x] `:libs:java-notification:test` + `:build` GREEN (40 tests, Docker-free).
- [x] `tasks/INDEX.md` done entry.
- [ ] commit + push (branch `task/mono-314-notification-lib-p1b`) + PR + Build&Test CI GREEN + merge (3-dim verify).
- [ ] P2 per-domain conformance (erp/ecommerce/wms/fan) — separate tasks (next phase).
