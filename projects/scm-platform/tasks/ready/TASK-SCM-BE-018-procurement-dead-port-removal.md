# Task ID

TASK-SCM-BE-018

# Title

procurement-service dead port removal — `IdempotencyStore` + `ClockPort` + `ClockConfig`

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Goal

`/refactor-code scm-platform/procurement-service` (2026-05-26) dry-run 에서 verify 된 **TRUE DEAD port 3 파일** 제거 — `IdempotencyStore` (사용처 0) + `ClockPort` (Bean 정의 외 inject site 0) + `ClockConfig` (`Clock` + `ClockPort` 둘 다 inject site 0). 외부 동작·계약·이벤트 envelope 변경 0. **behavior-neutral**.

TASK-SCM-BE-017 (`f62dd8b7`, 2026-05-26 머지) 의 § Out of Scope 항목이었음:

> `IdempotencyStore` / `ClockPort` dead port 제거 — architecture.md § Idempotency 문구 정리 동반, behavior-neutral 보다 spec hygiene 성격 → 별 audit task 후보 (BE-018 또는 refactor-spec)

본 task 가 그 약속을 closure. `/refactor-code` 의 "no behavior change" 원칙 부합. spec narrative 본문 정정 (§ Idempotency L441-455 + § Forbidden Patterns + § Edge Cases) 은 forward-compatible 보존 측면에서 **out of scope** — 별 `/refactor-spec` cycle 후보.

---

# Scope

## In Scope

| 대상 | 변경 |
|---|---|
| `apps/procurement-service/src/main/java/com/example/scmplatform/procurement/application/port/outbound/IdempotencyStore.java` | 삭제 (사용처 0 verify) |
| `apps/procurement-service/src/main/java/com/example/scmplatform/procurement/application/port/outbound/ClockPort.java` | 삭제 (inject site 0 verify) |
| `apps/procurement-service/src/main/java/com/example/scmplatform/procurement/infrastructure/config/ClockConfig.java` | 삭제 (`Clock` + `ClockPort` Bean 둘 다 inject site 0 — 전체 파일 dead) |
| `projects/scm-platform/specs/services/procurement-service/architecture.md:L149` (`IdempotencyStore.java ← Redis-or-DB dedupe port` tree 줄) | 제거 |
| `projects/scm-platform/specs/services/procurement-service/architecture.md:L150` (`ClockPort.java` tree 줄) | 제거 |

## Out of Scope

- **`IdempotencyKeyMismatchException`** — `domain/error/IdempotencyKeyMismatchException.java` 자체와 그 `GlobalExceptionHandler.@ExceptionHandler` mapping + `GlobalExceptionHandlerTest` case 보존 (current production throw site = 0 이지만, forward-compatible 측면에서 admin-events.md L26 enum entry 보존 패턴 (BE-316 Option A) 재사용 — future `IdempotencyStore` re-introduction 시 mapping/test 재활용 가능).
- **`idempotency_keys` 테이블** — Flyway `V1__init.sql` 의 schema 정의 + `data-model.md` 의 schema spec 보존 (future 재활성화 가능성).
- **`architecture.md § Idempotency` (L441-455) 본문 narrative** — "REST endpoint `Idempotency-Key` header validated by `IdempotencyStore` port — Redis primary + DB fallback" 산문이 production code 와 충돌하는 spec drift. **별 `/refactor-spec` mini-task 후보** (BE-316 Option A 패턴 재사용: 본문 정정 + forward-compatible 명시). 본 task 의 scope 밖.
- API / event contract / schema 변경
- 다른 service (inventory-visibility / gateway) 변경 0건, `:check` baseline 만 확인.
- libs/ 변경 (3 파일 모두 service-local).

---

# Acceptance Criteria

- [ ] (A1) `IdempotencyStore.java` + `ClockPort.java` + `ClockConfig.java` 3 파일 삭제. `apps/procurement-service/src/main/` 의 `IdempotencyStore` grep = 0; `ClockPort` grep = 0; `ClockConfig` grep = 0.
- [ ] (A2) `apps/procurement-service/src/test/` 의 `IdempotencyStore` / `ClockPort` / `ClockConfig` import grep = 0 (test 에서도 사용 안 함 verify).
- [ ] (A3) `architecture.md` Layer Structure tree 의 `IdempotencyStore.java ← Redis-or-DB dedupe port` (L149) + `ClockPort.java` (L150) 두 줄 제거.
- [ ] (A4) `architecture.md` 의 다른 § (§ Idempotency 본문 / § Forbidden Patterns / § Edge Cases) **변경 0** — Out of Scope 인 spec narrative drift 는 별도 task.
- [ ] (A5) `IdempotencyKeyMismatchException.java` + `GlobalExceptionHandler.@ExceptionHandler(IdempotencyKeyMismatchException.class)` + `GlobalExceptionHandlerTest` 의 IdempotencyKeyMismatchException case **byte-unchanged** (Out of Scope).
- [ ] (A6) `Flyway V1__init.sql` 의 `idempotency_keys` table schema **byte-unchanged** (Out of Scope).
- [ ] (A7) `./gradlew :projects:scm-platform:apps:procurement-service:check` BUILD SUCCESSFUL — 단위 + slice + IT 모두 GREEN. baseline = main `ff5568d7` (or current main).
- [ ] (A8) `./gradlew :projects:scm-platform:apps:inventory-visibility-service:check :projects:scm-platform:apps:gateway-service:check` BUILD SUCCESSFUL (회귀 0 verify).
- [ ] (A9) BE-017 회귀 0 (4 AC 모두 보존): `SkuBreakdownCachePort` 존재 / `ActorContextResolver` 의 application/security/ 위치 / `TenantClaimExtractor` static util / `PurchaseOrderController:63` `.toList()`.
- [ ] (A10) contract / event schema 변경 0, public HTTP API 응답 shape 변경 0, X-Cache 헤더 enum byte-identical.
- [ ] (A11) zero-retrofit invariant — `git diff --stat origin/main -- 'projects/{wms,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/' 'libs/'` = empty.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 (PROJECT.md = scm, domain=scm, traits=[transactional, integration-heavy, batch-heavy]).

- `platform/refactoring-policy.md` — refactor 의 정의 (no behavior change).
- `platform/coding-rules.md` — dead-code 정책.
- `platform/shared-library-policy.md` — 3 파일이 service-local 인 점 보존 (libs/ 영향 0).
- `projects/scm-platform/specs/services/procurement-service/architecture.md` — Layer Structure (L52-153) 트리 의 2 줄 (L149/L150) 만 정정. § Idempotency 본문 변경 안 함 (Out of Scope).
- `projects/scm-platform/tasks/done/TASK-SCM-BE-017-refactor-sweep-tail.md` § Out of Scope L44 — 본 task 의 발행 근거.
- `rules/traits/transactional.md` T1 — idempotency 본문은 spec 으로 유지 (production 실제 wire 는 future task).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Dead Code Removal pattern + § Baseline Check (compile + test GREEN before/after).

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` — wire-level 변경 0 (3 파일 dead 이므로 inbound endpoint 행동 무관).
- (변경 0건)

---

# Target Service

- `procurement-service` (A1 + A2 + A3 + A4)

회귀 verify (변경 0건):

- `inventory-visibility-service` (A8 baseline)
- `gateway-service` (A8 baseline)

---

# Architecture

scm-platform = Hexagonal (Ports & Adapters). 본 task 는 **unused outbound port + unused configuration class 삭제**.

- `IdempotencyStore` = application/port/outbound — interface 정의만 있고 구현 + caller 없음. Hexagonal 정신상 사용처 없는 port 는 dead.
- `ClockPort` = application/port/outbound — interface 정의 + ClockConfig Bean 정의 있으나 inject 받는 use-case 없음. 동일 dead.
- `ClockConfig` = infrastructure/config — `Clock` Bean + `ClockPort` Bean 둘 다 inject 받는 곳 없음. 전체 파일 dead.

architecture.md Layer Structure tree 의 2 줄 (L149/L150) 은 위 port 가 *expected to exist* 였음을 의미하지만, 실제 wire 안 되어 spec ↔ code mismatch. Tree 의 2 줄 제거로 spec ↔ code 정렬. § Idempotency 본문 (L441-455) 은 별 task 가 정정 — 본 task 는 mechanical dead-code removal 만.

---

# Implementation Notes

1. **Pre-verify (BE-301 패턴)**: impl 단계에서 dispatcher main session 이 직접 `grep IdempotencyStore | ClockPort | ClockConfig` 재실행 + `:check --rerun-tasks` 재실행 — agent report 의 숫자/주장 불신, 직접 재검증.
2. **Compile order**: 3 파일을 한꺼번에 `Bash rm` (또는 `git rm`) 으로 삭제. Spring Boot 가 ApplicationContext 시작 시 dangling reference 없음 verify (`./gradlew :procurement-service:bootJarMainClassName` 또는 `:test` 의 `@SpringBootTest` slice).
3. **architecture.md tree edit**: 단일 `Edit` 으로 2 줄 한꺼번에 제거. 다른 § (Idempotency 본문 / Forbidden Patterns / Edge Cases) 의 `IdempotencyStore` / `ClockPort` 참조 grep — present 면 본 task 의 Out of Scope (별 task) — 단 architecture.md L448 § Idempotency 본문 의 `IdempotencyStore` port 참조는 의도된 spec drift 잔존 (별 task author 가 정정).
4. **Branch**: `task/scm-be-018-procurement-dead-port-removal` (substring `master` 없음 확인 ✓).
5. **Spec PR + impl PR + close-chore PR** 3 분리 (PR Separation Rule).
6. (분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — mechanical file deletion + 2-line tree edit, low risk)

---

# Edge Cases

- `IdempotencyKeyMismatchException` 의 `IdempotencyStore` javadoc 참조 (`{@link com.example.scmplatform.procurement.domain.error.IdempotencyKeyMismatchException}` from `IdempotencyStore.java`) — 본 task 가 `IdempotencyStore` 삭제 시 자연 해소.
- `ClockConfig.java` 안의 `Clock clock()` Bean (`Clock.systemUTC()`) 가 `ClockPort` 외 다른 곳 (e.g. JPA auditing) 에서 inject 받는지 verify — grep 검증 필요. 받는 site 있으면 본 task 의 Out of Scope (별 task) — 본 verify 결과 receiver 0 였으므로 안전.
- `IdempotencyStore.StoredResponse` record (nested) 사용처 — grep 검증 = 0 verify.

---

# Failure Scenarios

- Compile fail (예상 가능성 낮음 — 사용처 0 verify 했으므로 dangling reference 없음). 만약 fail 시 revert + Investigation.
- Test fail — `@MockBean ClockPort` 같은 hidden test fixture 가 있을 가능성 미리 grep verify. 있으면 함께 정리 or 본 task scope 재정의.
- architecture.md 의 § Forbidden Patterns 가 dead port 를 "must exist" 로 referencing — verify 후 본 task scope 재정의 또는 별 task 분리.
- 회귀 (BE-017 4 AC) — `:check` GREEN 전체로 cover.

---

# Test Requirements

- baseline: main `ff5568d7` (BE-316 close-chore 직후) `./gradlew :projects:scm-platform:apps:procurement-service:check` GREEN.
- post-impl: 동일 명령어 GREEN. 추가 test 작성 불요 (dead code 제거 = 새 동작 도입 없음).
- 3 service `:check` baseline 와 동일.
- CI Linux runner 가 Testcontainers IT 의 권위적 verify (per `project_testcontainers_docker_desktop_blocker.md`).

---

# Definition of Done

- [ ] (A1-A11) 모두 PASS.
- [ ] Branch: `task/scm-be-018-procurement-dead-port-removal` (substring `master` 검증).
- [ ] PR: `refactor(scm-procurement):` impl PR + close-chore PR (PR Separation Rule).
- [ ] Lifecycle: `ready/` → `review/` → `done/`.
- [ ] BE-303 3-dim verify ALL GREEN per stage.
- [ ] (분석=Opus 4.7 / 구현 권장=Sonnet 4.6)
