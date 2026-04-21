# TASK-FE-049-fix-02: TASK-FE-049-fix-01 리뷰에서 발견된 문제 수정

## Goal

TASK-FE-049-fix-01 리뷰에서 발견된 다음 문제들을 수정한다:
1. `features/checkout`에서 `features/user`를 직접 import하는 크로스-피처 의존성 위반 해소
2. `payment-api.test.ts`의 409 중복 결제 테스트에서 잘못된 에러 코드(`ALREADY_PROCESSED`) 수정

## Scope

### Frontend (web-store)

1. **아키텍처 수정 — cross-feature 의존성 제거**
   - `features/checkout/ui/CheckoutForm.tsx` 는 `@/features/user`에서 `getMyAddresses`를 직접 import하고 있음
   - FSD 아키텍처 규칙상 `features/` 레이어에서 다른 `features/`를 직접 import하는 것은 금지됨
   - 해결 방안(우선순위 순):
     a. `getMyAddresses` API 호출 로직과 주소 데이터를 `entities/` 레이어로 이동하거나,
     b. `CheckoutForm`에 저장 주소 목록을 props로 주입하는 방식으로 변경 (page/widget 레이어에서 `features/user`를 호출하고 결과를 CheckoutForm에 전달)
   - 선택한 방안을 `specs/services/web-store/architecture.md`의 허용된 의존성 방향에 따라 구현할 것

2. **테스트 수정 — payment-api.test.ts 에러 코드 수정**
   - `confirmPayment` 409 중복 결제 케이스 테스트의 에러 코드를 `ALREADY_PROCESSED` → `PAYMENT_ALREADY_COMPLETED`로 수정
   - 근거: `specs/contracts/http/payment-api.md` 및 `specs/platform/error-handling.md`에 정의된 표준 코드는 `PAYMENT_ALREADY_COMPLETED`임

## Acceptance Criteria

- [ ] `CheckoutForm.tsx`가 `@/features/user`를 직접 import하지 않음
- [ ] 주소 조회 기능이 정상 동작함 (다른 레이어를 통해 제공됨)
- [ ] FSD 아키텍처 의존성 규칙(`features/` → `entities/`, `shared/` 만 허용)을 준수함
- [ ] `payment-api.test.ts`의 409 케이스 에러 코드가 `PAYMENT_ALREADY_COMPLETED`로 수정됨
- [ ] 기존 테스트가 모두 통과함
- [ ] 변경된 컴포넌트/훅에 대한 테스트가 수정/추가됨

## Related Specs

- `specs/services/web-store/architecture.md`
- `specs/contracts/http/payment-api.md`
- `specs/platform/error-handling.md`

## Related Contracts

- `specs/contracts/http/payment-api.md`

## Edge Cases

- 주소 목록 로딩 중 스켈레톤 UI가 정상 표시되어야 함
- 저장된 주소가 없는 경우 새 주소 입력 폼이 바로 표시되어야 함
- 기본 주소가 있는 경우 기본 주소가 선택된 상태로 표시되어야 함

## Failure Scenarios

- 주소 조회 API 실패 시 기존 동작(에러 무시, 새 주소 입력 폼 표시)이 유지되어야 함
- 테스트 모킹 실패 시 각 테스트가 독립적으로 격리되어 방지
