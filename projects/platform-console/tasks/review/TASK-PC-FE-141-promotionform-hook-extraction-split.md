# Task ID

TASK-PC-FE-141

# Title

console-web `ecommerce-ops/PromotionForm` 로직 훅 추출 분할: 폼 상태·검증·confirm-gated submit·day→Instant 변환·create/update 뮤테이션을 `usePromotionForm` 훅으로 분리하고 컨테이너는 마크업 배선만 — render 출력·test-id·wire shape 불변(behavior-preserving)

# Status

review

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 커스텀 훅 추출, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-086(PromotionForm 도입 — ADR-031 Phase 3b 프로모션 등록/수정 폼) + TASK-PC-FE-124(promotion date → Instant 변환) + TASK-PC-FE-127(date 클릭 시 picker 오픈 — `showPickerOnClick`) + TASK-PC-FE-128(promotion producer 에러 매핑). 본 분할이 보존해야 하는 동작·test-id·`dayToInstant` 변환의 출처.
- **note (현 구조)**: [`PromotionForm.tsx`](../../apps/console-web/src/features/ecommerce-ops/components/PromotionForm.tsx)(346줄)는 단일 `'use client'` 컴포넌트가 (a) 8종 폼 상태(name/description/discountType/discountValue/maxDiscountAmount/maxIssuanceCount/startDate/endDate), (b) 다항 검증 술어(`formValid`), (c) create/update 뮤테이션(producer는 update에 PUT full-replace 사용 — PATCH 아님) + 에러 매핑 + `dayToInstant` 변환, (d) 전체 마크업을 모두 보유한다.
- **note (분할 형태 = 훅-only)**: PromotionForm은 ProductForm의 variant editor나 ShippingsScreen의 table 같은 **반복·분리 가능한 프레젠테이셔널 덩어리가 없는 평평한 단일 폼**이다. 표현부를 별도 컴포넌트로 추출하면 value/setter 16개를 prop-drilling 하게 되어 재사용 이득 없이 간접 비용만 늘어난다. 따라서 PC-FE-106(`useLedgerOpsState`)과 동일하게 **로직 훅만** 추출하고 마크업은 컨테이너에 유지하는 것이 올바른 ROI다.

# Goal

`PromotionForm`의 상태·로직을 `usePromotionForm` 커스텀 훅으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `usePromotionForm(existing)` — 8종 필드 상태, 검증(`formValid` 다항 술어), confirm-gated `onSubmit`/`confirmSubmit`/`cancelConfirm`, create/update 뮤테이션과 에러 매핑, `dayToInstant`(date → UTC Instant: start 00:00:00Z / end 23:59:59Z) 변환을 소유. 로직은 분할 전과 1:1 동일(검증 술어 순서·wire body·edit 시 Instant→date slice 포함).
- `PromotionForm` — 훅 호출 + 마크업 배선만 남는 컨테이너.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·label/필수 표식·할인유형 단위 표기((₩)/(%))·date 입력 `onClick={showPickerOnClick}`·create vs update 분기·검증 규칙·confirm 다이얼로그·PUT/POST wire body(특히 startDate/endDate Instant 변환)는 전부 기존과 동일.

# Scope

## In Scope

- **신규 `src/features/ecommerce-ops/components/use-promotion-form.ts`** — `PromotionForm`에서 상태·검증·핸들러·뮤테이션·에러 매핑·`dayToInstant`를 그대로 이전. 반환 객체(ids/fields/플래그/핸들러)로 컨테이너가 필요한 값/핸들러 노출.
- **`src/features/ecommerce-ops/components/PromotionForm.tsx`** — 훅으로 슬림화. 남는 마크업(8 필드 + 에러 alert + submit/cancel + ConfirmDialog)과 `inputCls`/`labelCls` 스타일 상수 유지. `PromotionFormProps` 공개 시그니처 불변.
- **Tests** — 기존 [`tests/unit/promotion-form-date-clickopen.test.tsx`](../../apps/console-web/tests/unit/promotion-form-date-clickopen.test.tsx)(시작일/종료일 클릭 → `showPicker`) 및 [`tests/unit/error-messages-promotion.test.ts`](../../apps/console-web/tests/unit/error-messages-promotion.test.ts)가 **무변경으로 green**이어야 한다. barrel `@/features/ecommerce-ops`의 `PromotionForm` export 경로·시그니처 불변이므로 import-site 수정 불필요.

## Out of Scope

- 표현부(필드 마크업)의 별도 프레젠테이셔널 컴포넌트 추출 — 위 note(훅-only) 사유로 의도적 제외(반복·재사용 없는 평평한 폼).
- 검증 규칙·wire body·`dayToInstant`·에러 코드 매핑의 **동작 변경**(순수 이동만).
- `PromotionForm` 공개 props/배럴 export 표면 변경.
- 다른 ecommerce-ops 폼(ProductForm[PC-FE-139]/TemplateForm 등) — 별건.
- 백엔드/계약/스펙/프로모션 API 변경.

# Acceptance Criteria

- [ ] `usePromotionForm` 추출 후 `PromotionForm`은 훅+마크업 배선만 남고, create/update 양 모드의 렌더 출력·DOM·모든 `data-testid`가 기존과 동일하다.
- [ ] create 모드: 전 필드 필수 검증(name/discountType/discountValue>0/maxDiscountAmount≥0/maxIssuanceCount>0/start·end), 성공 시 `/ecommerce/promotions` 라우팅·refresh 동작이 불변.
- [ ] update 모드: PUT full-replace body, edit 진입 시 producer Instant → `type="date"`용 `YYYY-MM-DD` slice, 성공 시 `/ecommerce/promotions/{id}` 라우팅·refresh가 불변.
- [ ] `dayToInstant` 변환(start→`T00:00:00Z`, end→`T23:59:59Z`)이 wire body의 startDate/endDate에 동일하게 적용된다.
- [ ] date 입력 클릭 시 `showPickerOnClick` 동작이 불변(`promotion-form-date-clickopen` 테스트 무변경 green), confirm-gated submit·인라인 에러 alert(`promotion-form-error`) 동작 불변.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 프로모션(ADR-031 Phase 3b) — read-only 소비, 변경 없음(동일 호출·동일 wire shape, update=PUT full-replace).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리).

# Related Contracts

- 변경 없음. 동일 프로모션 create/update API·동일 클라이언트 훅(`useCreatePromotion`/`useUpdatePromotion`)·동일 POST/PUT body(Instant 변환 포함).

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ecommerce-ops/components/PromotionForm.tsx` + `use-promotion-form.ts`. behavior-preserving 커스텀 훅 추출(훅-only, 표현부 컨테이너 유지).

# Architecture

- React 커스텀 훅 추출 패턴(PC-FE 분할 시리즈 / PC-FE-106 `useLedgerOpsState`와 동일 계열). fat `'use client'` 폼 컴포넌트 → (1) 상태·검증·submit·뮤테이션·변환 훅, (2) 얇은 배선 컨테이너. 평평한 비반복 폼이라 프레젠테이셔널 분리는 prop-drilling 비용만 키우므로 의도적으로 훅 경계만 도입. 상태 소유권은 훅(컨테이너 로컬)에 유지, 렌더 트리·RSC 경계 불변.

# Edge Cases

- create vs update: update는 `existing!.promotionId`로 PUT full-replace + 상세 라우팅, create는 POST + 목록 라우팅 — 훅 `confirmSubmit` 분기 그대로 이동.
- edit 진입 초기값: producer Instant(`2026-07-01T00:00:00Z`) → `slice(0,10)`로 date input 초기값, `maxDiscountAmount`는 null이면 `'0'` 기본 — 훅 `useState` 초기화 그대로.
- 할인값 단위 표기: discountType `FIXED`→(₩) / `PERCENTAGE`→(%) 라벨 — 컨테이너 마크업 유지(discountType 값 기반).
- date picker: 시작일/종료일 클릭 시 `showPickerOnClick`로 네이티브 피커 오픈(PC-FE-127) — 컨테이너 input `onClick` 유지, test가 가드.
- 숫자 입력 sanitize: discountValue/maxDiscountAmount/maxIssuanceCount는 `replace(/[^0-9]/g,'')` — 컨테이너 onChange 유지.
- 다항 검증 술어 순서·경계(`>0` vs `>=0`): discountValue>0, maxDiscountAmount≥0, maxIssuanceCount>0 — 훅 `formValid`에 동일 순서·경계 유지.

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → `promotion-form-date-clickopen` 등 기존 테스트 RED 또는 e2e 셀렉터 깨짐: AC로 test-id 전수 보존 가드, `vitest run`으로 회귀 확인.
- `dayToInstant` 변환이 추출 중 바뀌면(edge 분기·`Z` 누락) producer가 INVALID_PROMOTION_REQUEST로 거부 → 순수 이동, AC로 가드.
- 상태를 (가상의) 표현 컴포넌트로 내리면 입력 시 리셋 → 훅-only 분할로 상태는 컨테이너 로컬 훅에 유지(표현 분리 안 함).
- 검증 술어 경계가 바뀌면(`>` ↔ `>=`) create 검증 동작 변화 → 술어 순서·경계 그대로 이동.
- 잔여 미사용 import(useId/useState/useRouter/ApiError 등 컨테이너에서 제거) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC).

# Definition of Done

- [ ] `use-promotion-form.ts`(상태·검증·submit·뮤테이션·`dayToInstant`) 추출, `PromotionForm.tsx`는 배선만
- [ ] 렌더 출력·DOM·전체 `data-testid`·create/update 분기·검증·`dayToInstant`·date picker·confirm·wire body behavior-preserving
- [ ] `promotion-form-date-clickopen` + `error-messages-promotion` 무변경 green + 전체 vitest + tsc + lint clean, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
