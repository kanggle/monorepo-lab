# Task ID

TASK-FE-055

# Title

web-store 배송 추적 — 주문 상세에서 배송 상태 조회 및 추적 정보 표시

# Status

review

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

고객이 주문 상세 페이지에서 해당 주문의 배송 상태, 운송장 번호, 택배사 정보를 확인하고 배송 진행 상황을 추적할 수 있다.

# Scope

## In Scope

- 주문 상세 페이지에 배송 추적 섹션 추가 (GET `/api/shippings/orders/{orderId}`)
- 배송 상태 표시 (PREPARING → SHIPPED → IN_TRANSIT → DELIVERED)
- 운송장 번호 및 택배사 정보 표시
- 배송 상태별 시각적 진행 표시 (스텝 인디케이터)

## Out of Scope

- 외부 택배사 추적 페이지 연동 (외부 링크만 제공)
- 배송 알림 설정 (notification에서 처리)
- 관리자 배송 상태 변경 (admin-dashboard에서 별도 처리)

# Acceptance Criteria

- [ ] 주문 상세 페이지에서 배송 상태가 스텝 인디케이터로 표시된다
- [ ] SHIPPED 이후 운송장 번호와 택배사가 표시된다
- [ ] DELIVERED 상태에서 배송 완료 일시가 표시된다
- [ ] 배송 정보가 아직 없는 주문(PREPARING 이전)에 대한 안내 메시지가 표시된다
- [ ] 로딩/에러 상태가 처리된다
- [ ] 컴포넌트 테스트가 작성된다

# Related Specs

- `specs/services/shipping-service/overview.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/web-store/overview.md`
- `specs/features/order-processing.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/shipping-api.md`

# Target App

- `apps/web-store`

# Implementation Notes

- 기존 주문 상세 페이지(`(store)/my/orders/[id]`)에 배송 추적 섹션 통합
- 별도 feature 디렉토리가 아닌 기존 `features/order/` 하위에 shipping 관련 컴포넌트 추가
- 배송 상태 4단계: PREPARING → SHIPPED → IN_TRANSIT → DELIVERED
- 배송 정보가 없는 경우(404) "배송 준비 중" 안내 표시
- 주문 소유자만 조회 가능 — 서버에서 검증, 프론트는 403 핸들링

# Edge Cases

- 배송 정보가 아직 생성되지 않은 주문 (404)
- 취소된 주문의 배송 정보 조회
- 운송장 번호 없는 PREPARING 상태
- 배송 완료 후 조회

# Failure Scenarios

- API 호출 실패
- 권한 없는 접근 (403)
- 타임아웃
- 배송 정보 없음 (404)

# Test Requirements

- ShippingTracker 컴포넌트 테스트 (상태별 렌더링)
- 배송 정보 없는 경우 테스트
- 에러/로딩 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
