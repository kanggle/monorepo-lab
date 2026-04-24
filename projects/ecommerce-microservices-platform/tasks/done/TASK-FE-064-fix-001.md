# Task ID

TASK-FE-064-fix-001

# Title

TASK-FE-064 리뷰 이슈 수정: 로그아웃 카트 클리어 테스트 누락 및 토큰 만료 시 카트 즉시 삭제

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-064 리뷰에서 발견된 두 가지 이슈를 수정한다.

1. `cart-context.test.tsx`에 로그아웃 시 localStorage `'cart'` 키가 삭제되는지 검증하는 테스트가 누락되어 있다. 태스크 Test Requirements에 명시된 "로그아웃 → localStorage `'cart'` 키 삭제 확인" 플로우 테스트를 추가해야 한다.

2. `createApiClient`의 `onAuthError` 핸들러(토큰 만료/갱신 실패 시 강제 로그아웃)가 `clearTokens()`만 호출하고 localStorage의 `'cart'` 키는 직접 삭제하지 않는다. 현재는 페이지 새로고침 후 `CartProvider`의 `useEffect`가 `isAuthenticated === false`를 감지해 삭제하지만, 이는 `CartProvider`가 마운트된 이후에만 동작하므로 신뢰성이 낮다. `onAuthError`에서 `'cart'` 키를 즉시 삭제하도록 수정해야 한다.

# Scope

## In Scope

- `apps/web-store/src/__tests__/cart-context.test.tsx`: `CartContext × 인증 상태` 그룹에 `isAuthenticated`가 `true` → `false`로 전환될 때 localStorage `'cart'` 키가 삭제되는지 검증하는 테스트 추가
- `packages/api-client/src/create-api-client.ts`: `onAuthError` 핸들러에서 `clearTokens()` 호출 후 `localStorage.removeItem('cart')` 추가 (또는 상수/유틸 함수를 통해)
- `packages/api-client/src/__tests__/create-api-client.test.ts` (존재하는 경우): `onAuthError` 호출 시 cart 키도 삭제되는지 테스트 추가

## Out of Scope

- 서버 사이드 카트 저장/동기화
- `STORAGE_KEY` 상수를 패키지 경계를 넘어 공유하는 구조 변경 (하드코딩 `'cart'` 문자열 사용 허용)
- `useRequireAuth`의 redirect 파라미터 추가 (AC 외 개선사항이므로 별도 태스크로)

# Acceptance Criteria

- [ ] `cart-context.test.tsx`에 `isAuthenticated`가 `false`로 전환 시 localStorage `'cart'` 키가 삭제되는 테스트가 추가되고 통과한다.
- [ ] `createApiClient`의 `onAuthError` 핸들러가 `localStorage.removeItem('cart')`를 명시적으로 호출한다.
- [ ] 기존 테스트가 모두 통과한다 (`npx vitest run` 통과).

# Related Specs

- `specs/use-cases/cart-and-order.md` (EF-3: 로그아웃 시 클라이언트 장바구니 즉시 비움)
- `specs/services/web-store/overview.md` (cart 항목 인증 필수)

# Related Contracts

- 없음

# Edge Cases

- `packages/api-client`는 web-store 전용이 아닌 공유 패키지이므로 `'cart'` 하드코딩이 적합한지 검토 후 결정한다. 적합하지 않다면 `onAuthError` 콜백 시그니처를 확장하거나, web-store의 `createApiClient` 설정 시 커스텀 `onAuthError`를 통해 처리한다.
- `localStorage`가 존재하지 않는 환경(SSR) 방어 처리 (`typeof window !== 'undefined'` 체크는 이미 존재).

# Failure Scenarios

- `localStorage.removeItem('cart')` 자체가 예외를 던지는 경우: try-catch로 감싸서 fallback 처리.
- `packages/api-client`에 `'cart'` 키를 하드코딩하는 것이 패키지 규칙상 부적절한 경우: `createApiClient` 옵션에 `onAuthError` 커스텀 핸들러 방식으로 web-store에서 처리.
