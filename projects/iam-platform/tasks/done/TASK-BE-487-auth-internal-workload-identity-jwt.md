# Task ID

TASK-BE-487

# Title

ADR-005 단계 4 완결 — auth-service `/internal/**` 를 `client_credentials` JWT 로 보호 + caller(account/admin) bearer 배선

# Status

done

# Owner

backend

# Task Tags

- code
- api

---

# Goal

ADR-005(서비스 간 workload 인증 — `client_credentials` 단기 JWT, **ACCEPTED**)의 **단계 4**("정적 토큰 제거 + auth `/internal` permitAll 제거")는 account-service(TASK-BE-319/319b)·security-service(TASK-BE-319a) 수신측에는 적용됐으나, **auth-service 자신의 `/internal/**` 수신측은 유예**되어 아직 `permitAll()` 이다(`SecurityConfig.java:47`). ADR-005 표(`ADR-005:25`)가 `auth-service | ❌ 미보호` 로 명시하고, 계약 `auth-internal.md:7` 도 *"현 시점의 auth-service `SecurityConfig` 는 `/internal/**` 를 `permitAll()` 로 두고 있다 (TASK 별도)"* 로 갭을 못박아 둔 상태다.

이 task 는 그 **마지막 미보호 내부 경계**를 닫는다. 완료 후 참: auth-service `/internal/**` 는 GAP `client_credentials` JWT(자체 발급, self-JWKS 검증)로만 통과하고, 그 안으로 호출하는 두 caller(account-service · admin-service)는 `Authorization: Bearer` 로 전환된다 — ADR-005 workload-identity 마이그레이션이 IAM 내부에서 100% 완결된다.

**원자적 monorepo PR**: receiver(auth) 를 fail-closed 로 flip 하면 헤더 없이 호출하던 caller 가 즉시 깨지므로, receiver JWT 요구 + 두 caller bearer 배선을 **한 커밋**에 담는다(staggered = 일시적 broken main).

---

# Scope

## In Scope

### 1. Receiver — auth-service `/internal/**` JWT 검증 (account-service BE-319b blueprint 복제)
- `infrastructure/config/SecurityConfig`(@Order(2) chain): `/internal/**` `permitAll()` → `.authenticated()` + `oauth2ResourceServer(jwt)`.
- `internalJwtDecoder` bean: `NimbusJwtDecoder.withJwkSetUri(<self /oauth2/jwks>)` + `JwtValidators.createDefaultWithIssuer(<GAP issuer>)`. **self-JWKS lazy fetch** — 기동 시 SAS 준비 의존 없음(account-service 주석과 동일 근거). auth-service 는 multi-tenant 아님이나, `/internal/**` 는 서비스 토큰이므로 `tenant_id` 미고정(account-service와 동일).
- `InternalApiFilter`(test/standalone 프로파일 bypass) — @WebMvcTest 슬라이스·standalone 로컬에서 실 JWT 없이 통과, 운영은 fail-closed(`internal.api.bypass-when-unconfigured:false` 기본).
- custom 401 entry point — 기존 `{"code":"UNAUTHORIZED",...}` 계약 보존.
- **주의**: @Order(1) SAS chain(`/oauth2/**`·`/.well-known/**`)·`WebLoginSecurityConfig`(`/login`) 는 별도 SecurityFilterChain → @Order(2) 에 RS 추가가 그 체인들에 영향 없음(각자 securityMatcher 보유). 회귀는 fed-e2e 브라우저 SAS 플로우로 적발.

### 2. Caller — account-service `AuthServiceClient` bearer 전환
- `IamClientCredentialsTokenProvider` 신설(auth-service/admin-service 기존 복사본과 동일 패턴, client_id=`account-service-client`) — self 아님, GAP `/oauth2/token` 호출.
- `AuthServiceClient` 의 2 경로(`/internal/auth/credentials`, `/internal/auth/credentials/identity-backfill`)에 `Authorization: Bearer <token>` 부착.

### 3. Caller — admin-service `AuthServiceClient` bearer 전환
- 기존 `IamClientCredentialsTokenProvider`(BE-318b, client_id=`admin-service-client`) **재사용** — 신설 불필요.
- `AuthServiceClient` 의 2 경로(`/internal/auth/accounts/{id}/force-logout`, `/internal/auth/credentials/account-id-by-email`)에 `Authorization: Bearer` 부착 + **stale `X-Internal-Token` 전송 제거**(auth 가 검증한 적 없는 dead 헤더).

### 4. 계약 + 설정
- `specs/contracts/http/internal/auth-internal.md`: "인증" 절 → Bearer JWT(계약 지문 갱신, permitAll 서술 제거).
- `specs/contracts/http/internal/admin-to-auth.md`(존재 시): 동일 갱신.
- account-service `application.yml` + compose 데모 오버레이: `iam.internal-client.{token-uri,client-id,client-secret}` env. admin-service 는 이미 보유 → env 확인만.
- V0019 seed(account-service-client / admin-service-client)는 이미 존재 → 신규 마이그레이션 불요.

## Out of Scope
- ecommerce-microservices-platform 등 **별개 프로젝트의 독립 auth 스택**(경로 문자열만 동일, ADR-005 무관).
- security-service·community-service·membership-service 수신측(이미 완료).
- ADR-005 단계 1~3(발급 인프라·수신 이중허용·타 caller bearer) — 이미 done.
- `X-Internal-Token`/`InternalApiFilter` 정적 토큰 인프라의 **타 서비스** 잔재 정리(별건).

---

# Acceptance Criteria

- [ ] auth-service `/internal/**`: 유효 GAP `client_credentials` JWT 없이 호출 시 **401**(`{"code":"UNAUTHORIZED"}`), 유효 JWT 시 기존 동작(201/200/…).
- [ ] account-service `AuthServiceClient` 2 경로가 `account-service-client` 토큰으로 `Bearer` 전송.
- [ ] admin-service `AuthServiceClient` 2 경로가 `admin-service-client` 토큰으로 `Bearer` 전송 + `X-Internal-Token` 미전송.
- [ ] 계약 `auth-internal.md` "인증" 절이 실제(Bearer JWT)와 일치.
- [ ] test/standalone 프로파일에서 `InternalApiFilter` bypass 로 슬라이스/로컬 통과(운영 fail-closed 기본 유지).
- [ ] `:check` GREEN(auth/account/admin 3 모듈) + fed-e2e(IAM) Testcontainers IT GREEN — signup(account→auth credential create) + force-logout(admin→auth) 회귀 0, SAS 브라우저 플로우 회귀 0.

---

# Related Specs

> **Before reading**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` + `rules/domains/saas.md` + 선언된 trait 파일들.

- `docs/adr/ADR-005-service-to-service-workload-identity.md`(단계 4 근거, 표 auth ❌)
- `specs/services/auth-service/architecture.md`
- `tasks/done/TASK-BE-317-*` (수신측 JWT 검증 blueprint), `TASK-BE-319-*`/`319a`/`319b`(정적 토큰 제거), `TASK-BE-318-*`(caller bearer blueprint)

# Related Contracts

- `specs/contracts/http/internal/auth-internal.md` (account→auth)
- `specs/contracts/http/internal/admin-to-auth.md` (admin→auth, 존재 시)

# Target Service

- `auth-service`(receiver), `account-service`·`admin-service`(callers)

---

# Edge Cases

- **caller-first vs atomic**: caller 가 현재 무헤더(account)·stale X-token(admin) → receiver flip 과 반드시 동일 커밋. 별도 배포 금지.
- **self-call 순환 없음**: auth-service receiver 는 자기 JWKS 로 검증하나 lazy fetch → 기동 self-dependency 없음(account-service 선례).
- **account-service 는 자기 `/internal/**` 도 이미 JWT 보호**(수신) → 이 task 는 account 의 **송신** 경로만 추가, 수신 config 무변경.
- **admin `forceLogout` 는 X-Operator-ID/X-Tenant-Id/Idempotency-Key 헤더 유지** — Bearer 는 추가일 뿐, 기존 도메인 헤더 보존.
- **compose 데모**: account-service 에 client-secret env 누락 시 토큰 취득 실패 → signup degrade. 오버레이 env 필수(로컬 데모 재검증).

---

# Failure Scenarios

- receiver flip 만 하고 caller 미전환 → signup·force-logout 즉시 401(프로덕션 회귀). 완화: 원자적 커밋 + IT.
- self-JWKS 를 issuer discovery 로 잘못 구성 → 기동 시 SAS 미준비로 순환 대기. 완화: `withJwkSetUri` 직접(discovery 아님), lazy.
- @Order(2) 에 RS 추가가 `/api/auth/**`·`/login` 에 누수 → SAS 브라우저 로그인 파손. 완화: matcher 스코프 + fed-e2e IT.
- account-service token provider client-secret 오설정 → 전 signup 실패. 완화: IT 에서 실 토큰 왕복 검증(Testcontainers, CI 권위).

---

# Test Requirements

- auth-service 수신 IT: `/internal/auth/credentials` 무 Bearer → 401, 유효 client_credentials JWT → 201/200. `InternalCredentialControllerTest`(슬라이스, bypass) 유지.
- account-service `AuthServiceClientUnitTest`: Bearer 헤더 부착 assert(WireMock).
- admin-service `AuthServiceClientUnitTest`: Bearer 부착 + X-Internal-Token 부재 assert.
- 회귀: `SocialLoginSasBrowserIntegrationTest`(BE-396) GREEN, fed-e2e(IAM) signup+force-logout GREEN.
- `:check` + fed-e2e Testcontainers IT(CI Linux 권위).

---

# Definition of Done

- [ ] receiver JWT 전환 + 두 caller bearer 배선(원자적)
- [ ] 계약/설정 갱신, seed 재사용 확인
- [ ] `:check` GREEN + fed-e2e IAM IT GREEN(signup/force-logout/SAS 회귀 0)
- [ ] Ready for review

---

# Provenance

Surfaced 2026-07-09 — 정식 ready 큐(BE-398/390 날짜게이트·MONO-328/330 신호게이트)가 전부 착수 불가로 확인되어 사용자 지시로 미티켓 백로그 3차원 발굴(ADR/code-marker/spec-gap) 수행. spec-gap 축이 ADR-005 단계 4 의 auth-service 수신측 미완을 REAL-GAP(module-liveness + live-caller + no-covering-task)로 확증. BE-319 가 명시적으로 "auth-service 수신측 permitAll → JWT 전환(별도 task)" 을 out-of-scope 로 남긴 그 후속.

분석=Opus 4.8 / 구현 권장=Opus (보안 경계 flip + 원자적 3-서비스 + 계약 + Testcontainers IT — 오류 시 IAM 로그인/signup 회귀).
