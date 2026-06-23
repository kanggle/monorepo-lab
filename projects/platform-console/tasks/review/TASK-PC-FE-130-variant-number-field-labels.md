# TASK-PC-FE-130 — 상품 등록/옵션 추가 폼의 숫자 칸(재고·추가 가격)이 기본값 0에 가려 무슨 칸인지 안 보이는 문제

- **Status**: review
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (소형 UX/접근성 폼 개선 — 동작 불변)

## Goal

콘솔 E-Commerce 운영의 **상품 등록 폼**(`ProductForm`, `/ecommerce/products/new`)과 **상품 상세의 옵션 추가 행**(`VariantEditor`)에서, 옵션(variant)의 숫자 입력 2개(**재고 `stock`**, **추가 가격 `additionalPrice`**)가 초기값 `'0'`으로 미리 채워져 있어 각 칸의 `placeholder`("재고" / "추가 가격")가 표시되지 않는 문제를 고친다. 운영자가 어느 칸이 재고이고 어느 칸이 추가 가격인지 즉시 구분할 수 있게 한다.

근본 원인: HTML `placeholder`는 입력값이 비어 있을 때만 보인다. 두 칸의 초기 state 가 `'0'`(빈 문자열이 아님)이라 placeholder 가 항상 가려진다. 게다가 `ProductForm` 의 옵션 fieldset 에는 칼럼 헤더 행이 전혀 없어, placeholder 가 보이더라도 값을 입력하는 순간 다시 무슨 칸인지 알 수 없다(`VariantEditor` 는 `재고 / 추가 가격` 테이블 헤더가 이미 있어 덜 심함).

## Scope

**In scope** (console-web only):

1. `features/ecommerce-ops/components/ProductForm.tsx`
   - 옵션 draft 초기값 `stock: '0' / additionalPrice: '0'` → `''`(초기 row 및 `addVariantRow` 양쪽).
   - 옵션 fieldset 에 사라지지 않는 칼럼 헤더 1줄(`옵션명 / 재고 / 추가 가격`) 추가(grid 컬럼 정렬 일치, 4번째 삭제-버튼 칼럼은 빈 헤더).
2. `features/ecommerce-ops/components/VariantEditor.tsx`
   - "옵션 추가" 행의 `addStock`/`addPrice` 초기값 `'0'` → `''`, 추가 성공 후 reset 도 `''` 로(placeholder 복원). (테이블 헤더는 이미 존재 → 헤더 추가 불필요.)
3. `tests/unit/ProductForm.test.tsx` — 신규 회귀 테스트(placeholder 노출 + 칼럼 헤더 존재 + 미입력 칸이 0 으로 전송되는 동작 보존).

**Out of scope**: 백엔드/계약(와이어 불변 — 미입력=`Number('')`=0, 기존과 동일 전송), 검증 규칙 변경(`stock>=0`·`additionalPrice>=0` 그대로), 가격(`price`)·썸네일 등 다른 필드, 비-ecommerce-ops 폼.

## Acceptance Criteria

- **AC-1 — placeholder 노출.** 상품 등록 폼 최초 진입 및 "옵션 추가"로 새 행을 더했을 때, 재고/추가 가격 칸이 비어 있고 placeholder("재고" / "추가 가격")가 보인다(초기값 0 아님).
- **AC-2 — 칼럼 헤더.** `ProductForm` 옵션 fieldset 에 `옵션명 / 재고 / 추가 가격` 칼럼 헤더가 보이며, 값 입력 후에도 칸 식별이 유지된다.
- **AC-3 — 동작 보존(미입력=0).** 재고/추가 가격을 비운 채 등록하면 기존과 동일하게 `stock:0` / `additionalPrice:0` 으로 전송된다(`RegisterProductRequest` 와이어 불변). 음수/비정수는 기존 검증대로 차단.
- **AC-4 — VariantEditor 일관성.** 상품 상세 "옵션 추가" 행도 초기/리셋 시 두 숫자 칸이 비어 placeholder 가 보이며, 기존 add-variant POST 동작(미입력 추가 가격=0)이 유지된다.
- **AC-5 — 게이트.** console-web `pnpm lint` + `tsc --noEmit` + `vitest run` GREEN(신규 테스트 포함, 기존 ProductsScreen 테스트 무회귀).

## Related Specs

- console-integration-contract § 2.4.10 — ecommerce 운영 surface(상품 등록은 `/new`, 옵션은 상세에서 inline). 옵션 = `optionName` + `stock` + `additionalPrice`.
- TASK-PC-FE-081 — ecommerce-ops 상품/옵션 등록·편집 폼 최초 도입(이 폼의 출처).

## Related Contracts

- 해당 없음(클라이언트 폼 표현 한정 — producer 계약·와이어 변경 없음. 미입력 칸은 종전과 동일하게 0 직렬화).

## Edge Cases

- 미입력(`''`) → `Number('')===0` 이라 검증·전송 모두 0. 사용자가 명시적으로 0 을 입력해도 동일.
- 옵션 행을 여러 줄 추가해도 칼럼 헤더는 fieldset 상단 1줄로 충분(행마다 반복 라벨 불필요).
- `VariantEditor` update(수정) 행은 stock 을 편집하지 않으므로(별도 StockAdjustDialog) 영향 없음 — add 행에만 적용.

## Failure Scenarios

- 초기값만 비우고 검증 로직을 함께 손대면 음수/비정수 차단이 깨질 수 있음 → 검증은 그대로 두고 초기 state 문자열만 변경(AC-3 가드).
- 칼럼 헤더 grid 템플릿을 입력 행과 다르게 두면 헤더-칸 정렬이 틀어짐 → 동일 `sm:grid-cols-[1fr_6rem_8rem_auto]` 사용.
