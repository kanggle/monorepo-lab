# Task ID

TASK-PC-FE-367

# Title

이커머스 배송 상태에 «취소됨»(`CANCELLED`) 표시 — 출고 전에 취소된 주문의 배송 (ADR-MONO-022 D4 v2(d))

# Status

review

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

- [x] **AC-0** — 위 측정 (2026-10-23 UTC, `console-web/src/features/`). 콘솔이 배송 상태를 받는 곳과 `CANCELLED` 의 현재 동작:
  - `ecommerce-ops/api/shipping-types.ts` — `SHIPPING_STATUS_VALUES`(4값; 필터 선택지 · 개요 읽기의 단일 근원), `SHIPPING_STATUS_TONE`(미등록 → `neutral`), `LINEAR_TRANSITIONS`(미등록 → 다음 단계 버튼 없음). zod 는 `status: z.string()` 이라 모르는 값도 파싱은 통과 → 화면이 깨지지 않고 원문 `CANCELLED` 가 중립 배지로 보임.
  - `ecommerce-ops/components/shipping-labels.ts` — `STATUS_LABELS`(미등록 → 원문), `STATUS_FILTER_OPTIONS`(4값 + 전체; `CANCELLED` 없음 → 필터로 못 찾음).
  - `ecommerce-ops/components/ShippingsTable.tsx` — 배지(`shippingStatusTone` + `statusLabel`), 다음 단계 버튼(`allowedNextStatus` → `CANCELLED` 는 null 이라 안 뜸), 「추적 동기화」 버튼은 `PREPARING`·`DELIVERED` 만 제외 → `CANCELLED` 에서 뜸(버그).
  - `ecommerce-ops/api/overview-state.ts` + `components/EcommerceOverview.tsx` + `ShippingStatusBar.tsx` — 개요 배송 분포: `SHIPPING_STATUS_VALUES` 값마다 읽기 1회 → 4개라 `CANCELLED` 는 분포에서 빠짐(합계가 목록 총수보다 작아짐).
  - `ecommerce-guide/data.ts` `SHIPPING_STATES` + `EcommerceGuideScreen.tsx`(흐름도 `StateFlow` · 상태 표) — 4단 선형, `CANCELLED` 설명 없음.
  - 운영자 상태 변경 UI: 다음 단계 버튼 하나뿐(`allowedNextStatus`)이라 대상으로 `CANCELLED` 를 고르는 경로 없음. 샘플 픽스처(`shared/sample/fixtures/ecommerce.ts`)는 4값만 — 변경 안 함.
- [x] **AC-1** — 문구 소유자 승인. ✅ 2026-10-23 UTC 소유자 선택창 «이대로 승인» — 라벨 «취소됨»(danger) · 가이드 표 행 «출고 전에 주문이 취소되면 배송도 «취소됨»으로 바뀝니다. 이미 출고된 배송은 취소되지 않습니다.»
- [x] **AC-2** — 구현 + 단위 시험 · CI 초록 · 브랜치 nightly 콘솔 잡 확인(화면 · SSR 변경이면). (2026-10-23 UTC 구현 + 로컬: tsc 0 · next lint 0(기존 경고 1) · vitest shipping/ecommerce/overview/guide 96 파일 1197 통과. CI · nightly 는 미확인 — OPEN) ✅ 2026-10-23 UTC (메인 세션) — PR #4492 체크 pending 0 · fail 0. CI 로그로 새 시험이 돈 것을 확인했다: `EcommerceGuideScreen.test.tsx` 19건(기준 18) · `ecommerce-shippings-state.test.ts` 19건(기준 17), 모두 ✓. 브랜치 nightly `39929201288` success(콘솔 full-stack 포함).
- [ ] **AC-3** — 굽기 뒤 라이브: 취소된 주문의 배송이 «취소됨» 으로 보인다.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D4 v2(d)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/shipping-events.md` · shipping-service HTTP 계약 (`CANCELLED` 값)

# Edge Cases

- 배송 상태 막대(`StepBar` 류)는 `CANCELLED` 를 순서 단계가 아닌 끝 상태로 그린다.

# Failure Scenarios

1. 모르는 상태 값이 와서 화면이 깨지거나 빈칸이 된다 → AC-0 이 지금 동작을 측정한다.
