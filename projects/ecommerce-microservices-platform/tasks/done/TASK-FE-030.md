# Task ID

TASK-FE-030

# Title

isApiError 타입 가드 및 ERROR_MESSAGES 상수 공유 모듈 통합 — web-store 5곳 중복 제거

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

web-store의 5개 컴포넌트(CheckoutForm, SignupForm, ProfileForm, AddressList, AddressForm)에서 동일한 `isApiError()` 타입 가드 함수와 `ERROR_MESSAGES` 상수가 중복 정의되어 있다. `@repo/types`에 이미 `getErrorMessage()` 유틸이 존재하므로, 타입 가드를 `@repo/types`로 통합하고 web-store 전체에서 공유 함수를 사용하도록 전환한다.

---

# Scope

## In Scope

- `@repo/types`에 `isApiError()` 타입 가드 함수 추가 (또는 기존 함수 확인 후 export)
- `@repo/types`에 공통 `ERROR_MESSAGES` 상수 정의
- web-store 5개 컴포넌트에서 로컬 `isApiError()` 및 `ERROR_MESSAGES` 제거, 공유 모듈 import로 전환
- admin-dashboard에서도 동일 패턴이 있으면 함께 전환

## Out of Scope

- 에러 메시지 내용 변경
- 새로운 에러 타입 추가
- getErrorMessage() 로직 변경

---

# Acceptance Criteria

- [ ] `@repo/types`에서 `isApiError` 함수가 export된다
- [ ] `@repo/types`에서 공통 `ERROR_MESSAGES` 상수가 export된다
- [ ] web-store의 CheckoutForm, SignupForm, ProfileForm, AddressList, AddressForm에서 로컬 `isApiError` 정의가 제거되었다
- [ ] web-store의 해당 컴포넌트에서 로컬 `ERROR_MESSAGES` 정의가 제거되었다
- [ ] 모든 컴포넌트가 `@repo/types`의 공유 함수/상수를 import한다
- [ ] 기존 에러 처리 동작이 변경되지 않았다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/loading-error-handling.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링)

---

# Target App

- `packages/types`
- `apps/web-store`
- `apps/admin-dashboard` (해당 시)

---

# Implementation Notes

- `@repo/types`에 이미 `getErrorMessage()` 함수가 있으므로, `isApiError()`를 같은 모듈에 추가한다.
- ERROR_MESSAGES는 키-메시지 매핑 상수로, 각 앱에서 메시지를 커스텀할 수 있도록 기본값만 제공하는 방식을 검토한다.
- admin-dashboard에서 `getErrorMessage()`를 이미 사용 중이면 web-store도 동일 패턴으로 전환한다.

---

# Edge Cases

- 컴포넌트별로 ERROR_MESSAGES에 다른 키가 포함된 경우 → 공통 상수에 합집합 정의
- 특정 컴포넌트만 추가 에러 메시지가 필요한 경우 → 스프레드 연산자로 확장 허용
- import 경로 변경으로 인한 빌드 실패

---

# Failure Scenarios

- 공유 모듈의 타입이 컴포넌트 로컬 타입과 미세하게 다른 경우 타입 에러
- 순환 의존성 발생 (types → api-client → types)
- 트리셰이킹 실패로 번들 크기 증가

---

# Test Requirements

- `@repo/types`의 `isApiError` 단위 테스트
- 각 컴포넌트 기존 테스트 통과 확인
- 에러 처리 동작 스냅샷/동작 테스트

---

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
