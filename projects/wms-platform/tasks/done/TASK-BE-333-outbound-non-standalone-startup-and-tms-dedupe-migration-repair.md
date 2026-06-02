# Task ID

TASK-BE-333

# Title

`outbound-service` — repair two latent breakages that prevent it from starting in any non-`standalone` profile: (1) a Spring **bean-name collision** (`outboxPublisher`) between outbound's own `@Component` and the shared `OutboxAutoConfiguration` `@Bean`, and (2) a **Flyway migration conflict** on `tms_request_dedupe` (V4 defines a different schema than V13 + the runtime entity). Both were latent because outbound-service is **not deployed in any running stack** and its `integrationTest` is **not wired into CI**.

# Status

done

# Owner

backend-engineer (outbound-service app config + one Flyway migration — no domain/contract/ADR change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **discovered by**: the TASK-BE-332 audit follow-up ("wire outbound `integrationTest` into CI"). Attempting to run outbound's ITs revealed they were entirely broken — and the breakage is in outbound's startup/migration, not just tests.
- **bug 1 — bean-name collision (startup)**: outbound's `adapter/out/event/publisher/OutboxPublisher` is `@Component @Profile("!standalone")` (default bean name `outboxPublisher`), and the shared `libs/java-messaging` `OutboxAutoConfiguration` also defines an `@Bean outboxPublisher()` (type `com.example.messaging.outbox.OutboxPublisher`, `@ConditionalOnMissingBean`). The condition is **by type**, and outbound's bean is a DIFFERENT type (`extends AbstractOutboxPublisher`), so the auto-config bean is NOT suppressed → two definitions named `outboxPublisher` → `BeanDefinitionOverrideException` under any non-`standalone` profile (every `@SpringBootTest` IT + non-standalone startup). outbound supplies its OWN outbox publisher/writer and uses NO libs auto-config bean (verified — no reference to `OutboxJpaConfig` / `com.example…OutboxPublisher`/`OutboxWriter`).
- **bug 2 — migration conflict**: `V4__init_packing_shipping_tables.sql` created `tms_request_dedupe (shipment_id PK, idempotency_key, tms_status, requested_at)`, but `V13__tms_request_dedupe.sql` AND the runtime entity `TmsRequestDedupeEntity` use a DIFFERENT schema `(request_id PK, sent_at, response_snapshot JSONB)`. V13's `CREATE TABLE IF NOT EXISTS` no-ops against V4's table, then its `CREATE INDEX … (sent_at)` fails — `column "sent_at" does not exist` — breaking the Flyway chain. Canonical schema is the runtime entity (= V13).
- **no dependency on**: any domain/contract/ADR change. No deployed outbound DB exists (service never deployed) → editing V4 in place is safe (no applied-migration checksum/state to honour).

---

# Goal

`outbound-service` boots in non-`standalone` profiles (production + the `integration` test profile): no `outboxPublisher` bean collision, and the Flyway chain applies cleanly (`tms_request_dedupe` consistent across V4/V13/the entity).

# Scope

## In Scope

`projects/wms-platform/apps/outbound-service`:

1. **Bean collision** — `@SpringBootApplication(exclude = OutboxAutoConfiguration.class)` on `OutboundServiceApplication` (outbound is outbox-self-contained; excluding the libs auto-config removes the colliding `outboxPublisher`/`outboxWriter` `@Bean`s + the unused `OutboxJpaConfig`).
2. **Migration conflict** — reconcile `V4`'s `tms_request_dedupe` CREATE to the canonical schema (`request_id`/`sent_at`/`response_snapshot`), matching `TmsRequestDedupeEntity` + V13. V8's column-agnostic triggers/grants + V13's `CREATE TABLE IF NOT EXISTS` + `sent_at` index then all apply cleanly.

## Out of Scope (DEFERRED — see "Remaining")

- **Getting outbound's full `integrationTest` suite green + wiring it into CI.** Fixing bugs 1+2 unmasked FURTHER independent pre-existing IT-infra breakages (layered behind the bean-override): `SagaSweeperIT`/others fail on `securityFilterChain` needing `HttpSecurity` under `@SpringBootTest(webEnvironment=NONE)` (SecurityConfig is not `@ConditionalOnWebApplication`); `IdempotencyFilterRedisIT` asserts `409` but gets `201` (servlet filter inactive in `NONE`). These are a separate web-environment IT overhaul (multiple design prongs + heavy Testcontainers verification), not in this task.
- Any domain/contract/ADR change.

# Acceptance Criteria

- [x] **AC-1** `OutboundServiceApplication` excludes `OutboxAutoConfiguration`; no `outboxPublisher` `BeanDefinitionOverrideException` on a non-standalone context load (verified — 6 outbound ITs now load+pass; remaining failures are the deferred web-env issues, NOT bean-override / NOT Flyway).
- [x] **AC-2** `V4`'s `tms_request_dedupe` matches the canonical `(request_id, sent_at, response_snapshot)` schema; the Flyway chain no longer fails with `column "sent_at" does not exist` (verified — no Flyway error in the post-fix IT run).
- [x] **AC-3** `:outbound-service:check` (the CI gate) green locally + CI "Build & Test (JDK 21, Linux)" 2m44s pass (20 checks total).
- [x] **AC-4** Diff confined to `OutboundServiceApplication.java` + `V4__init_packing_shipping_tables.sql` (+ task lifecycle). No domain/contract/ADR change.

# Remaining (deferred follow-up — needs a dedicated task)

Outbound's `integrationTest` suite has additional independent pre-existing failures (web-environment: security `HttpSecurity`/`NONE`, idempotency servlet filter, `TmsClientAdapterIT`). Wiring outbound `integrationTest` into CI requires fixing those first — a separate web-env IT overhaul. This task repairs only the startup + migration blockers (the prerequisite layer).

# Related Specs

- `libs/java-messaging` `OutboxAutoConfiguration` — the shared auto-config whose `outboxPublisher` `@Bean` collided.
- `outbound-service` `TmsRequestDedupeEntity` — the canonical `tms_request_dedupe` schema.

# Edge Cases

- `standalone` profile: outbound's `@Component OutboxPublisher` is `@Profile("!standalone")` → disabled there; the collision never arose under standalone (which is why unit/standalone paths passed). The exclude is safe for standalone too (outbound doesn't use the libs bean in any profile).
- V8 triggers/grants on `tms_request_dedupe` are column-agnostic → unaffected by the V4 schema change.

# Failure Scenarios

- Excluding `OutboxAutoConfiguration` would break outbound if it used a libs outbox bean — it does not (verified: own publisher/writer/repository).
- Editing an applied migration would be unsafe normally — but outbound has no deployed DB, so V4 has no applied checksum/state anywhere.

# Test Requirements

- AC-1/AC-2 verified by the post-fix outbound IT run (bean-override + Flyway errors eliminated; 6 ITs load+pass).
- AC-3: `:outbound-service:check` green locally + CI.

# Definition of Done

- [x] Bean exclude + V4 migration reconciled.
- [x] `:check` green; bean-override + Flyway errors eliminated (verified in IT run).
- [x] Diff confined; no domain/contract/ADR change.
- [x] Task md + `INDEX.md` updated, incl. the deferred IT-suite/CI follow-up.
- [x] Reviewed + merged (impl PR #1045 squash `23de0560`, 3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). BE-332 audit 의 "outbound CI 배선" 후속이 **outbound 가 non-standalone 에서 기동 불가**(빈 충돌 + 마이그레이션 충돌)임을 노출 — 미배포 + IT 미-CI 라 잠복했던 진짜 producer 버그. **메타: ① `@ConditionalOnMissingBean` 은 by-TYPE — 같은 빈 이름이라도 타입이 다르면 조건 미스 → 이름 충돌; 공유 auto-config 빈과 서비스 자체 빈이 이름 겹치면 exclude 또는 distinct-name 필요. ② 한 컨텍스트-로드 결함을 고치면 그 뒤에 가려져 있던 다음 결함이 드러남(layered) — outbound IT 는 startup→flyway→security→filter 다층; 본 task 는 startup+migration 전제층만 복구하고 web-env IT overhaul 은 분리(승인 범위 초과 발견 시 정직하게 재스코핑). ③ 미배포 서비스의 마이그레이션은 적용-상태가 없어 in-place 교정이 안전.**
