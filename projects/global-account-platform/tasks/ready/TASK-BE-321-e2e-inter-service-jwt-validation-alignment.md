# Task ID

TASK-BE-321

# Title

e2e inter-service JWT 검증 정합 — docker-compose.e2e.yml 의 `OIDC_ISSUER_URL` 미설정 latent gap 해소. account/security `/internal/**` 수신측이 GAP `client_credentials` Bearer JWT 를 **실제로 검증**하도록 SAS issuer + 수신측 issuer/JWKS + 호출측 token-uri 를 `http://auth-service:8081` 로 일관 정렬하고, 그 검증을 PR-time `@Tag("smoke")` e2e 로 회귀-게이트화한다. (ADR-005 단계 4 후속 — 문서화된 landmine #2)

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- security
- infra

---

# Dependency Markers

- **depends on**: TASK-BE-319a(security 수신측 JWT-only) + TASK-BE-319b(account 수신측 JWT-only) — 양 수신측이 이미 X-token 제거 + GAP JWT 전용. 본 task 는 그 e2e 검증 정합.
- 선행/후속 블로커 없음. 단독 진행 가능.

---

# Goal

ADR-005 단계 4 종결 후 남은 **latent landmine**: `docker-compose.e2e.yml` 어디에도 `OIDC_ISSUER_URL` 이 설정돼 있지 않다. 그 결과:
- account/security 수신측의 `*.jwt.issuer` / `jwk-set-uri` 가 `${OIDC_ISSUER_URL:http://localhost:8081}` 기본값으로 남아 **컨테이너 자기 자신**(auth-service 아님)을 가리킨다 → 수신측이 caller 의 Bearer JWT 를 **issuer 불일치 + JWKS 도달 불가**로 검증할 수 없다.
- 호출측(admin/security/auth)의 `gap.internal-client.token-uri` 도 동일 기본값 → auth-service `/oauth2/token` 에 도달 못 한다(auth 의 self-call 만 우연히 localhost=자기자신으로 동작).

현재 gap docker-compose smoke(`GoldenPathE2ETest`, 유일한 `@Tag("smoke")`)가 GREEN 인 이유는 그것이 admin operator-auth(admin **자체** JWT issuer=`global-account-platform`) 전 구간만 돌고 **Bearer-to-account/security `/internal/**` 경로를 전혀 exercise 하지 않기** 때문이다(마지막 bulk-lock step 도 revoked 토큰 401 을 admin 자체 auth 층에서 단언, admin→account 호출 이전). 즉 마이그레이션이 e2e 에서 **증명된 적이 없다**.

본 task 는 (1) workload-token 체인을 `http://auth-service:8081` 로 일관 정렬해 수신측이 실제로 JWT 를 검증하게 하고, (2) account+security 양 수신측의 fail-closed(401) + valid-JWT-accept 를 PR-time `@Tag("smoke")` e2e 로 못 박는다.

# Scope

## In scope

- **docker-compose.e2e.yml**: `OIDC_ISSUER_URL: http://auth-service:8081` 를 **auth-service / account-service / security-service / admin-service** 4개 서비스 env 에 추가.
  - auth(SAS): 발급 토큰의 `iss` claim = `http://auth-service:8081` + 자기 self-call token-uri 도 동일(네트워크 도달).
  - account/security(수신측): `issuer` = `http://auth-service:8081`(token iss 와 일치) + `jwk-set-uri` = `http://auth-service:8081/oauth2/jwks`(도달).
  - admin/security(호출측): `token-uri` = `http://auth-service:8081/oauth2/token`(도달).
  - **일관성 필수**: auth 자신의 발급 토큰 iss 도 수신측 issuer 와 같아야 하므로 auth 에도 동일 값 설정(한 곳이라도 빠지면 issuer 불일치 401).
- **ComposeFixture.java**: `AUTH_BASE_URL`(=`http://127.0.0.1:18081`) 상수 추가(현재 ADMIN/ACCOUNT/SECURITY 만 노출, AUTH 포트 상수는 있으나 base url 없음).
- **신규 e2e 테스트** `InternalWorkloadAuthE2ETest`(`@Tag("smoke")`): client_credentials 토큰 발급 + 양 수신측 검증.
  - 토큰 발급: `POST auth /oauth2/token` (Basic `admin-service-client:secret`, `grant_type=client_credentials`, `scope=internal.invoke`) → 200 + 비어있지 않은 access_token.
  - account fail-closed: `GET account /internal/accounts/{uuid}/status` (인증 없음) → 401.
  - account validate: 동일 + `Authorization: Bearer <token>` → **404**(account not found) — JWT 가 수락되어 비즈니스 로직까지 도달했음을 증명(401 아님).
  - security fail-closed: `GET security /internal/security/login-history?accountId=<uuid>` (인증 없음) → 401.
  - security validate: 동일 + Bearer → 200(빈 페이지).

## Out of scope

- 프로덕션 `application.yml` / 서비스 코드 변경(0) — 이미 env-driven. compose env + e2e 테스트만.
- nightly `@Tag("full")` 테스트(CrossServiceBulkLock / TenantProvisioning)의 재작성 — 본 정합으로 그들도 실제 검증하게 되지만 본 task 는 PR-time smoke 게이트만 추가. (full 회귀는 nightly 가 잡음.)
- `application-e2e.yml` 의 issuer/bypass override(현재 없음, 추가 불요 — compose env 로 충분).
- mTLS / 완전 keyless → 후속 ADR.

# Acceptance Criteria

- **AC-1**: `docker-compose.e2e.yml` 의 auth/account/security/admin 4서비스에 `OIDC_ISSUER_URL: http://auth-service:8081` 설정.
- **AC-2**: `InternalWorkloadAuthE2ETest`(`@Tag("smoke")`)가 추가되어 — (a) client_credentials 토큰 발급 200, (b) account 무인증 401, (c) account Bearer 404(검증 통과), (d) security 무인증 401, (e) security Bearer 200 — **PR-time `:e2eSmokeTest` 잡에서 GREEN**.
- **AC-3**: 기존 smoke(`GoldenPathE2ETest`) 회귀 0(operator-auth 는 SAS issuer 변경과 무관).
- **AC-4**: gateway 사용자토큰 검증(issuer=`global-account-platform`, 별개 시스템) 무영향 — gateway e2e 경로 회귀 0.
- **AC-5**: 프로덕션 코드 diff 0(테스트 + compose + ComposeFixture 상수만).

# Related Specs

- `specs/contracts/http/internal/*.md` — 이미 Bearer JWT 로 기술(BE-318/319). 본 task 가 그 인증을 e2e 에서 실증.
- ADR-005 § 무중단 마이그레이션 단계 4 + Implementation Roadmap(e2e 검증 정합).

# Related Contracts

- account `/internal/accounts/{id}/status`, security `/internal/security/login-history` — Bearer JWT 단일 인증(변경 없음, e2e 검증 추가).
- `POST /oauth2/token`(client_credentials), `GET /oauth2/jwks` — 변경 없음.

# Edge Cases

- **issuer 불일치 fail-closed**: auth 에 OIDC_ISSUER_URL 을 빼면 auth self-call 토큰 iss=localhost 가 되어 수신측(issuer=auth-service) 과 불일치 → auth→account 도 401. 4서비스 모두에 동일 값 필수.
- **JWKS lazy fetch**: 수신측 decoder 는 OIDC discovery 를 startup 에 안 함(lazy) → auth 미기동에도 부팅 무결. 첫 `/internal` 호출 시 JWKS fetch.
- **scope**: 수신측은 signature+issuer 만 검증(scope/aud 미강제, V0019 주석) — `internal.invoke` 요청은 catalog 정합용. 토큰 발급만 되면 통과.
- **404 vs 401 구분**: account validate 단언은 반드시 **404**(혹은 200 계열) 여야 하고 **401 이면 실패** — 401 은 JWT 미수락(정합 실패) 신호.
- **포트 직접 접근**: `/internal/**` 는 gateway 라우트가 아니라 서비스 자기 포트(account 18082 / security 18084)로 직접 호출(workload caller 와 동일 경로). gateway(18080) 경유 아님.

# Failure Scenarios

- 한 서비스라도 OIDC_ISSUER_URL 누락 → issuer 불일치로 Bearer 검증 401 → AC-2(c)/(e) RED. 4서비스 일관 설정으로 방지.
- SAS issuer 변경이 사용자 OIDC 흐름을 깨뜨림 → 사용자 로그인 토큰은 `auth.jwt.issuer=global-account-platform`(SAS 아님)이라 무관. smoke=operator-auth 라 영향 0(AC-3).
- e2e 환경 도달 실패(JWKS unreachable) → token-uri/jwk-set-uri 가 `auth-service:8081` 네트워크 이름으로 도달. ComposeFixture 가 4서비스 health 대기 후 시작.

---

# Implementation Design Notes

- `OIDC_ISSUER_URL` 은 **SAS workload-token 체인에만** 영향(`/oauth2/*`). gateway 가 검증하는 사용자 로그인 JWT(issuer=`global-account-platform`, auth `/internal/auth/jwks`)는 별개 시스템 — ripple 없음. 이 격리가 본 정합을 안전하게 만든다.
- 검증은 CI Linux `:e2eSmokeTest` 가 권위(로컬 Testcontainers/Docker 는 Rancher npipe 회귀로 간헐 스킵). 본 task 의 신규 smoke 테스트가 내 PR CI 에서 실제 compose 를 띄워 Bearer-to-receiver 를 돌리므로, PR 이 GREEN 이면 정합이 실증된 것.
- 착수: compose env 4서비스 추가 → ComposeFixture 상수 → e2e 테스트 → e2e 모듈 compileTestJava → push → CI.

---

# Notes

- ADR-005 단계 4 의 문서화된 latent landmine #2 해소(메모리 `project_adr005_workload_identity_complete`). 본 정합 후 nightly full(CrossServiceBulkLock/TenantProvisioning)도 실제 JWT 검증을 거치게 되어 마이그레이션이 e2e 전 구간에서 증명된다.
