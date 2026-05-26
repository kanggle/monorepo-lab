# Task ID

TASK-SCM-BE-017

# Title

scm refactor sweep tail (post-BE-016, 2026-05-26 dry-run)

# Status

review

# Owner

backend

# Task Tags

- code

---

# Goal

2026-05-26 dry-run 에서 식별된 SCM 3 서비스의 **BE-016 sweep out-of-scope** 였던 잔재 4 finding (L0 systemic 1 + L6 잔여 중복 3) 을 단일 PR 로 정리. 외부 동작·계약·이벤트 envelope 변경 0. **behavior-neutral**.

BE-016 (`f62dd8b7`, 2026-05-25 머지) 는 L1 naming · L5 long-method · L6 가시 중복 · 1 bug fix 를 회수했으나, L0 inbound→outbound 직접 import 1 site / L6 `ActorContextResolver` 정적 호출 6 site / L6 `extractTenantId` 중복 2 site / L6 잔여 `Collectors.toList()` 1 site 가 BE-016 in-scope 밖이었음. 본 task 가 이를 tail-end 로 회수.

---

# Scope

## In Scope

| L | 대상 | 변경 |
|---|---|---|
| L0 | `apps/inventory-visibility-service/.../adapter/inbound/web/InventoryVisibilityController.java:7` `import ...adapter.outbound.cache.SnapshotAggregationCache` (inbound adapter → outbound adapter 직접 import, Hexagonal boundary 위반) | `application/port/outbound/SkuBreakdownCachePort` interface 신설 → `SnapshotAggregationCache` implements port → controller 가 port 또는 application service 통해서만 호출. X-Cache 헤더 책임 위치는 application service 의 cache-status enum (또는 `CachedResult<T>` wrapper) 반환으로 이동. controller 의 `adapter.outbound.cache.*` 직접 import 0 |
| L6 | `apps/procurement-service/.../presentation/controller/PurchaseOrderController.java:13,55,72,83,93,102,112` 의 `ActorContextResolver.currentOrThrow()` 정적 호출 6 site (presentation → infrastructure 정적 의존) | `ActorContextResolver` 를 `application/security/` (또는 동등 application layer) 로 이동 → 6 controller use-site 정리. 메서드 파라미터 / `@AuthenticationPrincipal` argument resolver / DI bean 호출 중 dispatcher 선택. 핵심 = `presentation → infrastructure` 정적 import 0 + `ActorContext` 획득 방식 단일 |
| L6 | `apps/inventory-visibility-service/.../adapter/inbound/web/InventoryVisibilityController.java:154` + `NodeStalenessController.java:40` 의 `private String extractTenantId(Jwt jwt)` byte-identical 중복 2 site | `adapter/inbound/web/TenantClaimExtractor` static util (Spring bean 아님) 도입 → 두 controller 의 `private extractTenantId` 0. claim 누락 시 throw type 보존 |
| L6 | `apps/procurement-service/.../presentation/controller/PurchaseOrderController.java:63` 의 `.collect(Collectors.toList())` 잔재 1 site | `.toList()` Java 16+ idiom 으로 단순화 + 불필요한 `import java.util.stream.Collectors` 제거 (다른 site 가 사용 중이면 import 유지) |

## Out of Scope

- `IdempotencyStore` / `ClockPort` dead port 제거 — architecture.md § Idempotency 문구 정리 동반, behavior-neutral 보다 spec hygiene 성격 → 별 audit task 후보 (BE-018 또는 refactor-spec)
- `application/service/` 패키지 위치 spec-drift (inventory-visibility architecture.md 가 `application/` 로만 기술, 실 코드는 `application/service/` 한 단계 더) — 1 줄 spec 수정 → 별
- L7 god-class 분할 — post-BE-016 size top: `PurchaseOrderApplicationService.java` 332 LOC / `PurchaseOrder.java` 265 / `GlobalExceptionHandler` 200 / `InventoryVisibilityApplicationService.java` 256 / `InventoryVisibilityController.java` 177 → 모두 681 LOC (auth-service `AdminActionAuditor`, BE-314) 임계 미달, cohesion 양호. 분할 가치 없음
- API / event contract / schema 변경
- `@AuthenticationPrincipalArgumentResolver` 도입 같은 framework idiom 강화 (선택 사항 — A2 의 단순 layer 이동만으로 spec violation 해소되므로 mechanical option 선호)
- libs/ 변경 (TenantClaimExtractor / port 모두 service-local 유지, `platform/shared-library-policy.md`)

---

# Acceptance Criteria

- [ ] (A1) `application/port/outbound/SkuBreakdownCachePort` interface 신설; `SnapshotAggregationCache` implements port; `InventoryVisibilityController` 가 `adapter.outbound.cache.*` 직접 import 0
- [ ] (A1) X-Cache 헤더 동작 보존 (HIT / MISS / UNAVAILABLE 3 case IT GREEN, response header byte-identical)
- [ ] (A2) `ActorContextResolver` 가 `application/security/` 또는 동등 application layer 로 이동; SCM 3 서비스의 `presentation → infrastructure` 정적 import = 0 (Grep 검증)
- [ ] (A2) `PurchaseOrderController` 6 method 의 `ActorContext` 획득 방식 단일 (방법 무관)
- [ ] (A3) `adapter/inbound/web/TenantClaimExtractor` static util 도입; `InventoryVisibilityController` + `NodeStalenessController` 의 `private String extractTenantId(Jwt)` = 0
- [ ] (A3) claim 누락 시 throw type 보존 (기존 동작 매핑 변경 0)
- [ ] (A4) `PurchaseOrderController.java:63` `Collectors.toList()` → `.toList()`
- [ ] `./gradlew :projects:scm-platform:apps:procurement-service:check :projects:scm-platform:apps:inventory-visibility-service:check :projects:scm-platform:apps:gateway-service:check` 3 service 모두 BUILD SUCCESSFUL
- [ ] contract / event schema 변경 0, public HTTP API 응답 shape 변경 0, X-Cache 헤더 enum byte-identical
- [ ] BE-016 회귀 0 (sampling: `*RepositoryImpl` rename 9 + `ALREADY_PAST_SUBMITTED` + `recordTransition` + `pageQuery.toPageable()` + `WebhookSignatureVerifier` + `WmsEnvelopeParser` + `applySnapshotDelta` + gateway `String.join` 모두 유지)

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0.

- `platform/refactoring-policy.md`
- `platform/naming-conventions.md`
- `platform/coding-rules.md`
- `platform/shared-library-policy.md` — port / util service-local 유지 근거
- `projects/scm-platform/specs/services/procurement-service/architecture.md` — Layer Structure + Forbidden Dependencies 확인 (impl 시점 정확 표현 매핑)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` — 동일 + port 카탈로그 확인
- `projects/scm-platform/specs/services/gateway-service/architecture.md` — baseline (in-scope 0건, `:check` 만 통과 확인)
- `projects/scm-platform/tasks/done/TASK-SCM-BE-016-refactor-sweep.md` — 선행 sweep, In Scope 회수 항목 (재카운트 금지)
- `rules/traits/integration-heavy.md` — webhook / cache fail-open 정합
- `rules/traits/transactional.md` — port 추출 시 transactional boundary 보존

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Move to Correct Layer / Reduce Duplication / Replace Pattern + § Baseline Check 의 unit+IT 양쪽 baseline 의무

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` — X-Actor-Type / X-Actor-Id header 처리는 controller signature 가 바뀌어도 wire-level invariance
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` — X-Cache 헤더 enum (HIT / MISS / UNAVAILABLE) byte-identical 보장
- (변경 0건)

---

# Target Services

- procurement-service (A2 + A4)
- inventory-visibility-service (A1 + A3)
- gateway-service (변경 0건, `:check` baseline 만 확인)

---

# Implementation Notes

- **A1 X-Cache 헤더 책임 위치 (dispatcher 선택)**:
  - 옵션 (i) `SkuBreakdownCachePort` 가 `CachedResult<T>` (status enum + value) 반환 → application service 가 status 를 결과 wrapper 에 포함 → controller 가 wrapper.status → header 매핑. single-port-call, 권장.
  - 옵션 (ii) port 는 `Optional<T>` 반환 + application service 가 fail-open catch 로 UNAVAILABLE 결정. 기존 동작에 더 가까움.
  - 어느 쪽이든 X-Cache 헤더 string ("HIT" / "MISS" / "UNAVAILABLE") 은 byte-identical 보장.
- **A2 ActorContextResolver 이동 (dispatcher 선택)**:
  - 옵션 (i, 권장 — mechanical) layer 이동 only — `infrastructure/security/ActorContextResolver` → `application/security/ActorContextResolver`. BE-016 의 `WebhookSignatureVerifier` 가 DI 패턴 정착시킨 일관성 회복 측면에서 정적 호출 → DI bean 호출로 동시 전환 권장 (controller field 1개 추가).
  - 옵션 (ii) Spring `@AuthenticationPrincipalArgumentResolver` 도입 + controller method 파라미터 바인딩 — framework idiom 강화, churn 큼. 본 task out-of-scope (별 polish task 후보).
- **A3 TenantClaimExtractor 위치**: `adapter/inbound/web/TenantClaimExtractor` — static util, Spring bean 아님. `TenantClaimEnforcer` filter 와 같은 패키지. 두 controller 단순 import. claim 누락 시 throw type / message 는 기존 동작 byte-identical 보존.
- **A4**: 단순 1 line. `Collectors` import 가 다른 site 에 남아있으면 import 유지, 없으면 제거.
- 메모리 `project_refactor_sweep_status` 의 GAP retrofit-era port-hoist (BE-300/301) 의 "adapter diff = delegation+mapping only / `@Override` 추가만 = port-hoist 最速 review 검증법" 답습. A1 도 동일 패턴.

---

# Edge Cases

- **A1**: `SnapshotAggregationCache` 의 Redis fail-open (현재: cache 예외 → UNAVAILABLE 응답) 동작이 port 시그니처로 표현될 때 controller 에서 catch 누락 가드 필요. application service 가 try/catch 흡수하면 controller 는 단순.
- **A2**: ActorContextResolver layer 이동 시 기존 `@ControllerAdvice` (`GlobalExceptionHandler`) 의 `ActorContextMissingException` 핸들러 import 경로 확인 — exception class 자체는 동일 패키지 유지 권장.
- **A3**: `extractTenantId` claim 누락 시 throw type/message 변경 → `TenantClaimEnforcer` 또는 `GlobalExceptionHandler` 매핑 영향. 두 controller 가 동일 throw 였는지 사전 확인 후 unify (BE-016 의 `verifySignature` 통합 패턴 답습).
- **A4**: import 정리 후 다른 file 의 `Collectors.toList()` 사용 여부 검증.

---

# Failure Scenarios

- **A1**: port adoption 잘못 시 X-Cache 헤더 contract 위반 (예: `HIT` 응답이 cache miss 후에도 발생) → 외부 모니터링 / 운영자 alerting 거짓 신호. 방어: 3 case IT (HIT / MISS / UNAVAILABLE) 모두 검증.
- **A2**: ActorContext 누락 시 401/403 mapping 일관성 깨지면 보안 결함 가능 (예: 인증 누락이 500 으로 매핑). 방어: WebMvcTest 또는 IT 에서 actor header missing 케이스 검증.
- **A3**: TenantClaimExtractor 의 throw type 변경 시 `tenant_id=scm` fail-closed gate 가 401 대신 5xx 반환 → boundary 약화. 방어: throw type/message 보존 단언 unit test + IT.
- **A4**: 무.

---

# Test Requirements

- 기존 단위 + IT 전부 통과
- **A1**: `InventoryVisibilityController` IT 또는 `SnapshotAggregationCacheIntegrationTest` 에서 X-Cache `HIT` / `MISS` / `UNAVAILABLE` 3 case 모두 byte-identical 응답 헤더 검증 (Redis fail-open 시뮬레이션 포함)
- **A2**: `PurchaseOrderController` WebMvcTest 또는 procurement IT 에서 ActorContext 정상/누락 path 양쪽 검증 (BE-016 의 search() sort 검증 패턴 답습)
- **A3**: `TenantClaimExtractor` unit test (claim 존재 / 누락 양 case + throw type/message byte-identical 단언)

Test command:

```
./gradlew :projects:scm-platform:apps:procurement-service:check :projects:scm-platform:apps:inventory-visibility-service:check :projects:scm-platform:apps:gateway-service:check
```

CI verification (impl PR 시): `Integration (scm-platform, Testcontainers)` job + scm E2E smoke 가 결정적 신호 (BE-016 PR #817 CI 패턴 답습 — local Rancher Desktop multi-class IT 비용 회피, [`feedback_spring_boot_diagnostic_patterns`] 의 `BE-303 CI 결과 차원` 메타 적용).

---

# Definition of Done

- [ ] 4 변경 항목 (A1 / A2 / A3 / A4) 전부 구현
- [ ] 3 service `:check` BUILD SUCCESSFUL (local + CI)
- [ ] X-Cache 헤더 3 case IT GREEN
- [ ] `presentation → infrastructure` 정적 import = 0 (Grep 검증)
- [ ] commit + push (impl PR)
- [ ] Ready for review

---

# Recommendation Metadata

- 분석 = Opus 4.7 (2026-05-26 dry-run, refactoring-engineer agent dispatch)
- 구현 권장 = **Sonnet 4.6** (4 unit 모두 mechanical Move-to-Correct-Layer + Reduce Duplication, 도메인 판단 없음; A1 의 port 시그니처 옵션 (i)/(ii) 만 dispatcher 선택)
- 권장 PR 수 = **1 PR** (A1 + A2 + A3 + A4 묶음, ≈4 unit, -25 ~ +20 LOC 추정)
- cohort = LEAN (HEAVY ≥ 10 unit / -100 LOC 미달, MID 5-9 unit / -50 LOC 미달)
- base rate 정직 신호: BE-016 직후 + day-one Hexagonal cohort → 추가 sweep 가치 낮음. 단 L0 1 site + 정적 의존 6 site 일관성 결함 = `WebhookSignatureVerifier` DI 패턴 정착 vs `ActorContextResolver` 정적 호출 불균형 해소가 정당화 근거.
