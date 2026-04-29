# Task ID

TASK-BE-132

# Title

ecommerce auth-service 폐기 — Global Account Platform 이관 후 제거

# Status

ready

# Owner

backend

# Task Tags

- code
- security

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

ecommerce 플랫폼의 자체 `auth-service`(HS256 기반 JWT 발급 + 소셜 로그인 처리)를 폐기한다. Global Account Platform이 이관되어 모든 인증을 담당하게 되면, `auth-service`는 더 이상 필요하지 않다.

**선행 조건**: 이 태스크는 다음이 완료된 후에 실행한다:
1. TASK-BE-131 (gateway-service JWKS 마이그레이션) 완료
2. Global Account Platform 이관 완료 (JWKS 엔드포인트 운영 가능)
3. ecommerce 사용자 계정 데이터 Global Account Platform으로 마이그레이션 완료

---

# Scope

## In Scope

- `docker-compose.yml`에서 `auth-service` 서비스 블록 제거
- ecommerce Kubernetes 매니페스트(`infra/k8s/`)에서 `auth-service` Deployment, Service 제거
- `gateway-service`: `auth-service` healthcheck 의존성 제거
- `gateway-service` `application.yml`: `JWT_JWKS_URI`를 Global Account Platform의 실제 JWKS URI로 교체 (env var 업데이트)
- `web-store`, `admin-dashboard`: 소셜 로그인 redirect URL을 auth-service → Global Account Platform OAuth endpoint로 교체
- `auth-service` 모듈을 `settings.gradle`에서 제거 (빌드에서 제외)
- `AUTH_SECRET`, `AUTH_GOOGLE_CLIENT_ID` 등 auth-service 전용 환경변수를 `.env.example`에서 제거
- `specs/services/auth-service/` 디렉토리를 `specs/services/auth-service-deprecated/`로 이동 + README 추가

## Out of Scope

- `auth-service` 소스 코드 삭제 (히스토리 보존을 위해 레포에 남김; 단지 빌드/배포에서 제외)
- 사용자 데이터 마이그레이션 로직 (별도 데이터 엔지니어링 태스크)
- Global Account Platform 구현 (별도 프로젝트)

---

# Acceptance Criteria

- [x] `docker-compose up`에서 `auth-service` 컨테이너가 시작되지 않음
- [x] `gateway-service`가 Global Account Platform JWKS URI로 JWT 검증 성공
- [x] `./gradlew build`에서 `auth-service` 모듈 컴파일 미포함 (settings.gradle 제거)
- [x] `web-store` 소셜 로그인 flow가 Global Account Platform OAuth endpoint로 리다이렉트
- [x] `.env.example`에서 `AUTH_SECRET` 등 auth-service 전용 변수 제거

---

# Related Specs

- `specs/services/auth-service/architecture.md` (폐기 대상)
- `platform/contracts/jwt-standard-claims.md` — Global Account Platform이 이 계약을 구현

---

# Related Skills

- 없음

---

# Related Contracts

- `specs/contracts/http/auth-api.md` (있으면) — 폐기 표시 추가

---

# Target Service

- `auth-service` (제거 대상)
- `gateway-service` (JWT_JWKS_URI 교체)
- `web-store`, `admin-dashboard` (OAuth redirect URL 교체)

---

# Architecture

폐기 순서:

1. Global Account Platform JWKS URI 확보 (예: `https://account.example.com/.well-known/jwks.json`)
2. `JWT_JWKS_URI` env var를 staging 환경에서 먼저 교체 → 게이트웨이 정상 동작 확인
3. `docker-compose.yml`에서 `auth-service` 블록 제거 → `depends_on`에서도 제거
4. Kubernetes 매니페스트 제거
5. `settings.gradle`에서 `:apps:auth-service` include 제거
6. 정리 완료 후 `.env.example` 업데이트

---

# Edge Cases

- `gateway-service`가 `auth-service`에 의존하는 healthcheck가 있으면 제거 전 확인
- `admin-dashboard`가 auth-service의 `/auth/admin/login` endpoint를 직접 호출하는지 확인 → Global Account Platform 엔드포인트로 교체

---

# Failure Scenarios

- JWKS URI 교체 전 `auth-service` 제거 → 게이트웨이 JWT 검증 실패 → 401 전체 장애: 반드시 TASK-BE-131 완료 및 Global Account Platform 운영 확인 후 진행

---

# Test Requirements

- E2E: auth-service 없이 전체 ecommerce E2E 테스트 통과 확인
- 기존 `auth-service` 관련 E2E fixture/mock 제거

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
