# Task ID

TASK-BE-646

# Title

관리자 주문 상태 변경의 «취소» 가 `OrderCancelled` 를 내지 않는다 — 콘솔에서 취소한 주문은 환불 · 재고 해제가 일어나지 않고, 백오더 주문은 다음 재입고 때 재고가 차감된 뒤 소비 실패한다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 상태 기계 · 보상 이벤트 · 환불 fan-out)
> 기안 2026-10-22 UTC — `TASK-BE-645` 구현 중 곁 발견. 소유자 결정 2026-10-22 KST: **«티켓 + 굽기 전 구현»** (돈 · 재고가 걸린 결함이라 다음 굽기에 싣는다). 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 🔴 **선행: `TASK-BE-645`** (같은 파일 `AdminOrderStatusService` 를 고친다 — 645 머지 뒤 착수).
- 관련: `TASK-PC-FE-365`(콘솔이 백오더 주문에도 «취소» 를 열었다 — 이 결함의 범위가 넓어졌다).

# Goal

- 콘솔은 주문 취소를 `POST /api/admin/orders/{id}/status` `{status: CANCELLED}` 로 한다(`console-web/.../ecommerce-ops/api/orders-api.ts:155-165`).
- 그 엔드포인트의 서비스 `apps/order-service/.../application/service/AdminOrderStatusService.java:35` 는 `order.cancel(clock)` → 저장뿐이다 — **이벤트 발행자가 없다**.
- `OrderCancelled` 를 내는 운영자 취소 서비스는 따로 있다: `OperatorOrderCancellationService.java:54-61`(지표 `operator` + `publishOrderCancelled`) — 그러나 **내부 엔드포인트** `InternalOrderController.java:105` `POST /api/internal/orders/{id}/cancel` 에만 붙어 있다.
- 계약 `specs/contracts/events/order-events.md:218-219`: «A `BACKORDERED` order may be cancelled by an operator (`OrderCancelled` → refund fan-out)».
- 결과(코드 읽기 — AC-0 이 확인):
  - `CONFIRMED`(재고 예약 · 결제 완료) 주문을 콘솔에서 취소 → 환불 없음 · 예약 재고 해제 없음.
  - `BACKORDERED` 주문을 콘솔에서 취소 → product 쪽 예약이 남아, 다음 재입고 때 재고 차감 + `ORDER_RESERVED` → order-service 가 `CANCELLED` 주문에 `confirm` 을 시도하다 소비 실패.
  - 곁: 운영자의 `PENDING → CONFIRMED` 도 `OrderConfirmed` 를 내지 않는다(`TASK-BE-645` 보고) — 이 티켓에서 함께 판정(AC-0 ④).

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 콘솔 · 다른 소비자가 관리자 상태 변경 «취소» 를 쓰는 경로 전수 ② `OperatorOrderCancellationService` 와 `OrderCancellationService`(사용자 취소)의 차이 — 결제 상태별 환불 · 재고 해제 · 지표 · 멱등 ③ 운영 데이터에 이미 이벤트 없이 취소된 주문이 있을 수 있는가(데모 · 시드 — 보정 필요 여부) ④ 운영자 `PENDING → CONFIRMED` 가 `OrderConfirmed` 를 내지 않는 것이 결함인가(결제 전 대기 주문을 운영자가 확정하는 것의 의미 · 계약) — 결함이면 이 티켓에서 고칠지 소유자에게.
- 계약 먼저: `order-api.md` 운영자 상태 변경 절에 «취소 = `OrderCancelled`(사유 operator) 발행 · 환불 fan-out» 명시.
- 수선: 관리자 상태 변경의 `CANCELLED` 갈래가 `OperatorOrderCancellationService` 를 타게(같은 트랜잭션 · 같은 이벤트 · 같은 지표). 응답 모양 · 오류 어휘는 그대로.
- 시험: 관리자 취소(대기 · 확정 · 백오더) → `OrderCancelled` 1회 · 같은 요청 두 번 → 1회(멱등) · 이미 취소된 주문 → 기존 동작 · 내부 엔드포인트 동작 불변 · 백오더 취소 뒤 재입고 `ORDER_RESERVED` 가 와도 소비가 실패하지 않는가(product 쪽 예약 해제 경로 — AC-0 ② 가 정한 범위).

## Out of Scope

- 콘솔 변경(엔드포인트 그대로).
- 환불 서비스 자체.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④. ④ 의 범위는 소유자 결정.
- [ ] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록.
- [ ] **AC-2** — 굽기 뒤 라이브: 데모에서 콘솔로 주문 하나 취소 → `OrderCancelled` · 환불 · 재고 해제 확인(다음 데모 창).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/` (상태 기계 · 보상)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (갱신 대상)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`:211-220` — 읽기)

# Edge Cases

- 결제 전 `PENDING` 취소 — 환불 대상 없음(이벤트는 내되 소비자가 무시하는가 — AC-0 ②).
- 동시 취소(콘솔 + 사용자) — 한 번만 발행.

# Failure Scenarios

1. 이벤트만 더하고 재고 해제 · 환불 소비자가 운영자 사유를 모른다 → AC-0 ② · 시험.
2. 645 와 같은 파일을 병렬로 고쳐 충돌 · 회귀 → 645 머지 뒤 착수.
