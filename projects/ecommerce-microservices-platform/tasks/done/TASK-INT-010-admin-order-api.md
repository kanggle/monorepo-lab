# TASK-INT-010: 어드민 전용 주문 조회 API 추가

## Goal

관리자가 모든 사용자의 주문을 조회할 수 있도록 어드민 전용 주문 목록/상세 API를 추가하고, admin-dashboard에서 이를 사용하도록 연동한다.

## Scope

- `specs/contracts/http/order-api.md` — 어드민 엔드포인트 컨트랙트 추가
- `apps/order-service/` — 어드민 전용 컨트롤러, 서비스 구현
- `packages/api-client/` — 어드민 주문 API 함수 추가
- `packages/types/` — 어드민 주문 타입 추가
- `apps/admin-dashboard/` — 어드민 주문 API 연동

### In Scope

1. 컨트랙트: `GET /api/admin/orders` (전체 주문 목록, 페이징, 상태 필터)
2. 컨트랙트: `GET /api/admin/orders/{orderId}` (주문 상세, 주문자 정보 포함)
3. 백엔드: order-service에 AdminOrderController 추가
4. api-client: createAdminOrderApi 함수 추가
5. types: AdminOrderSummary, AdminOrderDetail 타입 추가
6. admin-dashboard: 어드민 주문 API로 전환

### Out of Scope

- 주문 상태 변경 API (confirm, ship 등)
- 환불 처리 API
- 주문 검색 (주문번호, 주문자명)

## Acceptance Criteria

- [ ] `specs/contracts/http/order-api.md`에 어드민 엔드포인트가 문서화되어 있다
- [ ] `GET /api/admin/orders`가 모든 사용자의 주문을 반환한다
- [ ] `GET /api/admin/orders/{orderId}`가 주문자 정보를 포함하여 반환한다
- [ ] 어드민 역할이 아닌 사용자는 403 ACCESS_DENIED를 받는다
- [ ] admin-dashboard 주문 관리 페이지가 어드민 API를 사용한다
- [ ] 주문 상세 페이지에 주문자 이름이 표시된다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/contracts/http/order-api.md`
- `specs/contracts/http/user-api.md` (admin API 패턴 참고)
- `specs/contracts/http/product-api.md` (admin API 패턴 참고)

## Related Contracts

- `specs/contracts/http/order-api.md`

## Edge Cases

- 주문이 0건일 때 빈 목록이 정상 반환되어야 한다
- 존재하지 않는 orderId 조회 시 404 반환
- 페이지 범위를 초과한 요청 시 빈 content 반환

## Failure Scenarios

- 인증 토큰이 없으면 401 반환
- 일반 사용자(CUSTOMER) 토큰으로 요청하면 403 반환
