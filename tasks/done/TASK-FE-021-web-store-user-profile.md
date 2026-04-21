# Task ID

TASK-FE-021

# Title

web-store 사용자 프로필 페이지 구현 — 프로필 조회 및 수정

# Status

review

# Owner

frontend

# Task Tags

- code
- api

# Goal

web-store에 사용자 프로필 페이지를 구현한다. 로그인한 사용자가 자신의 프로필 정보(닉네임, 전화번호, 프로필 이미지 URL)를 조회하고 수정할 수 있다.

Feature-Sliced Design의 `features/user` 모듈 내에 프로필 관련 UI, API, 모델을 구현한다.

# Scope

## In Scope

- `/my/profile` 라우트 및 페이지 구성
- 프로필 조회 UI (GET /api/users/me 연동)
- 프로필 수정 폼 UI (PATCH /api/users/me 연동)
- `features/user/` 모듈 내 api, ui, model 구성
- `@repo/api-client`에 user API 함수 추가 (없는 경우)
- `@repo/types`에 User 관련 타입 추가 (없는 경우)
- 로딩, 에러, 빈 상태 처리
- 수정 성공/실패 토스트 알림

## Out of Scope

- 배송지 관리 (TASK-FE-022)
- 회원 탈퇴 기능
- 프로필 이미지 업로드 (URL 직접 입력만 지원)
- 비밀번호 변경 (auth-service 소관)

# Acceptance Criteria

- [ ] `/my/profile` 페이지에서 현재 사용자의 프로필 정보가 표시된다
- [ ] 닉네임, 전화번호, 프로필 이미지 URL을 수정할 수 있다
- [ ] 수정 성공 시 화면에 즉시 반영되고 성공 알림이 표시된다
- [ ] 유효성 검증 실패 시 필드별 에러 메시지가 표시된다
- [ ] 미인증 사용자 접근 시 로그인 페이지로 리다이렉트된다
- [ ] API 에러 시 에러 상태가 표시된다
- [ ] 로딩 중 스켈레톤/스피너가 표시된다

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/platform/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- `specs/contracts/http/user-api.md` — GET /api/users/me, PATCH /api/users/me

# Target App

- `apps/web-store`

# Implementation Notes

- FSD 구조: `features/user/ui/ProfileForm.tsx`, `features/user/api/userApi.ts`, `features/user/model/types.ts`
- 기존 auth feature의 인증 상태를 활용하여 미인증 시 리다이렉트
- 서버 상태 관리는 기존 프로젝트 패턴(TanStack Query 또는 SWR)을 따를 것
- Partial update이므로 변경된 필드만 PATCH 요청에 포함

# Edge Cases

- 프로필이 아직 생성되지 않은 사용자 (404 USER_PROFILE_NOT_FOUND)
- 수정 중 세션 만료
- 동시 탭에서 동일 프로필 수정
- 빈 값으로 필드 초기화 (nullable 필드)

# Failure Scenarios

- GET /api/users/me 401 → 로그인 페이지로 리다이렉트
- GET /api/users/me 404 → "프로필을 찾을 수 없습니다" 안내
- PATCH /api/users/me 400 → 필드별 유효성 에러 표시
- 네트워크 타임아웃 → 재시도 안내

# Test Requirements

- ProfileForm 컴포넌트 테스트 (렌더링, 수정, 유효성 검증)
- 프로필 조회 hook 테스트 (성공, 에러, 로딩)
- 프로필 수정 hook 테스트 (성공, 유효성 에러)
- 미인증 리다이렉트 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
