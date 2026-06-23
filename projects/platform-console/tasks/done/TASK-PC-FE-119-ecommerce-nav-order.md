# TASK-PC-FE-119 — 콘솔 E-Commerce 사이드바 메뉴 순서 재배열

- **Status**: done
- **Project**: platform-console
- **App**: console-web (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (single-file 순서 변경)

## Goal

콘솔 사이드바 E-Commerce 드릴의 자식 메뉴 순서를 운영 흐름에 맞게 재배열한다. 운영자가 상품→주문→배송으로 이어지는 처리 동선을 먼저 보고, 부가(프로모션/사용자/셀러/알림)가 뒤따르도록 한다.

- **현재**: 운영 · 상품 · 주문 · 사용자 · 프로모션 · 배송 · 알림 · 셀러
- **변경**: 운영 · 상품 · 주문 · **배송 · 프로모션 · 사용자 · 셀러 · 알림**

href/testid/label은 일체 변경 없음 — 배열 순서만 재배열한다(라우트·딥링크·테스트 셀렉터 불변).

## Scope

**In scope** (console-web only):

1. `src/shared/ui/ConsoleSidebarNav.tsx` — `ecommerce` 드릴 `children` 배열의 항목 순서만 재배열.

**Out of scope**: 라우트/페이지 변경, href·testid·label 변경, 다른 도메인(wms/scm/finance/erp/iam) 메뉴, 메뉴 항목 추가/삭제.

## Acceptance Criteria

- **AC-1 — 순서.** 사이드바 E-Commerce 자식이 위→아래로 운영 · 상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · 알림 순으로 렌더된다.
- **AC-2 — 불변식.** 각 항목의 href·testid·label 은 변경 전과 동일(`nav-ecommerce-ops`/`-products`/`-orders`/`-shippings`/`-promotions`/`-users`/`-sellers`/`-notifications`). 항목 추가/삭제 없음.
- **AC-3 — 게이트.** 기존 ecommerce nav 단위테스트 전건 GREEN(`getByTestId` 기반이라 순서 무관 — 회귀 없음) + `tsc --noEmit` clean + `next lint` clean. CI `vitest run` GREEN.

## Related Specs

- 없음(순수 프레젠테이션 순서 — 스펙/계약 영향 없음).

## Related Contracts

- 없음.

## Edge Cases

- nav 단위테스트(ecommerce-nav / -orders / -users / -promotions / -shippings / -sellers / -notifications)는 `getByTestId`로 href·active 상태만 단언 → DOM 형제 순서에 의존하지 않으므로 재배열에 영향 없음(로컬 vitest 27건 통과 확인).

## Failure Scenarios

- 항목 누락/중복 시 사이드바에서 메뉴가 사라지거나 중복 → AC-1/AC-2 가 회귀 검출(testid 8종 모두 정확히 1회 존재).
