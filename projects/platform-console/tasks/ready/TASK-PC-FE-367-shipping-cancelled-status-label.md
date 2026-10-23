# Task ID

TASK-PC-FE-367

# Title

이커머스 배송 상태에 «취소됨»(`CANCELLED`) 표시 — 출고 전에 취소된 주문의 배송 (ADR-MONO-022 D4 v2(d))

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (라벨 · 배지 추가)
> 기안 2026-10-23 UTC — `ADR-MONO-022` **D4 v2(d) ACCEPTED — X-A / Y-A**. 서버 짝 = 이커머스 `TASK-BE-649`.

# Dependency Markers

- 선행: 이커머스 `TASK-BE-649` 의 계약(`ShippingStatus` 에 `CANCELLED` 추가)이 머지되어야 한다. 콘솔은 그 뒤에 머지한다. 모르는 상태를 콘솔이 어떻게 그리는지는 AC-0 에서 확인한다. 안전하게 그린다면 순서는 상관없다.

# Goal

출고 전에 주문이 취소되면 배송 기록은 `PREPARING` 에서 끝 상태 `CANCELLED` 로 간다. 콘솔의 배송 목록 · 상세 · 필터 · 가이드에 이 상태를 보여준다.

# Scope

## In Scope

- **AC-0**: 콘솔이 배송 상태 값을 받는 곳을 모두 찾는다(file:line). 라벨 표 · 배지 색 · 필터 선택지 · 상태 막대 · 가이드 문서가 대상이다. 지금 모르는 값이 오면 어떻게 그리는지도 확인한다.
- 라벨 «취소됨» 과 배지를 추가한다. 가이드 배송 상태 설명에는 «출고 전에 주문이 취소되면 배송도 취소됩니다» 류의 문장을 넣는다.
- **사용자에게 보이는 문구는 머지 전에 소유자 승인**을 받는다.

## Out of Scope

- 서버(`TASK-BE-649` · `TASK-BE-650`).

# Acceptance Criteria

- [ ] **AC-0** — 위 측정.
- [ ] **AC-1** — 문구 소유자 승인.
- [ ] **AC-2** — 구현 + 단위 시험 · CI 초록 · 브랜치 nightly 콘솔 잡 확인(화면 · SSR 변경이면).
- [ ] **AC-3** — 굽기 뒤 라이브: 취소된 주문의 배송이 «취소됨» 으로 보인다.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D4 v2(d)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/shipping-events.md` · shipping-service HTTP 계약 (`CANCELLED` 값)

# Edge Cases

- 배송 상태 막대(`StepBar` 류)는 `CANCELLED` 를 순서 단계가 아닌 끝 상태로 그린다.

# Failure Scenarios

1. 모르는 상태 값이 와서 화면이 깨지거나 빈칸이 된다 → AC-0 이 지금 동작을 측정한다.
