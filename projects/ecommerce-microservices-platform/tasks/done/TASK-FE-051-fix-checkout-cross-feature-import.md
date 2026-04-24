# Task ID

TASK-FE-051

# Title

Fix cross-feature import in CheckoutForm (FSD architecture violation)

# Status

ready

# Owner

frontend

# Task Tags

- code

# Goal

`CheckoutForm` (`features/checkout`)가 `useAddresses`를 `@/features/user`에서 직접 import하고 있어 FSD 아키텍처 규칙을 위반한다. `features/` 간 직접 import를 제거하고 아키텍처 규칙을 준수하는 방식으로 수정한다.

# Scope

## In Scope

- `apps/web-store/src/features/checkout/ui/CheckoutForm.tsx`: `@/features/user`에서 `useAddresses` 직접 import 제거
- 올바른 아키텍처 패턴으로 교체:
  - 옵션 A: `useAddresses`를 `entities/user`로 이동하여 양 feature가 entities 레이어에서 접근
  - 옵션 B: `addresses` 데이터를 `CheckoutForm`의 props로 전달 (부모 페이지/위젯에서 주입)
  - 옵션 C: `widgets/` 레이어에서 두 feature를 합성

## Out of Scope

- UI 변경
- 주소 로딩 동작 변경
- checkout-form.test.tsx 이외의 테스트 변경

# Acceptance Criteria

- [ ] `features/checkout`에서 `@/features/user` import가 없음
- [ ] `specs/services/web-store/architecture.md`의 Forbidden Dependencies 규칙 준수
- [ ] CheckoutForm 주소 로딩 동작이 기존과 동일하게 유지됨
- [ ] checkout-form.test.tsx 테스트 통과
- [ ] 빌드 성공

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/state-management.md`

# Related Contracts

- N/A

# Target App

- `apps/web-store`

# Implementation Notes

## 위반 내용

`apps/web-store/src/features/checkout/ui/CheckoutForm.tsx:12`에서:

```tsx
import { useAddresses } from '@/features/user';
```

`specs/services/web-store/architecture.md` Forbidden Dependencies 섹션:
> `features/` must not import from other `features/` directly

## 권장 해결 방안

**옵션 A (권장)**: `useAddresses` 훅을 `entities/user`로 이동하거나 주소 데이터 조회 훅을 entities 레이어에 위치시킴. 주소(Address)는 도메인 엔티티이므로 entities 레이어에 적합함.

**옵션 B**: CheckoutForm이 주소 데이터를 props로 받도록 변경. 부모 페이지(`app/(store)/checkout/page.tsx`)에서 `useAddresses`를 호출하여 내려줌.

옵션 선택 시 기존 테스트(checkout-form.test.tsx)의 mock 패턴도 함께 업데이트 필요.

## 이전 리뷰 맥락

TASK-FE-010 리뷰(TASK-FE-040)에서 하드코딩된 쿼리 키를 `useAddresses` 훅으로 교체하도록 요청했으나, 해당 요청이 FSD cross-feature 위반을 초래함. 이 태스크는 그 위반을 수정한다.

# Edge Cases

- `useAddresses`를 entities로 이동 시 기존 `features/user`에서 re-export하여 하위 호환성 유지 가능
- props 방식 선택 시 CheckoutForm 테스트의 useAddresses mock을 제거하고 props로 데이터 전달하도록 수정

# Failure Scenarios

- 수정 후 CheckoutForm에서 주소 목록이 표시되지 않는 경우
- 기본 배송지 자동 선택 로직이 깨지는 경우

# Test Requirements

- checkout-form.test.tsx 기존 테스트 모두 통과 유지
- 변경된 아키텍처 패턴에 맞게 테스트 mock 업데이트

# Definition of Done

- [ ] `features/checkout`에서 `@/features/user` import 제거
- [ ] FSD 아키텍처 규칙 준수하는 방식으로 주소 데이터 접근 구현
- [ ] 테스트 통과 확인
- [ ] 빌드 성공
