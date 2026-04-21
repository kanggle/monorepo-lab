# Task ID

TASK-FE-064

# Title

장바구니 인증 필수 — 비로그인 시 카트 접근·담기 차단 및 로그아웃 시 자동 클리어

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

비로그인 사용자에게는 장바구니를 제공하지 않는다. "담기", `/cart` 접근, 헤더 카트 노출을 모두 인증 상태에 연동한다. 로그아웃 시 클라이언트 카트 상태를 즉시 비워, 다음 사용자/비로그인 세션이 이전 카트를 물려받지 않도록 한다.

# Scope

## In Scope

- 상품 상세 "장바구니 담기" 버튼: 비로그인 시 클릭하면 로그인 페이지로 리디렉트 (원위치 복귀 포함). 카트에 추가하지 않는다.
- `/cart` 페이지 라우트: 비로그인 시 로그인 페이지로 리디렉트.
- 헤더/레이아웃의 카트 아이콘·카운트 뱃지: 비로그인 상태에서는 숨김 또는 disabled 처리.
- 로그아웃 플로우(`AuthProvider.logout`)에서 `clearCart()` 호출 + localStorage `'cart'` 삭제.
- 관련 유닛/컴포넌트 테스트 추가: 비로그인 상태 가드, 로그아웃 시 카트 클리어.

## Out of Scope

- 서버 사이드 카트 저장/동기화 (별도 use case로 추후 검토)
- 게스트 카트 → 로그인 후 병합 로직
- 백엔드 서비스 변경
- 카트 상태를 쿠키/세션 기반으로 옮기는 작업

# Acceptance Criteria

- [ ] 비로그인 상태에서 상품 상세의 "장바구니 담기" 버튼을 클릭하면 로그인 페이지로 이동하고, 카트 localStorage에 **항목이 추가되지 않는다**.
- [ ] 로그인 성공 후 원래 있던 페이지로 복귀한다.
- [ ] 비로그인 상태에서 `/cart` 직접 접근 시 로그인 페이지로 리디렉트된다.
- [ ] 비로그인 상태에서는 헤더의 카트 아이콘/카운트 뱃지가 렌더되지 않는다.
- [ ] 로그아웃하면 즉시 카트가 비워지고(`localStorage.getItem('cart')`는 `null` 또는 빈 배열), 이후 동일 브라우저에서 다시 로그인해도 이전 항목이 복원되지 않는다.
- [ ] 기존에 localStorage에 남아있던 카트 데이터가 있어도 **비로그인 방문 시에는 노출되지 않는다** (첫 로드 시 인증 상태 확인 후에만 카트 표시).

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification.

- `specs/use-cases/cart-and-order.md` (UC-0 신규 추가됨)
- `specs/services/web-store/overview.md` (cart 항목 인증 필수로 개정됨)
- `specs/rules/domains/ecommerce.md` (M9: 주문 후 카트 명시적 클리어)

# Related Skills

- `.claude/skills/frontend/` (해당되는 스킬이 있으면 로드)

# Related Contracts

- 없음 (백엔드 API 변경 없음)

# Target App

- `apps/web-store`

# Implementation Notes

- 현재 카트 상태: [apps/web-store/src/features/cart/model/cart-context.tsx](apps/web-store/src/features/cart/model/cart-context.tsx) — localStorage 키 `'cart'` 사용.
- 현재 인증 상태: [apps/web-store/src/features/auth/model/auth-context.tsx](apps/web-store/src/features/auth/model/auth-context.tsx) — `logout()` 에서 현재 `clearCart()` 미호출.
- 권장 접근 — `AuthProvider` 안에서 `useEffect`로 `isAuthenticated` 변화를 감지하지 말고, **`logout()` 내부에서 명시적으로 카트 클리어 이벤트를 디스패치**. `CartProvider`가 그 이벤트를 구독하거나, `useCart` 훅에서 `isAuthenticated` 상태를 초기 로드 시 확인 후 적용.
  - 단순한 방법: `logout()` 내부에서 `localStorage.removeItem('cart')` 직접 호출 후 `CartProvider` 상태도 리셋할 수 있게 커스텀 이벤트(`'cart:clear'`) 또는 `window.dispatchEvent(new Event('cart:clear'))` 패턴 사용.
  - 또는 `CartProvider`가 `AuthContext`를 구독해 `isAuthenticated === false` 전환 시 `setItems([])`.
- `/cart` 가드: 기존 `useRequireAuth()` 훅이 있다면 재사용. 없으면 `/checkout` 등 기존 보호 라우트 패턴을 따른다.
- "담기" 버튼 가드: 상품 상세 컴포넌트에서 `isAuthenticated` 체크 후 미인증이면 `router.push('/login?redirect=...')`.
- 헤더 카트 노출: `isAuthenticated` 기반 조건부 렌더.
- 초기 로드 시 카트 복원: `CartProvider`의 `loadCart()` 호출 전에 인증 상태 확정 대기. 인증 안 됐으면 `setItems([])`로 초기화.

# Edge Cases

- 로딩 중(인증 상태 확인 중): 카트 UI가 flicker 되지 않도록 skeleton 또는 hidden 유지.
- 토큰 만료로 세션이 풀리는 케이스: `AuthProvider`가 `setState({ isAuthenticated: false })`로 전환할 때도 카트 클리어가 발동해야 한다.
- 이미 로그인된 상태에서 새로고침: 카트가 정상 복원된다.
- 이전 세션에서 localStorage에 남아있던 카트 데이터: 비로그인 첫 방문 시 반드시 숨겨지고, 로그아웃 시 확실히 제거.

# Failure Scenarios

- 리디렉트 루프 (로그인 → /cart → /login) 방지: redirect 쿼리 파라미터 guard.
- `logout()` API 호출 실패 시에도 클라이언트 측 카트 클리어는 **반드시** 실행 (finally 블록 등).
- localStorage 접근 실패(프라이빗 모드 등): try-catch로 fallback.

# Test Requirements

- 컴포넌트 테스트:
  - `CartProvider` — 비인증 상태에서 `loadCart()` 호출 시 아무 것도 복원하지 않음.
  - `useCart().addItem()` — 비인증 상태에서 호출 시 no-op + 로그인 페이지 이동 side effect (Mock).
- 플로우 테스트:
  - 비로그인 사용자가 `/cart` 진입 → `/login`으로 리디렉트 확인.
  - 로그아웃 → localStorage `'cart'` 키 삭제 확인.
- 회귀 테스트: 기존 로그인 상태 카트 기능(추가/수량 변경/제거)은 정상 동작.

# Definition of Done

- [ ] UI 구현 완료 (헤더/상품 상세/카트 페이지)
- [ ] `AuthProvider.logout()` 에서 카트 클리어 연결
- [ ] `CartProvider` 초기 로드 시 인증 상태 검사
- [ ] 기존 체크아웃 가드와 동작이 충돌하지 않음
- [ ] 테스트 추가·통과
- [ ] 수동 검증: Chrome DevTools Application 탭에서 localStorage `cart` 키가 로그아웃 후 사라지는 것 확인
- [ ] Ready for review
