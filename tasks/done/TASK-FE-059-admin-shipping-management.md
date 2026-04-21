# Task ID

TASK-FE-059

# Title

admin-dashboard 배송 관리 — 배송 목록 조회 및 배송 상태 변경

# Status

review

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

관리자가 전체 배송 목록을 조회하고, 배송 상태를 단계별로 변경하며, 운송장 번호와 택배사 정보를 입력할 수 있다.

# Scope

## In Scope

- 배송 목록 페이지 (GET `/api/shippings`) — 페이지네이션, 상태 필터
- 배송 상태 변경 (PUT `/api/shippings/{shippingId}/status`)
- SHIPPED 전환 시 운송장 번호/택배사 입력 폼
- 라우트: `/shippings`

## Out of Scope

- 배송 생성 (OrderConfirmed 이벤트로 자동 생성)
- 외부 택배사 API 연동
- 배송 삭제
- 대량 상태 일괄 변경

# Acceptance Criteria

- [ ] 배송 목록이 페이지네이션과 상태 필터(PREPARING/SHIPPED/IN_TRANSIT/DELIVERED)로 표시된다
- [ ] 각 배송 항목에 주문 ID, 배송 상태, 택배사, 운송장 번호가 표시된다
- [ ] PREPARING → SHIPPED 전환 시 운송장 번호와 택배사 입력 모달이 표시된다
- [ ] SHIPPED → IN_TRANSIT, IN_TRANSIT → DELIVERED 전환이 버튼으로 가능하다
- [ ] 역방향 상태 전환은 불가하고 비활성 처리된다
- [ ] 상태 변경 시 확인 다이얼로그가 표시된다
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/shipping-service/overview.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/admin-dashboard/overview.md`
- `specs/features/order-processing.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/layered-by-feature.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/form-handling.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/shipping-api.md`

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- layered-by-feature 아키텍처에 따라 `features/shipping-management/` 디렉토리 구성
- 배송 상태 전환: PREPARING → SHIPPED → IN_TRANSIT → DELIVERED (단방향만 허용)
- SHIPPED 전환 시 trackingNumber, carrier 필수 입력
- 기존 order-management 패턴 참고 (목록 + 상태 변경 패턴 유사)
- 사이드바 네비게이션에 "배송 관리" 메뉴 추가
- 기존 ConfirmDialog, DataTable 등 공통 컴포넌트 활용

# Edge Cases

- 배송 건이 없는 경우 (빈 상태)
- 허용되지 않는 상태 전환 시도 (서버 400 에러)
- 운송장 번호 입력 없이 SHIPPED 전환 시도 (클라이언트 validation)
- 동시에 같은 배송 상태 변경 시도 (낙관적 잠금)

# Failure Scenarios

- API 호출 실패
- 상태 변경 시 validation 실패
- 잘못된 상태 전환 (400)
- 권한 없는 접근 (403)
- 타임아웃

# Test Requirements

- ShippingList 페이지 테스트 (목록, 필터링, 페이지네이션)
- ShippingStatusForm 컴포넌트 테스트 (상태 전환, 운송장 입력)
- 상태 전환 버튼 활성/비활성 테스트
- 에러/로딩/빈 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
