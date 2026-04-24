# Task ID

TASK-FE-062-fix-001

# Title

TASK-FE-062 리뷰 fix: cross-feature import 제거 + 에러/로딩 fallback 테스트 추가

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- fix

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-FE-062 리뷰에서 발견된 세 가지 이슈를 수정한다:

1. **[Critical] cross-feature import 아키텍처 위반**: `OrderList.tsx`, `OrderDetail.tsx`가 `@/features/user-management/hooks/use-user`를 직접 import함. `specs/services/admin-dashboard/architecture.md`의 "features must not import from other features" 규칙 위반. `useUser`(또는 이메일 조회 전용 래퍼)를 `shared/hooks/` 또는 `shared/api/`로 추출한다.
2. **[Warning] fallback 테스트 커버리지 누락**: 이메일 조회 실패(`isError`) / 로딩 중 케이스가 `OrderList.test.tsx` 및 `OrderDetail.test.tsx`에서 검증되지 않음. Acceptance Criteria의 "실패 시 정상 렌더링"과 Failure Scenarios를 테스트로 검증한다.
3. **[Suggestion] fallback 표시 일관성**: `UserEmailCell`은 에러 시 `userId.slice(0, 8)+'...'`, `OrderDetail`은 `'-'`로 서로 다름 → 둘 다 `'-'`(모르는 이메일)로 통일하고, 표시 목적의 userId는 기존 userId 셀/필드에 이미 있으므로 중복 노출을 피한다.

---

# Scope

## In Scope

- 신규 파일: `apps/admin-dashboard/src/shared/hooks/use-user-email.ts` (또는 재사용 가능한 이름) — userId → email을 조회하는 얇은 래퍼 훅. 내부적으로 기존 user-service API 클라이언트(`getUser` 또는 `packages/api-client`의 admin-user-api) 사용. 이때 user-management feature 내부 파일은 참조하지 않음 (shared가 feature를 참조하는 것 금지)
- `OrderList.tsx`, `OrderDetail.tsx`가 새 shared 훅만 사용하도록 수정
- `OrderList.test.tsx`, `OrderDetail.test.tsx`에 에러 fallback 및 로딩 fallback 케이스 추가
- fallback 표시 `'-'`로 통일

## Out of Scope

- user-management feature 내 `useUser` 제거 (user-management 내에서는 계속 사용)
- 이메일 외 다른 유저 정보 필드 표시
- order-service 백엔드/컨트랙트 변경

---

# Acceptance Criteria

- [ ] `OrderList.tsx`, `OrderDetail.tsx`에 `@/features/user-management/...` import 문이 존재하지 않는다
- [ ] 이메일 조회 로직이 `shared/` 또는 order-management 내부에 위치
- [ ] 에러 발생 시 두 컴포넌트 모두 `-`(또는 동일한 fallback 문자열)로 표시
- [ ] 로딩 중 두 컴포넌트 모두 일관된 로딩 표시
- [ ] `OrderList.test.tsx`: 이메일 조회 실패 시 fallback 렌더링 테스트 추가
- [ ] `OrderDetail.test.tsx`: 이메일 조회 실패 + 로딩 중 테스트 추가
- [ ] 기존 테스트 모두 통과
- [ ] TypeScript 빌드 통과

---

# Related Specs

- `specs/services/admin-dashboard/architecture.md` (Forbidden Dependencies)

# Related Skills

- `.claude/skills/frontend/react-query.md` (있으면)

---

# Related Contracts

없음 (변경 없음)

---

# Target Service

- `admin-dashboard`

---

# Edge Cases

- shared 훅이 React Query 캐시 키를 user-management 내부 훅과 공유하도록 설계 (동일 `['user', userId]` 키 재사용) → 양쪽에서 동일 userId 조회 시 dedupe 유지

---

# Failure Scenarios

- API 클라이언트 직접 호출 시 타입/경로 오류 → 기존 `packages/api-client/src/services/admin-user-api.ts` 재사용으로 회피

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
