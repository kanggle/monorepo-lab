# Task ID

TASK-BE-648

# Title

운영자 주문 «확정»(`PENDING → CONFIRMED`)을 없앤다 — 운영자 확정은 `OrderConfirmed` · 재고 예약을 거치지 않아, 나중에 결제가 끝나도 확정 이벤트가 영영 나가지 않고 멈춘 주문 감지에서도 빠진다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 상태 기계 · 계약 충돌 해소)
> 기안 2026-10-22 UTC — `TASK-BE-646` AC-0 ④ 의 판정. 소유자 결정 2026-10-22 KST: **«운영자 확정 없애기 · 티켓»**(굽기 전 구현). 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 선행: `TASK-BE-645` · `TASK-BE-646`(같은 파일 `AdminOrderStatusService` — 둘 다 머지됨).
- 짝: `projects/platform-console/tasks/ready/TASK-PC-FE-366-*.md`(콘솔 상태 변경 선택지에서 «확정» 제거 — 콘솔은 먼저 머지돼도 안전하다).

# Goal

- 관리자 상태 변경(`POST /api/admin/orders/{id}/status`)은 지금 `PENDING → CONFIRMED` 를 허용한다(`AdminOrderStatusService.java` 의 `CONFIRMED` 갈래 → `Order.confirm`, BE-645 가 `BACKORDERED` 만 막았다).
- 이 경로는 `OrderConfirmed` 를 내지 않고 재고 예약도 하지 않는다. 계약끼리 충돌한다: `order-events.md:84-89`(확정 시 항상 `OrderConfirmed`) vs `order-api.md:406`(운영자 확정 허용).
- 결과(BE-646 AC-0 ④): 운영자가 결제 전 주문을 확정 → 나중에 결제가 끝나 product 가 재고를 차감해도 `Order.confirm` 이 이미 `CONFIRMED` 라 false → `OrderConfirmed` 가 영영 안 나간다. 결제가 끝내 안 되면 멈춘 주문 감지(대상 = `PENDING` 만)에서도 빠진다.
- 확정은 결제 · 재고 예약 사가(`OrderConfirmationService`)만 한다.

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 운영자 확정을 쓰는 곳 전수(콘솔 · 다른 클라이언트 · 시드 · 시험 — 데모 시드가 운영자 확정에 기대는가) ② 운영자가 «결제는 됐는데 멈춘 주문» 을 손으로 확정할 정당한 필요가 있는가(그 경우는 이미 `StalePaidOrderConfirmHandler` · 멈춘 주문 감지가 맡는가) ③ 거부 응답의 오류 어휘(BE-645 와 같은 400 `INVALID_ORDER_REQUEST`).
- 계약 먼저: `order-api.md` 운영자 상태 변경 — 운영자가 정할 수 있는 상태는 `CANCELLED` 하나(대기 · 확정 · 백오더에서). 두 계약의 충돌 해소를 이력으로 남긴다.
- 수선: 관리자 상태 변경에서 목표 `CONFIRMED` 를 거부(모든 현재 상태). 사가 · `StalePaidOrderConfirmHandler` 경로 불변.
- 시험: 운영자 대기 → 확정 400 · 저장 없음 · 사가의 대기 → 확정 + `OrderConfirmed` 불변 · 운영자 취소 불변(BE-646).

## Out of Scope

- 콘솔(`TASK-PC-FE-366`).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~③. ② 에서 정당한 필요가 있으면 STOP(소유자에게).
- [ ] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록(가능하면 시험만 커밋으로 빨강 먼저).
- [ ] **AC-2** — 굽기 뒤 라이브: 운영자 API 로 대기 주문 확정 → 400(다음 데모 창).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` (상태 기계)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (갱신 대상)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`:84-89` — 읽기)

# Edge Cases

- 이미 `CONFIRMED` 인 주문에 운영자 확정 → 지금은 멱등 200 — 이제 400 으로 바뀌는가(AC-0 이 정함 · 콘솔은 366 부터 안 낸다).

# Failure Scenarios

1. 도메인 `confirm` 을 고쳐 사가 경로까지 막는다 → 사가 시험이 막는다(운영자 서비스에서만 거부).
2. 콘솔이 아직 «확정» 을 내는데 서버만 바뀌어 운영자에게 400 이 보인다 → 366 을 먼저(또는 함께) 머지.
