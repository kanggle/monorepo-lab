# Task ID

TASK-PC-FE-139

# Title

console-web `ecommerce-ops/ProductForm` 컨테이너/프레젠테이셔널 분할: 폼 상태·검증·confirm-gated submit 로직을 `useProductForm` 훅으로, register-mode 옵션(variant) 에디터를 `ProductVariantsFieldset` 프레젠테이셔널 컴포넌트로 추출 — render 출력·test-id·wire shape 불변(behavior-preserving)

# Status

review

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 컴포넌트 분할, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-081(ProductForm 도입 — § 2.4.10 #3/#4 상품 등록/수정 폼) + TASK-PC-FE-130(register-mode 옵션 number 필드 빈값-placeholder + 영구 컬럼 헤더 — 본 분할이 보존해야 하는 동작·test-id의 출처).
- **note (현 구조)**: [`ProductForm.tsx`](../../apps/console-web/src/features/ecommerce-ops/components/ProductForm.tsx)(369줄)는 단일 `'use client'` 컴포넌트가 (a) 6종 폼 상태 + variant draft 배열, (b) 검증(name/price/variants), (c) register/update 뮤테이션 + 409/422 에러 surfacing, (d) 전체 마크업(필드 + register-mode 옵션 fieldset + ConfirmDialog)을 모두 보유한다. 로직과 렌더가 한 파일에 섞여 단일 책임 위반·테스트 표면 비대.

# Goal

`ProductForm`을 **로직(훅) / 표현(프레젠테이셔널) / 얇은 컨테이너** 3계층으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `useProductForm(existing)` — 모든 필드 상태, 검증(`formValid`), `setVariant`/`addVariantRow`/`removeVariantRow`, confirm-gated `onSubmit`/`confirmSubmit`/`cancelConfirm`, register/update 뮤테이션과 409 CONFLICT/기타 에러 매핑을 소유. 로직은 분할 전과 1:1 동일(연산자 우선순위·검증식·wire body 포함).
- `ProductVariantsFieldset` — register-mode 옵션 에디터(옵션명/재고/추가 가격 행 + 영구 컬럼 헤더 + 추가/삭제)를 표현 전용으로 렌더. 상태 소유권은 부모(훅)에 유지, props로만 받음.
- `ProductForm` — 훅 호출 + 마크업 배선만 남는 컨테이너.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·ARIA/label·register vs update 분기·검증 규칙·confirm 다이얼로그·POST/PATCH wire body(특히 빈 재고/추가가격 → 0)는 전부 기존과 동일.

# Scope

## In Scope

- **신규 `src/features/ecommerce-ops/components/use-product-form.ts`** — `ProductForm`에서 상태·검증·핸들러·뮤테이션·에러 매핑을 그대로 이전. `VariantDraft` 타입도 이 모듈로 이동(여기서 export). 반환 객체로 컨테이너가 필요한 값/핸들러 노출.
- **신규 `src/features/ecommerce-ops/components/ProductVariantsFieldset.tsx`** — register-mode 옵션 fieldset JSX(기존 369줄 파일의 옵션 `<fieldset>` 블록)를 그대로 이동. `variants`/`setVariant`/`addVariantRow`/`removeVariantRow`/`inputCls`를 props로 받음. test-id(`product-form-variants`, `product-form-variant-header`, `product-form-variant-{i}`, `-name-/-stock-/-addprice-/-remove-{i}`, `product-form-variant-add`) 전부 보존.
- **`src/features/ecommerce-ops/components/ProductForm.tsx`** — 훅 + fieldset로 슬림화. 남는 마크업(name/description/price/thumbnail/status 필드 + 에러 alert + submit/cancel + ConfirmDialog)과 `inputCls`/`labelCls` 스타일 상수 유지. `ProductFormProps` 공개 시그니처 불변.
- **Tests** — 기존 [`tests/unit/ProductForm.test.tsx`](../../apps/console-web/tests/unit/ProductForm.test.tsx)(register-mode 빈 number 필드·영구 헤더·신규 행 빈값·빈값 0 등록 4 케이스)가 **무변경으로 green**이어야 한다. barrel `@/features/ecommerce-ops`의 `ProductForm` export 경로·시그니처 불변이므로 import-site 수정 불필요.

## Out of Scope

- update-mode VariantEditor(상세 페이지 인라인 옵션 편집 — `VariantEditor.tsx`) 변경. 본 폼은 register-mode 옵션만 다룸.
- 검증 규칙·wire body·에러 코드 매핑의 **동작 변경**(순수 이동만).
- `ProductForm` 공개 props/배럴 export 표면 변경.
- 다른 ecommerce-ops 폼(PromotionForm/TemplateForm 등) 분할 — 별건(필요 시 후속 PC-FE).
- 백엔드/계약/스펙/상품 API 변경.

# Acceptance Criteria

- [ ] `useProductForm`/`ProductVariantsFieldset` 추출 후 `ProductForm`은 훅+fieldset 배선만 남고, register/update 양 모드의 렌더 출력·DOM·`data-testid`가 기존과 동일하다.
- [ ] register 모드: 이름/가격/옵션 검증, 옵션 행 추가·삭제(최소 1행 유지), 빈 재고/추가가격이 POST wire에서 `0`으로 직렬화되는 동작이 불변(`tests/unit/ProductForm.test.tsx` 4 케이스 무변경 green).
- [ ] update 모드: 부분 PATCH body(빈 필드 → `undefined`), 성공 시 `/ecommerce/products/{id}` 라우팅·refresh, 409 CONFLICT 시 conflict 플래그+메시지 분기가 불변.
- [ ] confirm-gated submit(다이얼로그 open → confirm 시 mutate, cancel 시 에러/conflict 리셋)·인라인 에러 alert(`product-form-error`) 동작 불변.
- [ ] `VariantDraft` 타입은 `use-product-form.ts`로 이동되고 fieldset/컨테이너가 동일 타입을 참조한다(중복 정의 없음).
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10(#3 상품 등록 / #4 상품 수정) — read-only 소비, 변경 없음(동일 호출·동일 wire shape).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리).

# Related Contracts

- 변경 없음. 동일 상품 register/update API·동일 클라이언트 훅(`useRegisterProduct`/`useUpdateProduct`)·동일 POST/PATCH body.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ecommerce-ops/components/{ProductForm,ProductVariantsFieldset}.tsx` + `use-product-form.ts`. behavior-preserving 컨테이너/프레젠테이셔널 + 커스텀 훅 분할.

# Architecture

- React 컨테이너/프레젠테이셔널 + 커스텀 훅 추출 패턴(PC-FE god-file 분할 시리즈 PC-FE-098~112 / PC-FE-106 `useLedgerOpsState`와 동일 계열). fat `'use client'` 컴포넌트 → (1) 상태·로직 훅, (2) 표현 전용 fieldset, (3) 얇은 배선 컨테이너. 상태 소유권은 훅(부모)에 유지해 fieldset 추출이 상태 흐름에 영향을 주지 않음. RSC 경계·렌더 트리 불변.

# Edge Cases

- register 모드 옵션 1행만 남았을 때 삭제 버튼 disabled(최소 1행 유지) — fieldset의 `variants.length <= 1` 가드 그대로 이동.
- 빈 재고/추가가격: UI는 빈 문자열(placeholder 노출), 제출 시 `Number('')→0`이 아니라 `Number(v.stock)` 경로로 0 직렬화 — 훅의 `confirmSubmit` register body 매핑 불변.
- update 모드: register-mode 옵션 fieldset 미렌더(`!isEdit` 가드) — 컨테이너에 가드 유지, fieldset는 register에서만 마운트.
- 409 CONFLICT: `conflict` 플래그 set + CONFLICT 메시지, 그 외 코드는 일반 에러 메시지 — `handleError` 분기 그대로 이동.
- 검증식 연산자 우선순위(`isEdit || (variants.length > 0 && variants.every(...))`) — 이동 시 괄호로 의미 보존(동작 동일).

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → 기존 unit 테스트 RED 또는 e2e 셀렉터 깨짐: AC로 test-id 전수 보존 가드, `vitest run`으로 회귀 확인.
- 검증식 괄호 누락으로 `&&`/`||` 우선순위가 바뀌면 register 검증 동작 변화 → 명시 괄호로 보존, register 4 케이스 테스트가 가드.
- 상태를 fieldset로 내리면(props-lift 누락) 옵션 입력 시 리셋 → 상태는 훅(부모)에 유지, fieldset는 표현 전용.
- 잔여 미사용 import(useId/useState/useRouter 등 컨테이너에서 제거) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC).
- wire body 매핑이 추출 중 바뀌면(빈값→0, trim, undefined 분기) PATCH/POST 계약 위반 → 순수 이동만, 빈값-0 테스트가 가드.

# Definition of Done

- [ ] `use-product-form.ts`(상태·검증·submit·뮤테이션·`VariantDraft`) + `ProductVariantsFieldset.tsx`(register 옵션 에디터) 추출, `ProductForm.tsx`는 배선만
- [ ] 렌더 출력·DOM·전체 `data-testid`·register/update 분기·검증·confirm·wire body behavior-preserving
- [ ] `tests/unit/ProductForm.test.tsx` 무변경 green + tsc + lint clean, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
