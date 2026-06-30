# Task ID

TASK-PC-FE-142

# Title

console-web `ecommerce-ops/VariantEditor` 컨테이너/프레젠테이셔널 분할: 옵션 인라인 편집/추가/삭제 상태·3 뮤테이션을 `useVariantEditor` 훅으로, 편집 테이블을 `VariantTable`로, 추가 행을 `VariantAddRow`로 추출 — render 출력·test-id·wire shape 불변(behavior-preserving)

# Status

done

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 컴포넌트 분할, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-081(VariantEditor 도입 — § 2.4.10 #6 add / #7 update / #8 delete 상품 옵션 인라인 CRUD). 본 분할이 보존해야 하는 동작·test-id의 출처.
- **note (현 구조)**: [`VariantEditor.tsx`](../../apps/console-web/src/features/ecommerce-ops/components/VariantEditor.tsx)(301줄)는 단일 `'use client'` 컴포넌트가 (a) 인라인 편집 상태(editId/editName/editAddPrice/rowError) + 추가 상태(addName/addStock/addPrice/addError) + 삭제 확인 상태(toDelete/delError), (b) 3종 뮤테이션(add/update/delete)과 검증·에러 매핑, (c) 편집 가능 테이블 + 추가 행 + 삭제 ConfirmDialog 전체 마크업을 모두 보유한다. 로직과 두 개의 분리 가능한 표현 덩어리(테이블·추가 행)가 한 파일에 섞여 있다.
- **pattern**: container/presentational + 커스텀 훅 추출(PC-FE-139 ProductForm / PC-FE-140 ShippingsScreen과 동일 계열).

# Goal

`VariantEditor`를 **로직(훅) / 표현(테이블·추가 행) / 얇은 컨테이너** 계층으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `useVariantEditor(productId)` — 편집/추가/삭제 상태 슬라이스, 3종 뮤테이션(`add`/`update`/`del`), 검증(편집·추가)과 에러 매핑(`errMsg`), 핸들러(`startEdit`/`saveEdit`/`cancelEdit`/`submitAdd`/`openDelete`/`confirmDelete`/`cancelDelete`)를 소유. 로직은 분할 전과 1:1 동일(검증 경계·wire body 포함).
- `VariantTable` — 옵션 목록 테이블(행별 in-place 편집 토글: 옵션명/추가가격 입력, 재고는 읽기전용)을 표현 전용으로 렌더. 마지막 1개 삭제 비활성(`variants.length <= 1`) 가드 포함.
- `VariantAddRow` — 옵션 추가 인라인 행(옵션명/재고/추가가격 + 추가 버튼 + 에러)을 표현 전용으로 렌더.
- `VariantEditor` — 훅 호출 + 헤딩 + 테이블 + 추가 행 + 삭제 ConfirmDialog 배선만 남는 컨테이너.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·ARIA(aria-label)·편집 토글 분기·재고 읽기전용·추가가격 `toLocaleString('ko-KR')` 표시·숫자 입력 sanitize·검증 메시지·confirm(destructive tone)·add/update/delete wire body는 전부 기존과 동일.

# Scope

## In Scope

- **신규 `src/features/ecommerce-ops/components/use-variant-editor.ts`** — 편집/추가/삭제 상태·3 뮤테이션·검증·에러 매핑·핸들러를 그대로 이전. 반환 객체(`edit`/`openDelete`/`add`/`del` 슬라이스)로 테이블·추가 행·컨테이너가 필요한 값/핸들러 노출.
- **신규 `src/features/ecommerce-ops/components/VariantTable.tsx`** — 옵션 테이블 JSX(기존 `<table>` 블록)를 그대로 이동. `variants`/`edit`(편집 슬라이스)/`openDelete`/`inputCls`를 props로 받음. test-id(`variant-table`, `variant-row-{i}`, `variant-stock-{i}`, `variant-edit-name-{i}`, `variant-edit-addprice-{i}`, `variant-save-{i}`, `variant-cancel-{i}`, `variant-editbtn-{i}`, `variant-delete-{i}`, `variant-row-error-{i}`) 전부 보존.
- **신규 `src/features/ecommerce-ops/components/VariantAddRow.tsx`** — 추가 행 JSX(기존 `variant-add-row` 블록)를 그대로 이동. `add`(추가 슬라이스)/`inputCls`를 props로 받음. test-id(`variant-add-row`, `variant-add-name`, `variant-add-stock`, `variant-add-addprice`, `variant-add-submit`, `variant-add-error`) 전부 보존.
- **`src/features/ecommerce-ops/components/VariantEditor.tsx`** — 훅 + 두 표현 컴포넌트로 슬림화. 남는 마크업(헤딩 + 삭제 ConfirmDialog)과 `inputCls` 스타일 상수(두 표현 컴포넌트에 prop 전달) 유지. `VariantEditorProps` 공개 시그니처 불변.
- **Tests** — `src/features/ecommerce-ops/**` + `tests/unit/**`의 기존 vitest가 **무변경으로 green**이어야 한다. VariantEditor 전용 단위 테스트는 없으나 barrel export·test-id 보존으로 e2e/통합 셀렉터가 깨지지 않아야 한다(있다면). barrel `@/features/ecommerce-ops` export 경로·시그니처 불변이므로 import-site 수정 불필요.

## Out of Scope

- 재고 편집 추가(UpdateVariantRequest에 stock 필드 없음 — StockAdjustDialog 별도 surface) — 동작 변경 없음.
- 검증 경계·wire body·에러 코드 매핑의 **동작 변경**(순수 이동만).
- `VariantEditor` 공개 props/배럴 export 표면 변경.
- 다른 ecommerce-ops 컴포넌트 분할 — 별건.
- 백엔드/계약/스펙/variant API 변경.

# Acceptance Criteria

- [ ] `useVariantEditor`/`VariantTable`/`VariantAddRow` 추출 후 `VariantEditor`는 훅+표현 컴포넌트 배선만 남고, 렌더 출력·DOM·모든 `data-testid`가 기존과 동일하다.
- [ ] 편집: 행 "수정" → in-place 입력(옵션명/추가가격), "저장"은 검증(옵션명 non-empty·추가가격 정수≥0) 통과 시 update mutate, "취소"는 편집 종료, 행 에러는 `variant-row-error-{i}`로 표시 — 동작 불변.
- [ ] 추가: 옵션명·재고(정수≥0)·추가가격(정수≥0) 검증 후 add mutate, 성공 시 입력 리셋, 실패 시 `variant-add-error` 표시 — 동작 불변.
- [ ] 삭제: "삭제" → destructive ConfirmDialog(옵션명 표시), confirm 시 delete mutate. 마지막 1개 옵션은 삭제 버튼 비활성(`variants.length <= 1`, title 안내) — 동작 불변.
- [ ] 재고 읽기전용 표시·추가가격 `toLocaleString('ko-KR')`·숫자 입력 `replace(/[^0-9]/g,'')` sanitize가 불변.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10(#6 옵션 추가 / #7 수정 / #8 삭제) — read-only 소비, 변경 없음(동일 호출·동일 wire shape).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리).

# Related Contracts

- 변경 없음. 동일 variant add/update/delete API·동일 클라이언트 훅(`useAddVariant`/`useUpdateVariant`/`useDeleteVariant`)·동일 wire body.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ecommerce-ops/components/{VariantEditor,VariantTable,VariantAddRow}.tsx` + `use-variant-editor.ts`. behavior-preserving 컨테이너/프레젠테이셔널 + 커스텀 훅 분할.

# Architecture

- React 컨테이너/프레젠테이셔널 + 커스텀 훅 추출 패턴(PC-FE 분할 시리즈 / PC-FE-139 / PC-FE-140과 동일 계열). fat `'use client'` 컴포넌트 → (1) CRUD 상태·뮤테이션 훅, (2) 편집 테이블·(3) 추가 행 표현 컴포넌트, (4) 얇은 배선 컨테이너. 편집 입력은 controlled(부모 훅 소유 state/setter를 슬라이스로 전달)로 유지해 표현 추출이 상태 흐름에 영향을 주지 않음. RSC 경계·렌더 트리 불변.

# Edge Cases

- 편집 토글: 한 행만 편집(editId 단일) — 다른 행 "수정" 시 직전 편집 종료(startEdit가 editId 교체) 동작 그대로.
- 마지막 옵션 삭제 가드: `variants.length <= 1`이면 삭제 버튼 disabled + title 안내 — 테이블에 `variants` 전달, 조건 그대로.
- 검증 경계: 편집 추가가격 정수≥0, 추가 재고·추가가격 정수≥0, 옵션명 non-empty — 훅 검증 술어 그대로 이동.
- 추가 성공 시 입력 3종 리셋(`setAddName('')` 등), 편집 성공 시 editId/rowError 리셋 — 훅 onSuccess 그대로.
- 추가가격 표시: 비편집 시 `toLocaleString('ko-KR')`, 편집 시 raw 입력 — 테이블 분기 그대로.
- 삭제 확인 description: `"{optionName}" 옵션을 삭제합니다.` — 훅 `del.description` 파생으로 이동(toDelete null이면 빈 문자열).

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → e2e/통합 셀렉터 깨짐: AC로 test-id 전수 보존 가드, `vitest run`으로 회귀 확인.
- 상태를 표현 컴포넌트로 내리면(props-lift 누락) 편집/추가 입력 시 리셋 → 상태는 훅(부모)에 유지, 테이블/추가 행은 controlled 표현 전용.
- 검증 경계가 바뀌면(`<0` ↔ `<=0` 등) 편집/추가 동작 변화 → 술어 그대로 이동, AC로 가드.
- 편집 controlled input의 setter 시그니처(`Dispatch<SetStateAction<string>>`)가 어긋나면 tsc RED → 표현 컴포넌트 prop 타입을 훅 슬라이스와 일치시킴, `tsc --noEmit`로 가드.
- 잔여 미사용 import(useState/ApiError 등 컨테이너에서 제거) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC).

# Definition of Done

- [ ] `use-variant-editor.ts`(상태·3 뮤테이션·검증·핸들러) + `VariantTable.tsx`(편집 테이블) + `VariantAddRow.tsx`(추가 행) 추출, `VariantEditor.tsx`는 배선만
- [ ] 렌더 출력·DOM·전체 `data-testid`·편집/추가/삭제 분기·검증·재고 읽기전용·confirm·wire body behavior-preserving
- [ ] 기존 vitest 무변경 green + tsc + lint clean, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
