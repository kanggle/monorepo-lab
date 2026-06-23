# TASK-PC-FE-127 — 프로모션 등록 폼 시작일/종료일: 필드 영역 아무 곳이나 클릭하면 달력이 열리도록

- **Status**: done
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (단순 UX — 공유 헬퍼 적용)

## Goal

콘솔 E-Commerce > 프로모션 등록/수정 폼의 **시작일·종료일 `<input type="date">`** 가 기본 동작상 우측 달력 글리프(아이콘)를 눌러야만 picker 가 열린다. 운영자가 필드 영역 아무 곳이나 눌러도 달력이 열리도록 한다(이미 audit·erp 폼에서 쓰는 공유 헬퍼 `showPickerOnClick` 와 동일 동작으로 통일).

## Scope

**In scope** (console-web only):

1. `features/ecommerce-ops/components/PromotionForm.tsx` — 시작일/종료일 두 `type="date"` input 에 `onClick={showPickerOnClick}` 추가 + 헬퍼 import.
2. `tests/unit/promotion-form-date-clickopen.test.tsx` — 두 date input 클릭 시 `showPicker` 호출 + 미지원(jsdom) 환경에서 throw 하지 않음(feature-detect) 회귀 테스트.

**Out of scope**: 백엔드, 날짜 포맷/검증 로직(TASK-PC-FE-124 의 `dayToInstant` 유지), 새 헬퍼 작성(기존 `@/shared/lib/show-picker` 재사용), ecommerce-ops 외 다른 폼(이미 audit/erp 는 적용돼 있고 ecommerce-ops 의 유일한 date input 이 프로모션 폼).

## Acceptance Criteria

- **AC-1 — 클릭 영역 확대.** 시작일/종료일 입력 필드 영역 아무 곳이나 클릭하면 네이티브 date picker 가 열린다(글리프 전용 아님). `showPicker` 지원 브라우저 한정.
- **AC-2 — graceful fallback.** `showPicker` 미지원/차단(구형 브라우저·jsdom·이미 열림)에서는 예외 없이 기본 동작(글리프)으로 유지되어 입력이 그대로 사용 가능.
- **AC-3 — 기존 동작 보존.** 날짜 값 변경(`onChange`)·제출 시 Instant 변환(`dayToInstant`)·검증(`formValid`)은 영향 없음.
- **AC-4 — 게이트.** console-web `pnpm lint` + `tsc --noEmit` + `vitest run` GREEN(신규 테스트 포함).

## Related Specs

- console-integration-contract § 2.4.10 — ecommerce 프로모션 operator surface(등록/수정 폼).
- TASK-PC-FE-086 (ADR-031 Phase 3b) — 프로모션 폼 도입. TASK-PC-FE-124 — 날짜 Instant 변환(본 task 와 독립, 보존).

## Related Contracts

- 해당 없음(클라이언트 UX 한정 — producer 계약·와이어 변경 없음).

## Edge Cases

- `showPicker()` 는 user gesture 안에서만 허용되고 이미 열린 경우/미지원 시 throw → 공유 헬퍼가 feature-detect + try/catch 로 흡수(그대로 재사용).
- 모바일/터치는 원래 탭 시 picker 가 열리므로 동작 변화 없음(데스크톱 클릭 영역만 확대).

## Failure Scenarios

- `showPicker` 를 직접 호출하며 try/catch 를 빠뜨리면 일부 브라우저에서 콘솔 에러 → 공유 헬퍼 사용으로 회피(직접 호출 금지).
