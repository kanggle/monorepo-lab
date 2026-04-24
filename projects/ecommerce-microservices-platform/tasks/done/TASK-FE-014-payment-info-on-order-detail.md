# TASK-FE-014: 주문 상세 페이지 결제 정보 표시

## Goal
web-store 주문 상세 페이지에서 결제 정보를 조회하고 표시한다.
payment-api 계약에 정의된 `GET /api/payments/orders/{orderId}` 엔드포인트를 프론트엔드에서 연동한다.

## Scope
- `entities/payment/` — 결제 엔티티 신규 생성
  - `api/payment-api.ts` — 결제 조회 API 래퍼
  - `ui/PaymentStatusBadge.tsx` — 결제 상태 배지 컴포넌트
  - `index.ts` — 공개 API
- `app/(store)/orders/[id]/page.tsx` — 주문 상세 페이지에 결제 정보 섹션 추가
- 테스트 추가

## Acceptance Criteria
- 주문 상세 페이지에서 해당 주문의 결제 정보(결제 상태, 결제 금액, 결제일, 환불일)를 표시한다
- 결제 상태별(PENDING, COMPLETED, FAILED, REFUNDED) 배지가 적절한 색상으로 표시된다
- 결제 정보가 없는 경우(404) 결제 정보 섹션이 표시되지 않는다
- 결제 정보 조회 실패 시 에러 메시지를 표시한다
- entities/payment/ 엔티티가 FSD 아키텍처를 준수한다
- 기존 빌드가 깨지지 않는다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/contracts/http/payment-api.md`

## Related Contracts
- `specs/contracts/http/payment-api.md`

## Edge Cases
- 결제 정보가 아직 생성되지 않은 주문(PENDING 상태 직후)에서 404 응답 처리
- 결제가 환불된 경우 refundedAt 날짜 표시
- paidAt이 null인 경우(PENDING/FAILED) 결제일 미표시

## Failure Scenarios
- 네트워크 오류 시 결제 정보 섹션에 에러 메시지 표시 (주문 정보는 정상 표시 유지)
- 인증 만료 시 로그인 페이지로 이동
