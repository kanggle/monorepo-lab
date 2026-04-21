# Task ID

TASK-FE-026

# Title

web-store, admin-dashboard 인증 컨텍스트 및 API 설정 중복 코드 공통 패키지 추출

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

web-store와 admin-dashboard에 동일하게 존재하는 AuthContext 로직과 API 클라이언트 설정을 공통 패키지로 추출하여 중복을 제거한다.

현재 상태:
- `apps/web-store/src/features/auth/model/auth-context.tsx`와 `apps/admin-dashboard/src/shared/hooks/auth-context.tsx`에 JWT 파싱, 토큰 리프레시, 로그아웃 로직이 동일하게 존재
- `apps/web-store/src/shared/config/api.ts`와 `apps/admin-dashboard/src/shared/config/api.ts`가 동일

# Scope

## In Scope

- `packages/api-client`에 공통 API 설정 (baseURL, 인터셉터, 토큰 관리) 통합
- 공통 auth 유틸리티 (JWT 파싱, 토큰 저장/조회) 추출
- web-store와 admin-dashboard에서 공통 패키지 import로 전환
- 에러 메시지 매핑 상수 공통화

## Out of Scope

- httpOnly 쿠키 기반 인증 전환 (별도 태스크)
- API 클라이언트 리프레시 동시 요청 race condition 수정 (별도 태스크)
- 새로운 인증 기능 추가

# Acceptance Criteria

- [ ] 공통 auth 유틸리티가 `packages/` 하위에 존재한다
- [ ] web-store의 auth-context가 공통 유틸리티를 사용한다
- [ ] admin-dashboard의 auth-context가 공통 유틸리티를 사용한다
- [ ] API 설정이 `packages/api-client`에 통합된다
- [ ] 두 앱의 로그인/로그아웃/토큰 리프레시 기능이 정상 동작한다
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/services/admin-dashboard/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/architecture/layered-by-feature.md`

# Related Contracts

- `specs/contracts/http/auth-api.md`

# Target App

- `apps/web-store`
- `apps/admin-dashboard`
- `packages/api-client`

# Implementation Notes

- `parseJwtPayload()`, `getUserFromToken()` → `packages/utils` 또는 `packages/api-client/src/auth.ts`
- localStorage 키 상수화 (`ACCESS_TOKEN_KEY`, `REFRESH_TOKEN_KEY`)
- 각 앱의 AuthProvider는 유지 (React Context는 앱별로 관리), 내부 로직만 공통 함수 호출

# Edge Cases

- 공통 패키지 빌드 순서 의존성
- 앱별로 다른 baseURL 설정 필요 시 환경 변수로 분기
- SSR 환경에서 localStorage 접근 불가 — 기존 방어 코드 유지

# Failure Scenarios

- 공통 패키지 import 경로 오류
- 빌드 시 순환 의존성
- 타입 불일치

# Test Requirements

- 공통 유틸리티 단위 테스트 (JWT 파싱, 토큰 저장/조회)
- web-store AuthContext 기능 테스트
- admin-dashboard AuthContext 기능 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
