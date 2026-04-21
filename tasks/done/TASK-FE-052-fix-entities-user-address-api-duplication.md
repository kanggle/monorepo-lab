# Task ID

TASK-FE-052

# Title

Fix duplicated getMyAddresses implementation between entities/user and features/user (found in TASK-FE-051 review)

# Status

ready

# Owner

frontend

# Task Tags

- code

# Goal

TASK-FE-051 리뷰에서 발견된 이슈: `entities/user/api/address-api.ts`에 `getMyAddresses`가 독립적으로 구현되어 있어 `features/user/api/address-api.ts`의 동일 함수와 중복된다. 두 파일이 각자 독립적인 mock 데이터 인스턴스를 가지므로, checkout 화면과 배송지 관리 화면이 런타임에 서로 다른 mock 상태를 보게 된다. 중복을 제거하고 단일 소유권 구조로 정리한다.

# Scope

## In Scope

- `apps/web-store/src/entities/user/api/address-api.ts`: 독립 구현 제거. `features/user/api/address-api.ts`의 `getMyAddresses`를 재사용하거나, 전체 읽기 전용 API를 entities로 완전 이동
- 권장 방안: `entities/user/api/address-api.ts`가 `features/user/api/address-api.ts`에서 `getMyAddresses`를 re-export하는 방식은 FSD 위반(`entities` → `features` 금지)이므로 불가
- 올바른 방안: `getMyAddresses` 구현(mock 포함)을 `entities/user/api/address-api.ts` 단독 소유로 유지하고, `features/user/api/address-api.ts`에서 `getMyAddresses`를 `@/entities/user`에서 re-export하거나 `entities`의 address-api에서 직접 import
- `features/user/api/address-api.ts`의 CRUD 함수(`createAddress`, `updateAddress`, `deleteAddress`)는 features 레이어에 유지 (features 고유 기능)
- mock 데이터는 단일 모듈에서 관리

## Out of Scope

- UI 변경
- 비즈니스 로직 변경
- checkout-form.test.tsx 이외의 테스트 수정

# Acceptance Criteria

- [ ] `getMyAddresses`가 단일 모듈에만 구현되어 있음 (중복 없음)
- [ ] mock 주소 데이터가 단일 인스턴스로 관리됨
- [ ] `features/user`와 `entities/user`가 동일한 `getMyAddresses` 구현을 사용함
- [ ] `entities` → `features` 방향 import가 없음 (FSD 규칙 준수)
- [ ] checkout-form.test.tsx 테스트 통과
- [ ] 빌드 성공

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- N/A

# Target App

- `apps/web-store`

# Implementation Notes

## 현재 문제

`entities/user/api/address-api.ts` (라인 7-28)와 `features/user/api/address-api.ts` (라인 13-34)에 동일한 mock 데이터 배열이 별도로 선언되어 있다.

`entities/user/api/use-addresses.ts`는 `./address-api`에서 `getMyAddresses`를 import하며, `features/user/api/address-api.ts`의 구현과는 별개로 동작한다.

## 권장 해결 방안

`getMyAddresses` 구현 전체(mock 포함)를 `entities/user/api/address-api.ts`에 단독 소유로 두고, `features/user/api/address-api.ts`에서는 `@/entities/user`의 `getMyAddresses`를 import하여 사용한다.

```typescript
// features/user/api/address-api.ts
import { getMyAddresses } from '@/entities/user'; // re-use from entities
// CRUD 함수(createAddress, updateAddress, deleteAddress)는 여기서 계속 소유
```

이 방식은 FSD 허용 의존성(`features` → `entities`) 방향을 따른다.

# Edge Cases

- `features/user/api/address-api.ts`의 mock 데이터와 `entities/user/api/address-api.ts`의 mock 데이터가 통합되면 하나의 상태로 동작해야 함
- `features/user/index.ts`에서 `getMyAddresses` re-export 경로가 변경될 수 있음 — 기존 사용처 확인 필요

# Failure Scenarios

- 통합 후 배송지 관리 화면에서 주소 목록이 표시되지 않는 경우
- checkout 화면과 배송지 관리 화면의 mock 데이터가 여전히 불일치하는 경우

# Test Requirements

- checkout-form.test.tsx 기존 테스트 모두 통과 유지
- mock 경로 변경 시 테스트 mock 대상 경로 업데이트 불필요 (`@/entities/user` mock은 이미 올바름)

# Definition of Done

- [ ] `getMyAddresses` 중복 구현 제거
- [ ] mock 데이터 단일 인스턴스 관리
- [ ] FSD 의존성 방향 준수
- [ ] 테스트 통과 확인
- [ ] 빌드 성공
