# Task ID

TASK-FE-110

# Title

웹스토어 배송 추적이 «취소됨»(`CANCELLED`) 배송을 모른다 — 켜지는 단계 없이 빈 추적기가 보인다 (ADR-MONO-022 D4 v2(d))

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (상태 하나 추가 · 문구)
> 기안 2026-10-23 UTC — `TASK-BE-649` 구현 중 곁 발견. 배송에 끝 상태 `CANCELLED` 가 생겼는데(출고 전에 주문이 취소된 배송) 구매자 화면에 담당 티켓이 없다. 콘솔 짝 = `projects/platform-console` `TASK-PC-FE-367`.

# Dependency Markers

- 선행: `TASK-BE-649` (배송 `CANCELLED` · 계약 `shipping-api.md`).

# Goal

구매자가 취소한 주문의 배송을 열면, 지금은 `getStepIndex` 가 -1 이라 켜진 단계가 없는 추적기가 보인다. 화면이 깨지지는 않는다. 이것을 «배송이 취소되었습니다» 류의 상태로 보이게 한다.

# Scope

## In Scope

- **AC-0**: 웹스토어가 배송 상태를 받는 곳을 모두 찾는다(file:line). `packages/types/src/shipping.ts:3`, `shipping-steps.ts:3-12`, `ShippingTracker.tsx:51` 이 출발점이고, 주문 상세 · 목록의 배송 표시도 확인한다.
- 타입에 `CANCELLED` 를 넣는다. 추적기는 단계 대신 취소 표시를 그린다.
- **사용자에게 보이는 문구는 머지 전에 소유자 승인**을 받는다.

## Out of Scope

- 콘솔(`TASK-PC-FE-367`) · 서버.

# Acceptance Criteria

- [ ] **AC-0** — 위 측정.
- [ ] **AC-1** — 문구 소유자 승인.
- [ ] **AC-2** — 구현 + 단위 시험 · CI 초록 · 브랜치 nightly(web-store full-stack) 확인.
- [ ] **AC-3** — 굽기 뒤 라이브: 출고 전에 취소한 주문의 배송이 취소로 보인다.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D4 v2(d)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/shipping-api.md` (`CANCELLED`)

# Edge Cases

- 배송 이력(`statusHistory`)의 마지막 항목이 `CANCELLED` 다. 그 앞 단계는 `PREPARING` 하나뿐이다.

# Failure Scenarios

1. 취소된 배송이 «준비중» 처럼 읽혀 구매자가 물건을 기다린다 → AC-0 · 문구.
