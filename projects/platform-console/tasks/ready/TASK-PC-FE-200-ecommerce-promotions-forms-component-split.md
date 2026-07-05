# TASK-PC-FE-200 — E-Commerce 프로모션·셀러·알림 화면 + 폼 god-file 컴포넌트 분할 (ecommerce-ops · 2/2)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

PC-FE-199(개요·리스트 화면)에 이은 E-Commerce 콘솔 `ecommerce-ops` god-file 분할 **전면 sweep 2/2 — 프로모션/셀러/알림 화면 + 상세 + 폼 + 공유 필드**. 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱 적용. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- `PromotionsScreen.tsx`(~340) · `PromotionForm.tsx`(~258) · `ImageUploadField.tsx`(~246) · `TemplateForm.tsx`(~216) · `PromotionDetail.tsx`(~211) · `SellersScreen.tsx`(~209) · `NotificationsScreen.tsx`(~208)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration(state·validation·submit·delete confirm-gate·list-state 분기·페이지네이션/필터 state·upload 오케스트레이션)을 유지하는 얇은 컨테이너로 축소. 모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처 불변. ecommerce 컨벤션(DetailHeader ghost 버튼·`dl` 순서 명칭→상태→식별자→날짜) 보존.

**폼 판단(hook-only, PC-FE-196 선례)**: `PromotionForm`·`TemplateForm`은 이미 상태/검증/submit 전부를 `usePromotionForm`/`useTemplateForm`(PC-FE-141/143)에 위임한 flat·비반복 필드셋 → **분할하지 않음**(프레젠테이션 강제 추출 시 ~16-prop drilling만 증가, reuse 없음 = 순 구조 손실). 얇은 hook-wired 컨테이너로 유지.

**Out of scope:** `api/`·proxy 라우트·producer·contract·테스트 무변경. PC-FE-199 파일(EcommerceOverview/Products/Orders/Users + 추출 테이블)은 shared helper READ만. `index.ts` barrel 공개 API 불변.

## Acceptance Criteria
- **AC-1** 대상 god-file이 의미 있게 축소(폼 2개는 hook-only로 정당하게 무변경)되고, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿 포함: `promotion-row-${i}`·`promotion-row-status-${i}`·`promotion-detail-${i}`·`promotion-delete-${i}`·`seller-row-${i}`·`seller-detail-${i}`·`notification-row-${i}`·`notification-edit-${i}`·`image-upload-progress`[`role="progressbar"`+aria-valuemin/max/now])·aria·요소 순서 보존.
- **AC-3** `index.ts` 공개 API 불변.
- **AC-4** `tsc --noEmit` 0 + `next lint` 0 + `vitest`(ecommerce 전 스위트) green, 회귀 0. 신규 테스트 불필요.

## Edge Cases / Failure Scenarios
- **delete confirm-gate 보존**: PromotionsScreen·PromotionDetail의 삭제가 컨테이너 confirm-gate로 위임(`onDelete`) — ConfirmDialog·delete state 컨테이너 잔류.
- **ImageUploadField는 orchestration-dominant**(validate/pick/reset + 3-step async upload) → 프레젠테이션은 진행바(`ImageUploadProgress`)만 추출, upload state/handlers/refs 잔류. `phase === 'uploading' &&` 가드는 컨테이너 잔류해 렌더 조건 동일.
- **PromotionDetail**: `PromotionDetailFields`(`<dl>` 그리드)만 추출, DetailHeader ghost 버튼·coupon-issue 오케스트레이션 컨테이너 잔류.
- **폼 hook-only 정당성**: PC-FE-196(useCreateOperatorForm)과 동형 판단 — flat 폼 강제 분할 금지.

## Related
- 미러: TASK-PC-FE-199 (ecommerce 개요·리스트 분할), PC-FE-196 (폼 hook-only 선례).
- 컨벤션: [[proj_console_ecommerce_detail_conventions]].
- 기존 테스트(계약): `tests/unit/{promotions-*,sellers-*,notifications-*,template-*,promotion-detail,promotion-form,image-upload-field,image-*}.test.tsx` + ecommerce nav/proxy.
