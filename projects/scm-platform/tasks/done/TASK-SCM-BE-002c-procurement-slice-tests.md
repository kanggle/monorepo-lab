# Task ID

TASK-SCM-BE-002c

# Title

procurement-service slice tests — `@WebMvcTest` controllers + `@DataJpaTest` repos (TASK-SCM-BE-002b Phase 4)

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-SCM-BE-002b](../done/TASK-SCM-BE-002b-procurement-test-pyramid.md) 가 Phase 1 (domain unit 64) + Phase 2 (application unit 16) + Phase 3 (GlobalExceptionHandler 21) = **101 tests** 머지 (PR #243/#244/#245). Phase 4 — slice tests — 본 task 가 추가.

Phase 4 의 핵심 challenge: `PurchaseOrderController` / `AsnWebhookController` / `SupplierAckWebhookController` 가 `ActorContextResolver.currentOrThrow()` 정적 메서드로 `SecurityContextHolder` 에서 `ActorContext` 를 추출. `@WithMockUser` 로는 principal 타입 불일치 → `TestingAuthenticationToken` 으로 직접 SecurityContext populate 패턴 필요.

---

# Scope

## In Scope

### `@WebMvcTest` for 3 controllers (≥ 12 tests)

- **PurchaseOrderController** (5 endpoints): draft / get / search / submit / confirm / cancel
  - happy path (201/200) + 401 (no auth) + Idempotency-Key 누락 (400 IDEMPOTENCY_KEY_REQUIRED) + 4xx domain exception (PoNotFoundException → 404)
- **AsnWebhookController**: ASN 수신 + 중복 supplierAsnRef
- **SupplierAckWebhookController**: ack 수신 + 중복

각 테스트 패턴:
1. `SecurityContextHolder.getContext().setAuthentication(testActorToken)` — `TestingAuthenticationToken` carrying `ActorContext` as principal
2. `MockMvc.perform(...).andExpect(status().is...())` 로 status + ApiEnvelope 검증
3. `@MockBean PurchaseOrderApplicationService` mock + verify

### `@DataJpaTest` for 5 repos (≥ 5 tests)

- Supplier / PurchaseOrder / ASN / AuditLog / PoStatusHistory JPA repos — H2 in-memory + Flyway V1 적용
- 멀티테넌트 isolation (`findById(id, tenantId)`) 검증
- append-only invariant — H2 trigger / constraint 적용 가능 여부 (H2 vs MySQL 차이)

**참고**: V1__init.sql 가 MySQL-specific syntax (TRIGGER, JSON column 등) 사용 시 H2 호환 plan 필요. 호환 안 되면 JPA slice 는 IT 로 통합 (TASK-SCM-BE-002d).

## Out of Scope

- Testcontainers IT (TASK-SCM-BE-002d 영역 — Docker fix 후)
- production code 수정 (002b 와 동일 정책 — 명백한 버그 발견 시 small fix 만 별 commit)

---

# Acceptance Criteria

1. `@WebMvcTest` 3 controllers ≥ 12 tests PASS
2. `@DataJpaTest` 5 repos ≥ 5 tests PASS (또는 H2 호환 검증 결과 IT 분리 명시)
3. `:procurement-service:check` PASS
4. CI Build & Test PASS

---

# Related Specs

- [TASK-SCM-BE-002b](../done/TASK-SCM-BE-002b-procurement-test-pyramid.md) — 직접 선행
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/contracts/http/procurement-api.md`

---

# Implementation Notes

- `TestingAuthenticationToken` 으로 SecurityContextHolder 패턴 — fan-platform / GAP 의 기존 slice test 답습 가능
- `@WebMvcTest(PurchaseOrderController.class)` + `@MockBean PurchaseOrderApplicationService` + `@MockBean ActorContextResolver` (또는 SecurityContext 직접 setup)
- Spring Security AutoConfiguration 차단 시 `@AutoConfigureMockMvc(addFilters=false)` 또는 명시적 SecurityFilterChain mock

---

# Edge Cases

1. **ActorContextResolver 정적 호출** — `SecurityContextHolder` 직접 mock vs `MockedStatic<ActorContextResolver>` (Mockito-inline) 중 선택. 후자가 더 명시적.
2. **H2 Flyway 호환** — V1__init.sql 의 MySQL-specific syntax 시 H2 dialect override 필요 또는 JPA slice 보류.
3. **Idempotency-Key 검증** — `MissingRequestHeaderException` 매핑 확인 (이미 GlobalExceptionHandlerTest 가 unit-level 검증).

---

# Failure Scenarios

## A. H2 Flyway 호환 안 됨

V1__init.sql 의 MySQL syntax (TRIGGER 등) 가 H2 에서 fail → `@DataJpaTest` slice 는 보류, TASK-SCM-BE-002d (IT) 에서 통합 검증.

## B. WebMvcTest 의 SecurityContext mocking 복잡

기존 monorepo 의 slice test 패턴이 일관되지 않으면 `MockedStatic` 으로 `ActorContextResolver.currentOrThrow()` 직접 stub.

---

# Definition of Done

- [ ] WebMvcTest 3 controllers + DataJpaTest (가능하면) 작성
- [ ] `:procurement-service:test` 추가 ≥ 12 tests PASS
- [ ] CI Build & Test PASS
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — slice test 패턴 deterministic.
- **분량**: medium (3 controllers + 5 repos).
- **dependency**:
  - `선행`: TASK-SCM-BE-002b (이미 done — 101 tests 머지).
  - `병렬 가능`: TASK-SCM-BE-002d (Testcontainers IT, Docker fix 후).
- **Docker 무관** — `@WebMvcTest` + H2 만 사용.
