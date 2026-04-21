# Task ID

TASK-FE-062

# Title

admin-dashboard 주문관리 목록/상세에 주문자 이메일 표시

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

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

관리자 주문관리 화면에서 주문자 식별을 `userId`(UUID)만으로 확인하기 어려운 문제를 개선한다. 목록 테이블과 상세 페이지에 주문자 이메일을 추가로 표시한다.

- 데이터 소스: 기존 `GET /api/admin/users/{userId}` (AdminUserDetail, email 포함)
- order-service/컨트랙트 변경 불필요 — 프런트엔드에서 userId로 user-service 사용자 정보를 별도 조회

---

# Scope

## In Scope

- `apps/admin-dashboard/src/features/order-management/components/OrderList.tsx`: 테이블에 "주문자 이메일" 컬럼 추가 (또는 주문자 ID 셀에 이메일 병기)
- `apps/admin-dashboard/src/features/order-management/components/OrderDetail.tsx`: 주문 정보 섹션에 주문자 이메일 필드 추가
- 이메일 조회는 기존 `useUser(userId)` 훅 재사용 (React Query 캐싱으로 동일 userId 중복 요청 자동 제거)
- 로딩/에러 상태 기본 처리 (이메일 로딩 중에는 플레이스홀더, 실패 시 userId만 표시)
- 관련 테스트 업데이트

## Out of Scope

- order-service API 응답 DTO 변경
- `specs/contracts/http/order-api.md` 변경
- order 목록 페이지의 검색/필터에 이메일 추가 (별도 태스크)
- batch 이메일 조회 API 신설

---

# Acceptance Criteria

- [ ] `OrderList.tsx`에서 각 주문 행에 주문자 이메일이 표시된다
- [ ] `OrderDetail.tsx`에서 주문자 이메일이 주문 정보 섹션에 표시된다
- [ ] 이메일 로딩 실패 시에도 주문 목록/상세가 정상 렌더링된다 (userId만 표시)
- [ ] 기존 OrderList / OrderDetail 테스트가 이메일 표시 포함으로 업데이트되어 통과한다
- [ ] TypeScript 빌드 통과 (`pnpm --filter admin-dashboard build` 또는 `tsc --noEmit`)

---

# Related Specs

- `specs/services/admin-dashboard/*.md` (있으면)
- `specs/features/*order*.md` (있으면)

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

- 동일 페이지에 같은 userId의 주문이 여러 건 존재 → React Query 캐싱으로 중복 요청 제거
- userId가 user-service에 존재하지 않는 경우 (탈퇴 등) → 이메일 표시 대신 "삭제된 사용자" 또는 userId 그대로 표시

---

# Failure Scenarios

- `GET /api/admin/users/{userId}` 404/500 응답 → 목록/상세 자체는 정상 렌더링, 이메일 칸에만 fallback 표시

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated
- [ ] Tests passing
- [ ] Ready for review
