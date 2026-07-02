# TASK-FE-084 — web-store 주문 내역 카드를 3줄 → 2줄 레이아웃으로 압축

- **Status**: done
- **DONE (2026-07-02, 3-dim verified — PR #2111 `d9e840819`)**: state=MERGED + origin/main tip=`d9e840819` 일치 + pre-merge failing=0(Frontend unit tests[Node20, 기존 OrderCard 테스트 무수정]+lint&build+E2E smoke GREEN). 주문 내역 카드 3줄→2줄(날짜+상태 / 상품명·총액) 압축.
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)
- **IMPLEMENTED (2026-07-02)**: `src/entities/order/ui/OrderCard.tsx` — 왼쪽 3-`<p>` 스택 → 2줄 재구성. 1줄=날짜(좌)+상태 배지(우) space-between, 2줄=상품명 span(강조)+` · `+총액 span(보조), 한 줄 ellipsis truncation. 상품명/총액 별도 `<span>` 으로 정확 문자열 매칭 보존 → 기존 order-card/order-ui/orders-page 테스트 무수정. 순수 프레젠테이션(데이터·계약 무변경). ⚠️로컬 vitest 불가(Node24↔vitest4)→CI Node20 권위.

## Goal

web-store 주문 내역(`/my/orders`, `/orders`) 목록의 `OrderCard` 는 현재 왼쪽 영역이 **3줄**로 쌓여 있다:

1. 주문 생성 날짜
2. 첫 상품명 (`… 외 N건`)
3. 주문 총액 (`…원`)

이를 **2줄**로 압축해 카드 높이를 줄이고 스캔성을 높인다. 선택된 레이아웃:

- **1줄**: 날짜(좌) + 주문 상태 배지(우) — 기존 space-between 유지
- **2줄**: `첫 상품명 외 N건 · 총액원` (상품명=강조, 총액=보조 색)

## Scope

**In scope** (web-store only):

1. `src/entities/order/ui/OrderCard.tsx` — 왼쪽 3-`<p>` 스택을 (a) 날짜+상태 배지의 상단 행, (b) 상품명·총액을 ` · ` 로 잇는 하단 한 줄로 재구성. 상품명과 총액은 각각 **별도 `<span>`** 으로 렌더(테스트의 정확 문자열 매칭 보존 + 강조/보조 스타일 분리). 상품명 truncation(ellipsis) 유지.

**Out of scope**: 주문 상세 페이지(`OrderDetailView`), 카드가 링크로 이동하는 대상, `OrderSummary` 계약, 다른 목록/카드 컴포넌트. 데이터·API 무변경(순수 프레젠테이션).

## Acceptance Criteria

- **AC-1 — 2줄 렌더.** 카드 왼쪽이 정확히 2줄로 렌더된다: (1줄) 날짜 + 상태 배지, (2줄) `상품명 외 N건 · 총액원`.
- **AC-2 — 정보 보존.** 날짜·상품명·나머지 건수(`외 N건`)·총액·상태 배지가 모두 표시된다(누락 없음).
- **AC-3 — 상품 1건.** `itemCount === 1` 이면 `외 N건` 없이 상품명만 표시.
- **AC-4 — 상품명 없음.** `firstItemName` 이 없으면 상품명·구분자(`·`)를 렌더하지 않고 총액만 2줄에 표시(상태·날짜는 유지).
- **AC-5 — 긴 상품명.** 긴 상품명은 ellipsis 로 잘린다(줄바꿈으로 3줄로 되돌아가지 않음).
- **AC-6 — 게이트.** web-store 프런트 유닛(vitest) GREEN — 기존 `order-card.test.tsx` / `order-ui.test.tsx` / `orders-page.test.tsx` 무수정 통과. ⚠️ 로컬 vitest 불가(Node24↔vitest4) → CI Node20 권위.

## Related Specs

- `specs/services/web-store/architecture.md` — 주문 내역 화면(프레젠테이션 조정, 데이터 흐름 무변경).

## Related Contracts

- 없음(`OrderSummary` 타입·주문 API 무변경, 순수 UI).

## Edge Cases

- 상품명 없음(`firstItemName` falsy): 2줄에 총액만. 구분자 미출력.
- 상품 1건: `외 N건` 미출력.
- 매우 긴 상품명: `overflow: hidden` + `text-overflow: ellipsis` + `white-space: nowrap` 로 한 줄 고정(2줄 유지).
- 총액 0원 등 경계값: 표시 로직 무변경(기존 `toLocaleString`).

## Failure Scenarios

- 상품명·총액을 하나의 텍스트 노드로 합치면 `order-card.test.tsx` 의 정확 문자열 매칭(`getByText('… 외 2건')`)이 깨짐 → **별도 `<span>`** 으로 분리해 각 요소의 textContent 를 보존.
- 상품명 truncation 을 빼면 긴 이름이 줄바꿈되어 3줄로 회귀 → ellipsis 스타일 유지로 방지.
