# Task ID

TASK-FE-019

# Title

admin-dashboard 주문 관리 기능 구현 — 주문 목록, 상세, 취소

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- api

# Goal

admin-dashboard에 주문 관리 기능을 구현한다. 주문 목록 조회(상태 필터, 페이지네이션), 주문 상세 조회, 주문 취소 기능을 포함한다.

# Scope

## In Scope

- `features/order-management/api/order-api.ts`: order API 래퍼 함수
- `features/order-management/hooks/use-orders.ts`: 주문 목록 훅 (상태 필터 + 페이지네이션)
- `features/order-management/hooks/use-order.ts`: 주문 상세 훅
- `features/order-management/hooks/use-cancel-order.ts`: 주문 취소 mutation 훅
- `features/order-management/components/OrderList.tsx`: 주문 목록 컴포넌트
- `features/order-management/components/OrderDetail.tsx`: 주문 상세 컴포넌트
- `features/order-management/index.ts`: feature export
- `app/(admin)/orders/page.tsx`: 주문 목록 페이지 업데이트
- `app/(admin)/orders/[id]/page.tsx`: 주문 상세 페이지 업데이트
- 테스트 추가

## Out of Scope

- 주문 생성 (관리자가 직접 주문 생성하지 않음)
- 주문 상태 변경 (CONFIRMED, SHIPPED 등 — 백엔드 API 미존재)
- 결제 정보 조회 (별도 태스크)

# Acceptance Criteria

- [ ] 주문 목록이 DataTable로 표시됨 (주문번호, 상태, 총액, 상품수, 생성일)
- [ ] 주문 상태별 필터링 가능 (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)
- [ ] 페이지네이션 동작
- [ ] 주문 행 클릭 시 상세 페이지 이동
- [ ] 주문 상세에서 주문 정보, 주문 항목, 배송지 정보 표시
- [ ] PENDING/CONFIRMED 상태 주문에 취소 버튼 표시, ConfirmDialog 후 취소 실행
- [ ] 취소 성공 시 상태 갱신
- [ ] 에러/로딩/빈 상태 처리
- [ ] 테스트 추가
- [ ] 빌드 성공

# Related Specs

- `specs/contracts/http/order-api.md`
- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/order-api.md`

# Target App

- `apps/admin-dashboard`

# Edge Cases

- 주문 목록이 비어있는 경우
- 취소 불가능한 상태(SHIPPED, DELIVERED, CANCELLED)에서 취소 버튼 미표시
- 취소 요청 중 중복 클릭 방지
- 취소 실패 시 에러 표시

# Failure Scenarios

- API 에러 시 에러 상태 표시 + 재시도
- 취소 API 실패 시 에러 메시지 표시
- 존재하지 않는 주문 접근 시 처리
