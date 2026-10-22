# Task ID

TASK-BE-645

# Title

운영자 주문 상태 변경 API 가 `BACKORDERED → CONFIRMED` 를 받아 준다 — 재고 재예약 없이 확정돼 재입고 사가와 이중이 되거나 재고 없이 출고로 넘어갈 수 있다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 상태 기계 · 운영자 API · 재입고 사가의 경계)
> 기안 2026-10-22 UTC — `TASK-PC-FE-365` AC-0 ③ 의 곁 발견(소유자 결정 2026-10-22 KST: «이커머스 ready 티켓»). **ready 만 — 착수는 소유자 지시 때.** 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 출처: `projects/platform-console/tasks/*/TASK-PC-FE-365-*.md` § AC-0 ③.
- 콘솔은 365 부터 백오더 주문에 «취소» 만 낸다 — 이 티켓은 **API 를 직접 부르는 경로**를 막는다.

# Goal

- 운영자 상태 변경: `apps/order-service/.../application/service/AdminOrderStatusService.java:33-35` — 목표가 `CONFIRMED` 면 `order.confirm(clock)`.
- 도메인: `apps/order-service/.../domain/model/Order.java:213-215` — `confirm` 은 `PENDING` **또는 `BACKORDERED`** 에서 허용한다(주석: «a later replenishment re-reserved the held order — TASK-BE-428»). 이 두 번째 갈래는 **재입고 재예약 사가**를 위한 것이다.
- ⇒ 운영자가 API 로 백오더 주문을 «확정» 하면 재고 예약 없이 `CONFIRMED` 가 된다. 그 뒤 재입고 사가가 같은 주문을 다시 확정하려 하거나(이중), 재고 없는 주문이 배송 흐름으로 넘어간다.
- 계약(`specs/contracts/events/order-events.md:218` 근처)은 백오더에서 운영자가 할 수 있는 것을 «취소» 로 적는다(AC-0 에서 원문 확인).

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 운영자 API 의 계약(`order-api.md` 의 상태 변경 절)이 허용 전이를 어떻게 적는가 ② `confirm` 을 부르는 곳 전수(결제 완료 · 재입고 재예약 · 운영자) ③ 운영자 확정이 지금 재고 예약을 거치는가(거치지 않는다는 것이 이 티켓의 전제 — 틀리면 STOP) ④ 이미 운영자가 백오더를 확정한 데이터가 있을 수 있는가(데모 · 시드).
- 수선(AC-0 뒤): 운영자 경로에서 `BACKORDERED → CONFIRMED` 를 거부(400 · 기존 오류 어휘) — 도메인 `confirm` 을 둘로 나누거나(`confirmAfterPayment` · `confirmAfterReReservation`) 운영자 서비스에서 막는다(AC-0 이 정한 쪽). 재입고 사가 경로는 그대로.
- 계약 먼저: 운영자 상태 변경 절에 «백오더에서는 취소만» 을 명시.
- 시험: 운영자 백오더 → 확정 = 400 · 운영자 백오더 → 취소 = 200 · 재입고 사가의 백오더 → 확정 = 그대로 성공 · 대기 → 확정(운영자) = 그대로.

## Out of Scope

- 콘솔(365 가 이미 취소만 낸다).
- 재입고 사가 자체 변경.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④. ③ 이 틀리면 STOP.
- [ ] **AC-1** — 계약 먼저 → 구현 + 시험 · CI 의 ecommerce 잡 초록.
- [ ] **AC-2** — 굽기 뒤 라이브: 데모에서 백오더 주문을 운영자 API 로 확정하면 400(다음 데모 창 · 백오더 주문이 있을 때).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/` (상태 기계 — AC-0 에서 이름 확인)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (운영자 상태 변경 — 갱신 대상)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (`:218` 근처 — 읽기)

# Edge Cases

- 운영자 확정 요청과 재입고 재예약이 거의 동시에 오면 — 재예약 쪽이 이겨야 한다(운영자 요청은 400).
- 이미 `CONFIRMED` 인 주문에 운영자 확정 → 지금 동작 유지(멱등 · 또는 기존 오류).

# Failure Scenarios

1. 도메인 `confirm` 에서 `BACKORDERED` 갈래를 지워 재입고 사가까지 막는다 → 사가 시험이 막는다.
2. 운영자 경로만 막고 계약을 안 고쳐 계약이 거짓 → AC-1.
