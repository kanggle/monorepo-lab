# Task ID

TASK-FE-023

# Title

admin-dashboard 사용자 관리 기능 구현 — 사용자 목록, 상세 조회

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

# Goal

admin-dashboard에 사용자 관리 기능을 구현한다. 관리자가 전체 사용자 목록을 필터링/페이지네이션하여 조회하고, 개별 사용자의 상세 프로필 정보를 확인할 수 있다.

# Scope

## In Scope

- `/users` 라우트 — 사용자 목록 페이지
- `/users/[userId]` 라우트 — 사용자 상세 페이지
- 사용자 목록 조회 (GET /api/admin/users) — 필터링(status, email), 페이지네이션
- 사용자 상세 조회 (GET /api/admin/users/{userId})
- `features/user-management/` 모듈 구성 (components, hooks, api, types)
- 공유 컴포넌트 활용 (DataTable, FilterBar, PageLayout, StatusBadge)
- `@repo/api-client`에 admin user API 함수 추가 (없는 경우)
- `@repo/types`에 AdminUser 관련 타입 추가 (없는 경우)
- 사이드바 네비게이션에 "사용자 관리" 메뉴 추가
- 로딩, 에러, 빈 상태 처리

## Out of Scope

- 사용자 정지/해제 기능 (관리자 API 미정의)
- 사용자 역할 변경
- 사용자 직접 생성/삭제
- 사용자 주소 관리

# Acceptance Criteria

- [ ] `/users` 페이지에서 사용자 목록이 DataTable로 표시된다
- [ ] status 필터(ACTIVE, SUSPENDED, WITHDRAWN)로 사용자를 필터링할 수 있다
- [ ] email 검색으로 사용자를 필터링할 수 있다
- [ ] 페이지네이션이 동작한다 (page, size)
- [ ] 사용자 행 클릭 시 `/users/{userId}` 상세 페이지로 이동한다
- [ ] 상세 페이지에서 사용자의 전체 프로필 정보가 표시된다
- [ ] 존재하지 않는 사용자 조회 시 404 상태가 표시된다
- [ ] 사이드바에 "사용자 관리" 메뉴가 추가되어 있다
- [ ] 로딩 중 스켈레톤/스피너가 표시된다

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature.md`

# Related Contracts

- `specs/contracts/http/user-api.md` — GET /api/admin/users, GET /api/admin/users/{userId}

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- Layered by Feature 구조: `features/user-management/components/`, `features/user-management/hooks/`, `features/user-management/api/`, `features/user-management/types/`
- 기존 product-management, order-management 패턴을 참고하여 일관성 유지
- DataTable, FilterBar, StatusBadge 등 shared 컴포넌트 재사용
- 사용자 status에 따라 StatusBadge 색상 구분 (ACTIVE=green, SUSPENDED=yellow, WITHDRAWN=red)
- 목록 컬럼: email, name, nickname, status, createdAt

# Edge Cases

- 사용자가 0명인 경우 빈 상태 표시
- 필터 결과가 0건인 경우
- 매우 긴 이메일/닉네임 텍스트 오버플로우 처리
- 상세 페이지에서 뒤로가기 시 필터/페이지 상태 유지

# Failure Scenarios

- GET /api/admin/users 401 → 로그인 리다이렉트
- GET /api/admin/users 403 → 권한 없음 안내
- GET /api/admin/users/{userId} 404 → "사용자를 찾을 수 없습니다" 안내
- 네트워크 타임아웃 → 재시도 안내

# Test Requirements

- UserList 컴포넌트 테스트 (목록 렌더링, 빈 상태, 필터, 페이지네이션)
- UserDetail 컴포넌트 테스트 (프로필 정보 표시, 404 상태)
- 사용자 목록 hook 테스트 (필터 파라미터, 페이지네이션)
- 사용자 상세 hook 테스트 (성공, 에러)
- StatusBadge 렌더링 테스트 (status별 색상)

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
