# Task ID

TASK-BE-317

# Title

서비스 간(workload) 인증을 정적 `X-Internal-Token` 에서 GAP `client_credentials` 단기 JWT 로 전환 — 단계 1+2 (무중단): GAP 에 서비스별 `client_credentials` registered client 를 등록하고, 수신측 `/internal/**` 에 JWKS 기반 JWT 검증을 **추가하되 기존 `X-Internal-Token` 을 병행 허용**한다. 기존 호출자 회귀 0. (ADR-005 옵션 A)

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- infra

---

# Dependency Markers

- **depends on**: ADR-005 (PROPOSED → 본 task 가 첫 구현; 채택 근거 = ADR-003 회귀 매트릭스가 입증한 `client_credentials` grant 기동 사실).
- **prerequisite of**: TASK-BE-318 (단계 3 — 호출측 Bearer JWT 전환). 본 task 가 "수신측 이중 허용" 을 먼저 확정해야 호출측 전환 시 무중단이 보장된다.
- **무중단 순서**: 본 task(단계 1+2) → BE-318(단계 3) → BE-319(단계 4: 정적 토큰 제거 + 계약 spec 갱신). 순서 역전 금지.

---

# Goal

GAP 가 서비스 간 호출용 `client_credentials` 단기 JWT 를 발급할 수 있고, 수신측이 그 JWT 를 검증하되 **기존 `X-Internal-Token` 호출도 그대로 통과**하는 상태. 즉 새 인증 경로를 추가하되 기존 경로를 깨지 않는다(무중단 토대 확립).

본 task 완료 시:
- GAP `auth-service` 에 서비스별 `client_credentials` client (`admin-service-client`, `account-service-client`, `auth-service-client`, `security-service-client` 등 본 task scope 의 GAP-내부 호출자 — `community-service-client`/`membership-service-client` 는 기존 seed 재사용) 가 등록되어 `POST /oauth2/token (grant_type=client_credentials)` 으로 단기 JWT 를 발급한다.
- 발급된 JWT 는 `iss=GAP issuer`, 서비스 신원 claim(`sub`/`client_id`)을 포함하고 JWKS(`/oauth2/jwks`)로 검증 가능하다.
- 수신측(account-service, security-service 의 `/internal/**`)이 **유효한 GAP JWT 또는 기존 `X-Internal-Token` 중 하나라도 통과하면 허용**한다(이중 허용).
- 기존 `X-Internal-Token` 호출자(현재 모든 internal 호출)는 **변경 없이 계속 동작**한다(회귀 0).

# Scope

## In scope

- GAP `auth-service`: 서비스별 `client_credentials` registered client seed(Flyway migration, 기존 V0008~V0018 패턴 반복). client_id 명명 규약 `<service>-service-client`(기존 `community-service-client`/`membership-service-client` 선례 정렬, `svc-` prefix 미사용). `client_settings`/`token_settings`(서비스간 전용 access TTL 결정 — 기본 재사용 또는 단축).
- 발급 JWT 의 서비스 신원 claim 노출 검증(`client_id`/`sub`) — 수신측 allowlist 가능하도록.
- 수신측 보안 체인 이중 허용:
  - account-service: `InternalApiFilter`(X-Internal-Token) **유지** + OAuth2 Resource Server(JWKS) **추가**. 둘 중 하나 통과 시 허용.
  - security-service: 동일 이중 허용.
- 단위/통합 테스트: client_credentials 발급, JWT 검증 통과, X-Internal-Token 병행 통과, 둘 다 없으면 거부.

## Out of scope (후속 task)

- 호출측(admin/account/auth/security/community/gateway) 의 Bearer JWT 전환 → **TASK-BE-318**.
- 정적 `X-Internal-Token`/`InternalApiFilter` 제거 + auth `/internal` permitAll 제거 + 계약 spec 10개 갱신 + docker-compose `INTERNAL_API_TOKEN` 정리 → **TASK-BE-319**.
- mTLS / 완전 keyless → 별도 후속 ADR (ADR-005 Consequences 명시).
- community-service / membership-service 수신측 전환 — 본 task 는 정적 토큰 검증이 이미 있는 account/security 우선. community/membership 은 BE-318/319 에서 호출측 전환과 함께.

# Acceptance Criteria

- **AC-1**: GAP `auth-service` 에 본 task scope 서비스의 `client_credentials` client 가 seed 되고, `POST /oauth2/token (grant_type=client_credentials, client_id=svc-…, client_secret=…)` 이 200 + access_token(JWT) 을 반환한다.
- **AC-2**: 발급 JWT 가 `iss=GAP issuer` + 서비스 신원 claim 을 포함하고 `/oauth2/jwks` 로 서명 검증된다.
- **AC-3**: account-service `/internal/**` 가 **유효 GAP JWT(Authorization: Bearer)** 로 호출 시 200(또는 정상 응답).
- **AC-4**: account-service `/internal/**` 가 **기존 `X-Internal-Token`(Bearer 없음)** 으로 호출 시 200 — **회귀 0**(기존 호출자 불변).
- **AC-5**: account-service `/internal/**` 가 **JWT·X-Internal-Token 둘 다 없으면** 401/403(fail-closed 유지).
- **AC-6**: security-service `/internal/**` 가 AC-3/4/5 와 동일하게 동작.
- **AC-7**: edge(사용자) OIDC 경로 및 기존 IT 회귀 0 — `OAuth2*IntegrationTest`, `InternalControllerTest` 등 기존 통과 테스트 유지.
- **AC-8**: 본 task 는 **계약 spec(`*.md`) 의 인증 절을 변경하지 않는다**(이중 허용 상태라 계약상 X-Internal-Token 여전히 유효 — spec 갱신은 BE-319). 단, ADR-005 Implementation Roadmap 의 본 task 상태를 진행으로 반영 가능.

# Related Specs

- `specs/contracts/http/internal/admin-to-account.md`, `auth-to-account.md`, `security-to-account.md`, `auth-internal.md` (현재 인증 절 = X-Internal-Token / permitAll; 본 task 는 **읽기만** — 갱신은 BE-319)
- `specs/services/auth-service/architecture.md` (OIDC AS — client_credentials grant 기술)
- `specs/services/account-service/architecture.md`, `specs/services/security-service/architecture.md` (`/internal/**` 보안 경계)

# Related Contracts

- `POST /oauth2/token` (grant_type=client_credentials) — RFC 6749 §4.4. 신규 서비스 client 발급 경로(기존 grant 동작 입증됨 — ADR-003).
- `GET /oauth2/jwks` — 수신측 JWT 검증 키 소스(기존).
- 내부 호출 계약 10개 — 본 task 는 인증 **이중 허용**으로 계약 호환성 유지(계약 텍스트 불변).

# Edge Cases

- **만료 JWT**: 수신측은 만료 토큰 거부, 단 X-Internal-Token 이 함께 오면(전환기) 그쪽으로 통과(이중 허용 정책상). → 호출측은 만료 전 갱신(BE-318 캐시 정책).
- **잘못된 `iss`/서명**: JWKS 검증 실패 → JWT 경로 거부. X-Internal-Token 없으면 401.
- **client_id allowlist**(선택): 등록은 됐으나 해당 `/internal` 경로 권한 밖인 서비스 client → 403(allowlist 도입 시). 본 task 기본은 "유효 GAP JWT 면 통과", allowlist 는 AC 에 강제하지 않음(BE-319 강화 가능).
- **bypass 플래그**: `InternalApiFilter` 의 `bypassWhenUnconfigured`(test/dev) 와 신규 JWT 검증의 상호작용 — 테스트 슬라이스에서 둘 다 비활성 시 동작 정의 필요.
- **토큰 미설정 + JWT 없음**(prod 오설정): fail-closed 유지(401) — AC-5.

# Failure Scenarios

- **이중 허용 OR 로직 오류로 둘 다 없는데 통과**: 보안 회귀 → AC-5 로 가드. fail-closed 단위 테스트 필수.
- **JWT 검증 추가가 기존 X-Internal-Token 경로를 깨뜨림**(필터 순서/체인 충돌): AC-4 회귀 가드 + 기존 `InternalControllerTest` 유지로 탐지.
- **client_credentials seed 가 기존 client(`test-internal-client`/`demo-spa-client`) 와 충돌**: client_id 유니크 명명(`svc-` prefix)으로 회피. migration 멱등성 확인.
- **edge OIDC 경로 부수 회귀**(resource-server 설정이 SAS 체인과 간섭): AC-7 + auth-service 기존 IT 로 탐지. 수신측은 account/security 이고 auth-service 의 SAS 체인과 분리되나, account/security 에 resource-server 추가 시 기존 필터 체인 영향 관찰.

---

# Implementation Design Notes (코드 검증 2026-05-30)

ADR-005 채택 시 코드 사실 검증 결과. 구현자는 아래 메커니즘을 전제로 진행한다.

## 검증된 현재 상태

- account-service: `infrastructure/config/InternalApiFilter.java`(`OncePerRequestFilter`) 가 `/internal/**` 에서 `X-Internal-Token` 검증, 미통과 시 **필터가 직접 401 작성**. `SecurityConfig.java` 에서 `/internal/**` = `permitAll()` 이고 필터가 `addFilterBefore(UsernamePasswordAuthenticationFilter)` 로 선행 게이트. bypass = `internal.api.bypass-when-unconfigured=true` OR active profile `test`.
- security-service: **`SecurityConfig` 없음**. raw `@Component` 필터 `InternalAuthFilter`(403 반환, bypass profile = `test`/`standalone`). 본 task 에서 `@EnableWebSecurity` + `SecurityFilterChain` 신규 도입 필요.
- **수신측 blueprint**: community-service/membership-service `SecurityConfig` 에 `oauth2ResourceServer(rs -> rs.jwt(...))` + `application.yml` 의 `OIDC_ISSUER_URL` / `OIDC_JWK_SET_URI(=.../oauth2/jwks)` 가 이미 존재 — 복제 대상.
- 발급측: `client_credentials` client seed = Flyway only(Java `RegisteredClientRepository` 하드코딩 없음). SAS issuer = `OIDC_ISSUER_URL`(기본 `http://localhost:8081`), JWKS = `/oauth2/jwks`. (gateway/admin 의 `/internal/auth/jwks` + issuer `global-account-platform` 은 **레거시 경로** — 서비스 토큰과 무관.)

## 이중 허용(dual-accept) 메커니즘 — 핵심 설계점

community/membership 은 `/internal/**` = `.authenticated()`(JWT 단일 요구)이나 본 task 는 **JWT OR X-Internal-Token** 이 필요. 권장 통일 설계:

1. `/internal/**` 를 `permitAll()` → **`.authenticated()`** 로 전환.
2. `oauth2ResourceServer(jwt)` 추가 (JWKS = SAS `/oauth2/jwks`, issuer = `OIDC_ISSUER_URL`). 유효 Bearer JWT → `BearerTokenAuthenticationFilter` 가 `Authentication` 설정.
3. `InternalApiFilter` 를 **비종결(additive)** 로 리팩터: 유효 `X-Internal-Token`(또는 unconfigured+bypass) 시 `SecurityContext` 에 인증 토큰(예: `PreAuthenticatedAuthenticationToken` + authority `SCOPE_internal`/`ROLE_INTERNAL`) 설정 후 통과. **스스로 401 작성하지 않음** — 토큰 부재/불일치 시 단순 통과시키고 최종 `.authenticated()` 게이트가 401/403 결정.
4. 결과: 둘 중 하나라도 유효 → authenticated → 통과(AC-3/AC-4). 둘 다 없음 → `.authenticated()` → 401/403(AC-5 fail-closed). bypass=true(test) → 필터가 인증 설정 → 기존 `InternalControllerTest` 무토큰 통과 유지(AC-7).

**테스트 영향**: `InternalApiFilterTest`(8 메서드, 필터가 직접 401 작성함을 단언)는 필터 contract 변경으로 **재작성 대상** — "필터가 SecurityContext 인증을 설정/미설정" 단언으로 전환. (AC-7 의 보존 대상 = `OAuth2*IntegrationTest`/`InternalControllerTest`; `InternalApiFilterTest` 는 필터 자체 단위 테스트라 정당한 변경.) security-service 는 `InternalAuthFilter` 를 신규 `SecurityFilterChain` 안의 동일 비종결 패턴으로 흡수.

## 호출자 client 인벤토리 (seed 판단)

- **기존 보유(재사용)**: `community-service-client`, `membership-service-client`.
- **신규 seed 필요(GAP-내부 호출자)**: `admin-service-client`(admin→account), `auth-service-client`(auth→account/social), `security-service-client`(security→account), `account-service-client`(account→auth credential). ※ security-service `/internal` 의 호출자 목록은 구현 착수 시 1차 확인(현재 확인된 outbound = `security/.../client/AccountServiceClient.java` 의 security→account).

---

# Notes

- ADR-005 § "무중단 마이그레이션" 의 단계 1+2 에 해당. 단계 3(BE-318)·4(BE-319) 는 본 task main 머지 후 발행.
- `client_credentials` grant 는 신축이 아니라 **기존 동작 재사용**(ADR-003 회귀 매트릭스 `client_credentials → 200 PASS`). 본 task 의 신규 부분은 (a) 서비스별 client seed, (b) 수신측 JWT 검증 이중 허용.
- 완전 keyless(client_secret 제거)는 본 task 범위 밖 — mTLS 후속 ADR. 본 task 는 ④ workload identity 의 실질(중앙 발급·단기·호출자 신원)을 확보하는 단계.
