# Task ID

TASK-FE-050

# Title

Fix issues found in TASK-FE-010 (web-store React Query refactoring)

# Status

ready

# Owner

frontend

# Task Tags

- code

# Goal

TASK-FE-010 리뷰에서 발견된 이슈를 수정한다. 주요 이슈: CheckoutForm의 인라인 쿼리 키 하드코딩, PaymentSuccessPage의 useEffect dependency array 누락, checkout-form 테스트 기대값 불일치, ProfileForm에서 '프로필 이미지 URL' 텍스트 입력 필드 제거로 인한 테스트 실패.

# Scope

## In Scope

- `apps/web-store/src/features/checkout/ui/CheckoutForm.tsx`: 인라인 `useQuery` + 하드코딩 키를 `useAddresses` 훅으로 교체
- `apps/web-store/src/app/(store)/checkout/payment/success/page.tsx`: `useEffect` dependency array에 누락된 의존성 추가
- `apps/web-store/src/__tests__/checkout-form.test.tsx`: `submitOrder` 기대 인자에 `productName`, `optionName`, `unitPrice` 포함 또는 구현과 테스트 기대값 일치 조정
- `apps/web-store/src/__tests__/profile-form.test.tsx`: '프로필 이미지 URL' 텍스트 입력 필드 관련 테스트를 파일 업로드 기반으로 수정 또는 구현 방식과 동기화

## Out of Scope

- 기존에 TASK-FE-010 이전부터 존재하던 테스트 실패 (address-form, address-list, checkout-page, cart-ui, product-card, product-image, signup-form, order-api, addresses-page, profile-page 등)
- UI 변경
- API 변경

# Acceptance Criteria

- [ ] CheckoutForm이 하드코딩된 쿼리 키 대신 `useAddresses` 훅을 사용함
- [ ] PaymentSuccessPage의 `useEffect` dependency array가 ESLint exhaustive-deps 규칙을 준수함
- [ ] `checkout-form.test.tsx`의 실패 3건이 모두 통과함
- [ ] `profile-form.test.tsx`의 실패 3건이 모두 통과함 (구현과 테스트 동기화)
- [ ] 빌드 성공

# Related Specs

- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- N/A

# Target App

- `apps/web-store`

# Implementation Notes

## Issue 1: CheckoutForm 인라인 쿼리 키 하드코딩 (Warning)

`CheckoutForm.tsx:35-38`에서 `useQuery`를 직접 호출하고 쿼리 키를 `['user', 'addresses']`로 하드코딩하고 있음. `useAddresses` 훅이 이미 `features/user/index.ts`에서 export되어 있으므로 이를 사용해야 함.

```tsx
// 현재 (잘못됨)
import { useQuery } from '@tanstack/react-query';
import { getMyAddresses } from '@/entities/user';
const addressQuery = useQuery({
  queryKey: ['user', 'addresses'],
  queryFn: getMyAddresses,
});

// 수정 후
import { useAddresses } from '@/features/user';
const { data: addressData, isLoading: addressLoading } = useAddresses();
```

단, `useAddresses`가 반환하는 `invalidate` 함수는 CheckoutForm에서 불필요하므로 destructuring 시 무시.

## Issue 2: PaymentSuccessPage useEffect dependency array (Critical)

`page.tsx:32`에서 `useEffect`의 dependency array가 `[hasValidParams, router]`만 포함하고 있으나, `confirmMutation`, `paymentKey`, `orderId`, `amount`가 누락됨. `calledRef`로 중복 호출은 방지되므로 실제 버그로 이어지진 않지만 React 규칙 위반임.

## Issue 3: checkout-form 테스트 기대값 불일치 (Warning)

테스트에서 `submitOrder`가 `{ items: [{ productId, variantId, quantity }] }` 형태로 호출될 것을 기대하지만, 구현은 `productName`, `optionName`, `unitPrice`도 포함함. 테스트를 구현 동작에 맞게 수정.

## Issue 4: profile-form 테스트와 구현 불일치 (Warning)

테스트가 '프로필 이미지 URL' 레이블을 가진 텍스트 입력 필드를 기대하지만, 구현은 파일 업로드 방식으로 변경됨. 구현 방식(파일 업로드)에 맞게 테스트 수정.

# Edge Cases

- `useAddresses`로 교체 시 CheckoutForm에서 `invalidate` 함수가 불필요하므로 destructuring에서 제외
- dependency array 수정 시 `calledRef` guard가 여전히 중복 호출을 막는지 확인

# Failure Scenarios

- `useAddresses` 훅 교체 후 CheckoutForm 주소 로딩 동작이 기존과 동일한지 확인

# Test Requirements

- 수정 후 `checkout-form.test.tsx` 3건 통과
- 수정 후 `profile-form.test.tsx` 3건 통과
- 기존 통과하던 테스트(248건) 유지

# Definition of Done

- [ ] CheckoutForm `useAddresses` 훅 교체
- [ ] PaymentSuccessPage useEffect dependency 수정
- [ ] checkout-form.test.tsx 기대값 수정
- [ ] profile-form.test.tsx 기대값 수정
- [ ] 테스트 통과 확인
