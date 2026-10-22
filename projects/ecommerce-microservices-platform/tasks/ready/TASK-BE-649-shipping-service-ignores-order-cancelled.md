# Task ID

TASK-BE-649

# Title

shipping-service 가 `order.order.cancelled` 를 소비하지 않는다 — 출고 요청이 이미 나간 확정 주문을 취소해도 배송 · 출고 쪽은 모른다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (주문 · 배송 · WMS 출고에 걸친 취소 흐름 설계)
> 기안 2026-10-22 UTC — `TASK-BE-646` AC-0 곁 발견 (b). 소유자 결정 2026-10-22 KST: **«ready 티켓만»** — 이번 굽기에 싣지 않는다. 착수는 소유자 지시 때.

# Dependency Markers

- 출처: `projects/ecommerce-microservices-platform/tasks/review/TASK-BE-646-*.md` § AC-0 곁 발견 (b).
- 관련: WMS 출고 취소(`projects/wms-platform` outbound-service `CANCELLED` · 사가 `CANCELLATION_REQUESTED`) — 이커머스 → WMS 출고 연동(ADR-MONO-022 계열)이 있는 범위에서.

# Goal

- `OrderCancelled` 의 소비자는 payment · product · promotion 이다(`order-events.md` 소비자 목록 — BE-646 이 product 를 보탬). **shipping-service 는 없다**.
- 확정(`CONFIRMED`) 주문은 배송 생성 · (설정에 따라) WMS 출고 요청까지 갈 수 있다. 그 뒤 운영자 · 사용자가 취소하면 환불 · 재고 해제는 되지만(BE-646) 배송 · 출고는 계속 진행될 수 있다.

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 확정 뒤 배송 · 출고 요청이 만들어지는 시점과 경로 ② 배송 상태별(준비중 · 발송 · 배송중 · 배송완료) 취소 가능성 · WMS 출고 상태별 취소 가능성(정상 흐름 · 곁가지) ③ 지금 «출고 뒤 취소» 가 실제로 일어날 수 있는가(주문 취소 가능 상태 `isCancellable` = 대기 · 확정 · 백오더 — 배송 시작 뒤에는?) ④ 계약 · ADR 이 출고 취소를 어떻게 적는가.
- **설계 결정 (AC-0 뒤 소유자 선택창)**: 배송이 시작되기 전까지만 주문 취소 허용 vs 배송 · 출고 취소 보상 흐름 신설 — ADR 이 필요한지 판정.
- 구현은 결정 뒤.

## Out of Scope

- 환불 · 재고 해제(BE-646 에서 됨).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④.
- [ ] **AC-1** — 소유자 결정 · 필요하면 ADR 먼저.
- [ ] **AC-2** — 구현 + 시험 · CI 초록.
- [ ] **AC-3** — 굽기 뒤 라이브.

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/` · `order-service/`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md`
- 배송 · WMS 출고 연동 계약(AC-0 에서 이름 확인)

# Edge Cases

- 이미 배송완료된 주문의 취소 — 반품 흐름이지 취소가 아니다(범위 판정).

# Failure Scenarios

1. 배송 서비스만 취소를 받고 WMS 출고는 계속 → 출고된 물건 · 취소된 주문. AC-0 ① · ④ 가 연동 전체를 본다.
