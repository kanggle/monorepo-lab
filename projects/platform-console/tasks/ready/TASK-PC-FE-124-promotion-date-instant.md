# TASK-PC-FE-124 — 콘솔 프로모션 폼이 날짜를 Instant(Z) 형식으로 전송하도록 수정

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (날짜 직렬화 버그픽스)

## Goal

콘솔 E-Commerce 프로모션 **등록/수정**이 항상 `400 INVALID_PROMOTION_REQUEST: Invalid date format` 으로 실패하던 버그를 고친다.

근본 원인: `PromotionForm` 의 시작일/종료일 입력은 `<input type="date">` 라 값이 `YYYY-MM-DD`(날짜만, 시간/오프셋 없음)인데, 제출 시 이를 그대로(`startDate.trim()`) 전송한다. 프로듀서(promotion-service)는 `startDate`/`endDate` 를 `java.time.Instant` 로 파싱하므로 **트레일링 `Z` 가 있는 ISO-8601** 이어야 한다(`Instant.parse("2026-06-23")` 실패). 빈 값은 폼 검증에 막혀서 항상 이 형식 오류로 귀결 → 등록 전건 실패.

부수 버그: 수정(edit) 진입 시 `existing.startDate`(프로듀서가 준 full Instant 문자열, 예 `2026-07-01T00:00:00Z`)를 `type="date"` 입력에 그대로 넣어 값이 표시되지 않음(date 입력은 `YYYY-MM-DD` 만 허용).

## Scope

**In scope** (console-web only, `PromotionForm.tsx`):

1. `dayToInstant(day, edge)` 헬퍼 추가 — `YYYY-MM-DD` → UTC instant(start `T00:00:00Z`, end `T23:59:59Z`).
2. 제출 바디의 `startDate`/`endDate` 를 `dayToInstant(...)` 로 변환해 전송.
3. edit 프리필 시 `existing.{start,end}Date` 를 `.slice(0,10)` 으로 날짜부만 입력에 채움.

**Out of scope**: 프로듀서(promotion-service) 변경(계약상 Instant 가 맞음), API client(`promotions-api`)는 passthrough 유지, 날짜 입력 위젯 타입 변경(date 유지).

## Acceptance Criteria

- **AC-1 — 등록 성공.** 유효 입력으로 프로모션 등록 시 `POST /api/promotions` 가 201 을 반환(프로듀서가 `2026-06-23T00:00:00Z` 형식을 수락 — 실측 확인).
- **AC-2 — 수정 성공/프리필.** 수정 화면 진입 시 시작일/종료일 date 입력에 기존 날짜가 표시되고, 저장(PUT) 시 Instant 형식으로 전송되어 200.
- **AC-3 — 윈도우.** 종료일은 해당 일자의 끝(`23:59:59Z`)으로 전송되어 당일 종료가 포함적.
- **AC-4 — 게이트.** ecommerce 단위테스트 GREEN(promotions-api passthrough 테스트는 date-only 입력을 그대로 전달하므로 무영향) + `tsc --noEmit` clean + `next lint` clean. CI `vitest run` GREEN.

## Related Specs

- `console-integration-contract.md` § 2.4.10 (ecommerce promotion operator surface) — 프로모션 create/update 계약.

## Related Contracts

- promotion-service `CreatePromotionCommand` / `UpdatePromotionCommand` — `startDate`/`endDate` 가 `java.time.Instant`(Z 필수). 콘솔이 이 형식을 맞춰야 함.

## Edge Cases

- `<input type="date">` 빈 값 → `formValid` 가 막아 제출 불가(기존 동작 유지).
- 동일 일자 start=end → start `00:00:00Z` < end `23:59:59Z` 로 유효 윈도우 형성.
- 프로듀서가 반환하는 Instant 가 `…T00:00:00Z` 외 정밀도(밀리초 등)여도 `.slice(0,10)` 은 날짜부만 취하므로 프리필 안전.

## Failure Scenarios

- 변환 누락 시(`YYYY-MM-DD` 그대로 전송) 프로듀서가 400 INVALID_PROMOTION_REQUEST → 등록/수정 전건 실패(본 회귀 검출 지점은 AC-1/AC-2).
