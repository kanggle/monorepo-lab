# Task ID

TASK-FE-020

# Title

주문 목록 status 필터 미동작 수정 — 계약 업데이트 및 클라이언트 필터 연동

# Status

done

# Owner

frontend

# Task Tags

- code
- api

# Goal

TASK-FE-019 리뷰에서 발견된 주문 목록 status 필터 미동작 이슈를 수정한다. `useOrders` 훅에서 `status`가 queryKey에만 포함되고 실제 API 호출에 전달되지 않는 문제를 해결한다. order-api 계약에 `status` 쿼리 파라미터를 추가하고, 타입과 API 클라이언트를 업데이트한다.

# Scope

## In Scope

- `specs/contracts/http/order-api.md`: GET /api/orders 쿼리 파라미터에 `status` (optional) 추가
- `packages/types/src/order.ts`: 주문 목록 조회용 파라미터 타입 추가 (OrderListParams)
- `packages/api-client/src/services/order-api.ts`: getOrders에 status 파라미터 전달
- `apps/admin-dashboard/src/features/order-management/hooks/use-orders.ts`: queryFn에 status 전달
- 기존 테스트 업데이트 및 status 필터 테스트 추가

## Out of Scope

- 백엔드 order-service 구현 (별도 백엔드 태스크)
- 검색 기능 추가

# Acceptance Criteria

- [ ] `specs/contracts/http/order-api.md`에 `status` 쿼리 파라미터가 정의됨
- [ ] 주문 목록 조회용 파라미터 타입에 `status` 필드 존재
- [ ] `useOrders` 훅이 status를 API에 전달
- [ ] status 필터가 빈 값일 때 API에 전달되지 않음
- [ ] 기존 테스트 통과 및 status 필터 관련 테스트 추가
- [ ] 빌드 성공

# Related Specs

- `specs/contracts/http/order-api.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/order-api.md`

# Target App

- `apps/admin-dashboard`

# Edge Cases

- status 필터가 빈 값인 경우 파라미터 제외
- status 필터와 page 동시 적용
- 유효하지 않은 status 값 무시

# Failure Scenarios

- 백엔드에서 status 파라미터를 아직 지원하지 않는 경우 (무시됨, 에러 없음)
- 필터 적용 후 API 실패 시 에러 상태 표시
