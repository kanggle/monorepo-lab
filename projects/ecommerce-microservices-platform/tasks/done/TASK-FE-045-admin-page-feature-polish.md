# TASK-FE-045: Admin 관리 페이지 기능 보완

## Goal

admin-dashboard의 상품/주문/사용자 관리 페이지에서 기존 API 컨트랙트 범위 내 누락된 기능을 추가하고, 상세 페이지의 UX를 개선한다.

## Scope

- `apps/admin-dashboard/src/features/` 내 product-management, order-management, user-management

### In Scope

1. **상품 관리** — ProductDetail에 상태 변경 드롭다운 추가 (기존 PATCH API 활용)
2. **상품 관리** — ProductList에서 가격 포맷팅 개선 (천단위 콤마)
3. **주문 관리** — OrderList에 주문번호 검색 기능 추가
4. **주문 관리** — OrderDetail에 결제 정보 섹션 추가 (paymentInfo가 응답에 있는 경우)
5. **사용자 관리** — UserDetail에 주소 목록 표시 섹션 추가 (addresses가 응답에 있는 경우)
6. **공통** — 리스트 페이지에서 행 클릭 시 커서 포인터 + hover 피드백이 자연스럽게 동작하도록 확인

### Out of Scope

- 백엔드 API 또는 컨트랙트 변경이 필요한 기능 (삭제 API, 주문 상태 변경 API, 사용자 상태 변경 API 등)
- 상품 이미지 업로드
- 대시보드 통계 구현

## Acceptance Criteria

- [ ] ProductDetail 페이지에서 상태를 ON_SALE/SOLD_OUT/HIDDEN으로 변경할 수 있다
- [ ] 상태 변경 시 Toast로 성공/실패 알림이 표시된다
- [ ] ProductList의 가격 컬럼에 천단위 콤마가 적용된다
- [ ] OrderList에 주문번호 검색 필드가 존재하고 동작한다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/order-api.md`

## Related Contracts

- `specs/contracts/http/product-api.md` — PATCH /api/admin/products/{productId}

## Edge Cases

- 상품 상태 변경 중 네트워크 에러 시 이전 상태가 유지되어야 한다
- 상태 변경 요청 중 중복 클릭을 방지해야 한다
- 주문번호 검색 시 빈 값을 전송하면 전체 목록이 조회되어야 한다

## Failure Scenarios

- 상품 상태 변경 실패 시 Toast 에러 메시지가 표시되고 UI가 롤백된다
- 존재하지 않는 주문번호 검색 시 빈 결과가 표시된다
