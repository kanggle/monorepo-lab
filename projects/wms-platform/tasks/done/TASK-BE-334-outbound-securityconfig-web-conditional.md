# Task ID

TASK-BE-334

# Title

`outbound-service` — make `SecurityConfig` `@ConditionalOnWebApplication(SERVLET)` so a non-web `@SpringBootTest(webEnvironment=NONE)` context can load. The `securityFilterChain` bean depends on `HttpSecurity` (servlet-web only); without the condition, every non-web outbound IT fails to load its context (`No qualifying bean of type HttpSecurity`). This is **step 1** of the deferred outbound IT-suite repair (the prerequisite context-load layer); production security is unchanged.

# Status

done

# Owner

backend-engineer (one annotation on `SecurityConfig` — no behavior change in production / web contexts)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **follows**: TASK-BE-333 (outbound non-standalone startup + migration repair). After BE-333 fixed the bean-override + Flyway blockers, re-running outbound's `@SpringBootTest` ITs unmasked the NEXT context-load layer: `securityFilterChain` needs `HttpSecurity`, absent under `webEnvironment=NONE`.
- **root cause**: `SecurityConfig` (`@Configuration @EnableMethodSecurity`) defines `SecurityFilterChain securityFilterChain(HttpSecurity http, …)`. `HttpSecurity` is only present in a servlet web application context. The outbound IT base (`OutboundServiceIntegrationBase`) uses `@SpringBootTest(webEnvironment = NONE)` (the saga / TMS ITs only autowire domain beans, no HTTP), so the full app context loads SecurityConfig but has no `HttpSecurity` → `UnsatisfiedDependencyException`.
- **fix**: `@ConditionalOnWebApplication(type = SERVLET)` on `SecurityConfig`. Production runs as a servlet web app → condition true → security unchanged. Non-web contexts (NONE ITs) skip it cleanly. Verified: `:check` green (web-slice/@WebMvcTest tests still load SecurityConfig in their web context); the `securityFilterChain`/`HttpSecurity` context-load failure is eliminated (SagaSweeperIT/TmsClientAdapterIT now progress PAST context-load).
- **no dependency on**: any contract/ADR/domain change.

---

# Goal

A non-web outbound `@SpringBootTest` context loads without a `securityFilterChain`/`HttpSecurity` failure, unblocking the context-load layer for outbound's ITs. Production + web-slice security behaviour unchanged.

# Scope

## In Scope

`projects/wms-platform/apps/outbound-service/.../config/SecurityConfig.java` — add `@ConditionalOnWebApplication(type = SERVLET)` (+ import).

## Out of Scope (DEFERRED — comprehensive outbound IT-suite repair)

Getting outbound's `integrationTest` suite green + wiring it into CI is **explicitly deferred** (user decision 2026-06-03). Fixing the context-load layers (BE-333 bean-override + Flyway, BE-334 security) unmasked FURTHER independent per-test issues — the suite was never run, so each test has un-validated setup:
- **Test-data FK violations**: `SagaSweeperIT.seedStuckSaga` / `TmsClientAdapterIT.seedShipmentRow` insert `outbound_saga`/`shipment` rows whose `order_id` FK has no parent `outbound_order` row → `insert or update … violates foreign key constraint`.
- **`IdempotencyFilterRedisIT` functional**: `sameKeyDifferentBody_returns409` asserts `409` but gets `201` (a real filter-logic or test-isolation issue; this test is NOT `@SpringBootTest` — it constructs the filter directly over Testcontainers Redis).
- …and likely more layers behind those.

outbound-service is **not deployed in any running stack** (absent from the federation/demo/CI E2E) → full IT-green + CI-gating is a **low-ROI, large, test-by-test repair** best done as a dedicated effort if/when outbound is slated for deployment. This task lands ONLY the production-correct security-conditional prerequisite.

# Acceptance Criteria

- [x] **AC-1** `SecurityConfig` is `@ConditionalOnWebApplication(type = SERVLET)`; a `webEnvironment=NONE` `@SpringBootTest` no longer fails with `No qualifying bean of type HttpSecurity` (verified — SagaSweeperIT/TmsClientAdapterIT now load context + progress to the deferred test-data FK layer).
- [x] **AC-2** `:outbound-service:check` green locally + CI "Build & Test (JDK 21, Linux)" 2m26s (19 checks pass) — web-slice/unit tests unaffected; production security unchanged.
- [x] **AC-3** Diff confined to `SecurityConfig.java` (+ task lifecycle). No contract/ADR/domain change.

# Remaining (deferred follow-up — dedicated task, low priority)

Full outbound `integrationTest` green + CI wiring: fix the per-test FK setup (`SagaSweeperIT`/`TmsClientAdapterIT` need parent `outbound_order` seeds), diagnose `IdempotencyFilterRedisIT` 409, resolve any further layers, then add outbound `integrationTest` to CI. Deferred — outbound is undeployed (low ROI until deployment).

# Related Specs

- `outbound-service` `architecture.md` § Security — the filter chain this conditionally scopes to web.

# Edge Cases

- Production (servlet web) → condition true → security + method-security unchanged.
- `@WebMvcTest` slices (servlet web) → SecurityConfig loads in their web context → unchanged.
- `webEnvironment=NONE` ITs → SecurityConfig (incl. `@EnableMethodSecurity`) skipped → domain-bean autowiring works without an auth context (intended for saga/TMS ITs).

# Failure Scenarios

- If a non-web context genuinely needed method-security enforcement, skipping it would matter — but the NONE ITs call services directly without auth and do not test authz. Web/method-security tests run in a web context (condition true).

# Test Requirements

- `:outbound-service:check` green.
- AC-1 verified by the post-fix IT run (securityFilterChain/HttpSecurity error gone).

# Definition of Done

- [x] `@ConditionalOnWebApplication(SERVLET)` on `SecurityConfig`.
- [x] `:check` green; securityFilterChain context-load failure eliminated (verified in IT run).
- [x] Diff confined; no contract/ADR/domain change.
- [x] Task md + `INDEX.md` updated (incl. the deferred comprehensive IT-suite repair).
- [x] Reviewed + merged (impl PR #1047 squash `fe9607b6`, 3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). BE-333 후속 — outbound IT 의 다층 컨텍스트-로드 결함 중 **security 층만** production-correct 하게 해소(`@ConditionalOnWebApplication`). 사용자 결정(2026-06-03): full IT-green+CI 는 **미배포 서비스의 저-ROI 대형 재작업**이라 보류; 본 task 는 전제층 한 줄만 landing. **메타: 컨텍스트-로드 결함의 다층성(bean-override→flyway→security→test-data FK→functional) — 미배포·미-CI 서비스는 전 레이어가 미검증이라 "green 만들기"가 사실상 IT 재작성; ROI 판단 후 전제층만 incremental landing 하고 나머지는 정직하게 deferred.**
