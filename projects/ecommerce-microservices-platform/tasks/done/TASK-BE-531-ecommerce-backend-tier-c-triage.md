# Task ID

TASK-BE-531

# Title

ecommerce backend Tier C triage — payment confirm() Extract Method (+ 4 items skipped/deferred with rationale)

# Status

done

# Owner

backend

# Task Tags

- code
- refactor

# Goal

12-서비스 백엔드 리팩토링 스캔의 **Phase 3 — Tier C**(행위 민감 / 개별 안전망 필요 후보). 착수 시 각 항목을 **behavior-preserving refactor 인지** 재검증한 결과, **payment `confirm()` escalation Extract Method 1건만** 실질 refactor로 확정·실행하고 나머지 4건은 아래 근거로 미실행. Tier A(BE-529)·Tier B(BE-530)로 이커머스 백엔드 리팩토링 스윕 종료.

# Scope

## In Scope (실행 — category=long-method/Extract Method)

- **payment-service** `PaymentConfirmService.confirm()`: post-capture 스트랜드-리펀드 escalation 블록(4-depth 중첩)의 record-or-log 로직을 `escalateStrandedRefund(orderId, latest, paymentKey, cause)` private 헬퍼로 추출. `throw new PaymentAlreadyCompletedException`은 caller에 잔류(제어흐름 가시). recorder는 이미 별도 bean(REQUIRES_NEW) → self-invocation 트랩 없음. 호출 순서·side-effect timing·F1 fail-open 로그 불변. `PaymentConfirmServiceTest` 무수정 통과, durability IT가 CI 게이트.

## Out of Scope (재검증 후 미실행 — 항목별 근거)

- **order cancel-tail dedup**(3 cancellation service): **false-dup**. tail이 실제로 갈라짐 — `order.cancel(CancelReason.OPERATOR, clock)` vs `order.cancel(clock)`(다른 도메인 오버로드), `OrderCancelledEvent.of(…, CancelReason.OPERATOR, clock)` vs `of(…, clock)`(다른 이벤트 오버로드), metric 라벨·로그 상이. 공유 헬퍼는 거의 전부 파라미터화 + outbox refund/coupon fan-out 경로 med-risk → 미실행.
- **settlement `SettlementPeriod`/`SellerPayout` domain↔JPA-entity split**: **명문화된 의도적 선택**. `SettlementPeriod` javadoc 자체가 *"JPA annotations are the allowed domain↔framework exception"* 라 선언(mutating aggregate + `@Version` 낙관락). 명백한 layer 위반 아님 → "고치기"는 코드 SoT 에 반하고 `@Version`/컬럼/enum 매핑 건드리는 behavior-risk. refactor 아니라 ADR 사안.
- **product `WmsInventoryReconciliationService` inline clamp → `Inventory` aggregate**: **behavior-preserving 아님**. aggregate 로 옮기면 full-row `create` vs managed-entity 변경 → `@Version`/emitted-delta 의미 변화. 설계 결정(spec) 필요, 기계적 refactor 범위 밖.
- **gateway `SwaggerAggregationConfig` 10-route 데이터화**: 이득 modest(prod-off config, `@ConditionalOnProperty` OFF 기본) + agent 명시 test-first 인데 `RouteLocator` 테스트 선례 0 → Spring 컨텍스트 슬라이스/취약 mock 필요, 비용 과다. 미검증 config refactor 회피 → 보류.

# Acceptance Criteria

- [ ] **AC-1** `PaymentConfirmService.confirm()` escalation 블록이 `escalateStrandedRefund` 헬퍼로 추출되고, 호출 순서/예외 타입/메시지/metric/로그(F1 포함) 불변.
- [ ] **AC-2** payment 모듈 `compileTestJava` 0, `PaymentConfirmServiceTest` 무수정 통과.
- [ ] **AC-3** CI(Linux) 전 레인 GREEN(durability IT 포함).
- [ ] **AC-4** 미실행 4건은 본 task 에 근거 기록(재착수 시 이 triage 가 출발점).

# Related Specs

- `platform/refactoring-policy.md`
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`

# Related Contracts

- N/A — 내부 헬퍼 추출, API·이벤트 계약 무변경.

# Edge Cases

- multi-catch `PgGatewayUnavailableException | PgConfirmFailedException e` → 헬퍼 param `RuntimeException cause`(둘 다 unchecked, 컴파일 검증).
- no-race 경로(row 여전히 PENDING)는 escalation 미진입 → 성공 경로 byte-불변.

# Failure Scenarios

- 추출이 side-effect 순서/타이밍을 바꾸면 durability IT(`PaymentRefundStrandedDurabilityIntegrationTest` 등)가 CI 에서 포착.

# Test Requirements

- payment compileTestJava GREEN + `PaymentConfirmServiceTest` 통과.
- CI-Linux 전 레인 GREEN.

# Definition of Done

- [ ] payment confirm() Extract Method 커밋, 미실행 4건 근거 기록
- [ ] CI GREEN, 3-dim 검증, 머지, worktree 정리
- [ ] 이커머스 백엔드 리팩토링 스윕(Tier A/B/C) 종료
