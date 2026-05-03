# Task ID

TASK-BE-132

# Title

ecommerce auth-service 컴포넌트 폐기 — settings.gradle / docker-compose / k8s / .env 정리

# Status

review

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

ecommerce 플랫폼의 자체 `auth-service` 컴포넌트 (HS256 JWT 발급 + Google 소셜 로그인 + AdminAccountSeeder) 를 빌드 / 배포 / 컨테이너 / 환경변수 차원에서 제거한다. TASK-MONO-027 (GAP V0012 시드 + gateway issuer-uri/validators + compose env cutover) 머지 후, ecommerce gateway 가 GAP 토큰만 검증하기 시작하므로 auth-service 는 더 이상 토큰 발급 경로에서 사용되지 않는다.

본 task 는 컴포넌트 제거 (compose / settings.gradle / k8s / .env / spec) 만 다룬다 — frontend 의 OAuth provider cutover (NextAuth → GAP authorize) 는 별도 follow-up TASK-FE-067 이 담당한다. 두 task 가 모두 머지되면 ecommerce 가 GAP 표준 OIDC consumer 로 완전 전환.

**선행 조건**:
1. ✅ TASK-BE-131 (gateway-service JWKS 마이그레이션) — done
2. ✅ Global Account Platform 이관 완료 (PR #93~#107)
3. ⏸ TASK-MONO-027 (V0012 + gateway issuer/validators + compose env cutover) — 본 task 의 선행 조건. 머지 후 ecommerce gateway 가 GAP 토큰을 받기 시작하면 본 task 진행 가능.
4. (portfolio 범위 밖) ecommerce 사용자 데이터의 GAP 마이그레이션 — production 운영 시 필수이나 portfolio 모노레포 시연 범위에는 포함하지 않는다 (시연용 GAP DB 는 빈 상태에서 새 사용자만 가입).

---

# Scope

## In Scope

- `docker-compose.yml`에서 `auth-service` 서비스 블록 제거 (postgres-auth 컨테이너도 함께 정리).
- ecommerce Kubernetes 매니페스트(`infra/k8s/`)에서 `auth-service` Deployment / Service / Ingress 라우팅 / Secret 키 (`JWT_SECRET`) 제거.
- `gateway-service` `application.yml` / `docker-compose.yml`: `auth-service` 의존성 (`depends_on`, `AUTH_SERVICE_URL` env) 제거. gateway 의 routes 에서 `id: auth-service` 블록 제거 (또는 GAP `/api/auth/**` 로 redirect).
- `auth-service` 모듈을 root `settings.gradle` 에서 제거 (빌드 / 테스트 제외).
- `JWT_SECRET`, `AUTH_SECRET`, `AUTH_GOOGLE_CLIENT_ID`, `AUTH_GOOGLE_CLIENT_SECRET`, `ADMIN_INITIAL_PASSWORD` 등 auth-service 전용 환경변수를 `.env.example`에서 제거.
- root `.github/workflows/ci.yml` 의 build-and-test 잡 gradle 리스트에서 `:projects:ecommerce-microservices-platform:apps:auth-service:check` 제거.
- root `.github/workflows/ci.yml` 의 boot-jars 잡에서 auth-service `:bootJar` 제거.
- root `package.json` 의 ecommerce auth-service 관련 단축 스크립트 (있으면) 제거.
- `specs/services/auth-service/` 디렉토리를 `specs/services/auth-service-deprecated/` 로 이동 + `README.md` 추가하여 폐기 사유와 대체 (GAP) 명시.
- `specs/contracts/http/auth-api.md` 가 있으면 deprecated 표시 + GAP `auth-api.md` 로 redirect.

## Out of Scope

- `auth-service` **소스 코드 자체** 삭제 — 히스토리 보존을 위해 `apps/auth-service/` 디렉토리는 레포에 남긴다 (settings.gradle 에서만 제외하여 빌드에서 빠짐). 완전 소스 삭제는 향후 별도 cleanup task.
- 사용자 데이터 마이그레이션 로직 — portfolio 범위 밖.
- web-store / admin-dashboard 의 NextAuth provider 변경 — TASK-FE-067 follow-up.
- GAP 자체 변경 — TASK-MONO-027 에서 처리됨.

---

# Acceptance Criteria

- [ ] `docker-compose up`에서 `auth-service` / `postgres-auth` 컨테이너가 시작되지 않음.
- [ ] `gateway-service` 가 `auth-service` 의존 없이 단독 기동 가능.
- [ ] `./gradlew build`에서 `auth-service` 모듈 컴파일 미포함 (settings.gradle 제거).
- [ ] root CI `build-and-test` 잡이 `auth-service:check` 없이 통과.
- [ ] `.env.example`에서 `JWT_SECRET`, `ADMIN_INITIAL_PASSWORD` 등 auth-service 전용 변수 제거.
- [ ] `specs/services/auth-service/` → `specs/services/auth-service-deprecated/` 이동 + README 추가.

---

# Related Specs

- `tasks/ready/TASK-MONO-027-ecommerce-gap-integration.md` (선행 — root tasks)
- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md` (027 산출물, GAP 통합 본문)
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-131-gateway-jwks-migration.md` — gateway 가 이미 RS256/JWKS 검증
- `projects/ecommerce-microservices-platform/specs/services/auth-service/` (이동 / deprecate 대상)
- `platform/contracts/jwt-standard-claims.md` — Global Account Platform 이 이 계약을 구현

---

# Related Skills

- 없음

---

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/auth-api.md` (있으면) — 폐기 표시 추가 + GAP redirect

---

# Target Service

- `auth-service` (제거 대상)
- `gateway-service` (auth-service 의존성 제거)
- root `settings.gradle` (auth-service include 제거)
- root `.github/workflows/ci.yml` (auth-service:check 제거)

---

# Architecture

폐기 순서 (TASK-MONO-027 머지 후 진행):

1. ecommerce gateway 가 GAP `/oauth2/jwks` 로 토큰 검증을 시작했음을 확인 (TASK-MONO-027 의 acceptance 5번 통과 시점).
2. `docker-compose.yml` 에서 `auth-service` 블록 제거 → `gateway-service` 의 `depends_on` 에서도 제거.
3. Kubernetes 매니페스트 제거.
4. `settings.gradle` 에서 `:apps:auth-service` include 제거.
5. CI 워크플로우 갱신.
6. `.env.example` 정리.
7. spec 디렉토리 rename + README.

---

# Edge Cases

- `gateway-service`가 `auth-service`에 의존하는 healthcheck / route 가 있으면 제거 전 확인. `application.yml` 의 routes 에서 `id: auth-service` 블록은 GAP 통합 후 더 이상 필요 없음 — 제거 또는 GAP 의 `/api/auth/**` 로 redirect.
- `admin-dashboard`가 auth-service의 `/auth/admin/login` endpoint를 직접 호출하는지 확인 → TASK-FE-067 가 GAP 엔드포인트로 교체. 본 task 머지 시점에는 admin-dashboard 가 아직 자체 auth-service 호출할 가능성 — 그 경우 admin-dashboard 401 발생 (auth-service 컨테이너 부재). TASK-FE-067 머지 전까지 dev 환경 admin-dashboard 사용 불가 (의도된 임시 단절).
- `JWT_SECRET` env 가 다른 서비스 (product-service, order-service 등) 에서 참조하는지 확인 → 모두 gateway 가 검증한 후 헤더 (X-User-Id 등) 만 받으므로 downstream 은 JWT_SECRET 필요 없음.

---

# Failure Scenarios

- TASK-MONO-027 미머지 상태에서 본 task 진행 → ecommerce gateway 가 auth-service JWKS 를 가리킨 채 auth-service 가 사라져 모든 토큰 검증 실패 → 401 전체 장애. 반드시 027 머지 + GAP 통합 e2e 검증 통과 후 진행.
- admin-dashboard 가 자체 auth-service `/auth/admin/login` 을 호출하는 경우, 본 task 머지 시 admin-dashboard 의 로그인 화면이 깨짐. TASK-FE-067 의 admin-dashboard 부분 머지가 가급적 본 task 보다 먼저 또는 같이 진행되어야 한다.

---

# Test Requirements

- E2E: auth-service 없이 전체 ecommerce E2E 테스트 통과 확인 (frontend-e2e CI 잡).
- 기존 `auth-service` 관련 E2E fixture/mock 제거.
- root CI: `build-and-test` + `boot-jars` 잡이 auth-service 없이 모두 green.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (root CI green without auth-service)
- [ ] Contracts updated if needed (auth-api.md deprecated)
- [ ] Specs updated (auth-service-deprecated rename + README)
- [ ] Ready for review

---

# Prerequisites

- TASK-MONO-027 머지 완료
- (권장) TASK-FE-067 의 admin-dashboard 부분이 동시 또는 선행으로 머지 — 본 task 머지 시 admin-dashboard 가 GAP 으로 로그인할 수 있어야 dev 환경 단절 없음. 동시 머지 어려우면 본 task 머지 후 짧은 기간 admin-dashboard dev 사용 불가 허용.
