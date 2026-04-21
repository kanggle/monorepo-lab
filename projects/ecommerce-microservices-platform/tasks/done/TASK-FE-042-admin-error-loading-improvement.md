# TASK-FE-042: Admin Dashboard 에러 처리 / 로딩 상태 개선

## Goal

admin-dashboard의 에러 처리와 로딩 상태를 Next.js App Router 패턴에 맞게 개선한다.

## Scope

- `apps/admin-dashboard/src/` 내 에러/로딩 관련 파일

### In Scope

1. 세그먼트별 `error.tsx` 추가 (`(admin)/`, `products/`, `orders/`, `users/`)
2. `not-found.tsx` 추가 (root, admin 세그먼트)
3. 상세 페이지에서 404 응답 시 `notFound()` 호출
4. 뮤테이션 에러 처리를 `window.alert()` → Toast 알림으로 교체
5. 로딩 상태 일관성 개선 (상세 페이지에 Suspense 래핑)

### Out of Scope

- 외부 에러 추적 서비스 연동 (Sentry 등)
- API 클라이언트 재시도 전략 변경
- 인증/가드 로직 변경

## Acceptance Criteria

- [ ] `(admin)/error.tsx` 가 존재하고 admin 영역 에러를 캐치한다
- [ ] `products/`, `orders/`, `users/` 각 세그먼트에 `error.tsx`가 존재한다
- [ ] root 레벨과 admin 레벨에 `not-found.tsx`가 존재한다
- [ ] 상세 페이지(product, order, user)에서 404 응답 시 `notFound()`를 호출한다
- [ ] 뮤테이션 훅에서 `window.alert()` 대신 Toast 컴포넌트를 사용한다
- [ ] 상세 페이지(products/[id], orders/[id], users/[id])에 Suspense 래핑이 적용된다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/` (존재 시)

## Related Contracts

- 없음 (프론트엔드 UI 개선)

## Edge Cases

- API 서버 미응답 시 error.tsx가 적절히 표시되어야 한다
- 존재하지 않는 상품/주문/사용자 ID 접근 시 not-found 페이지가 표시되어야 한다
- Toast 알림이 여러 개 동시 발생해도 정상 표시되어야 한다

## Failure Scenarios

- 네트워크 단절 시 에러 페이지에서 재시도 가능해야 한다
- error.tsx 자체에서 에러 발생 시 상위 error.tsx가 캐치해야 한다
