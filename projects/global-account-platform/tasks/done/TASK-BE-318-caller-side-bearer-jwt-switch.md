# Task ID

TASK-BE-318

# Title

서비스 간(workload) 인증 무중단 전환 — 단계 3a (호출측 전환, security-service): security-service 의 account-service `/internal/accounts/{id}/lock` 호출을 정적 `X-Internal-Token` 에서 GAP `client_credentials` 단기 JWT(`Authorization: Bearer`)로 전환한다. 수신측은 이미 이중 허용(BE-317)이므로 무중단. (ADR-005 옵션 A 단계 3)

# Status

done

# Owner

backend

# Task Tags

- code
- security

---

# Dependency Markers

- **depends on**: TASK-BE-317 (단계 1+2 — 수신측 account/security 이중 허용 + `client_credentials` client seed). **main 머지 완료** (impl PR #934 / close #935).
- **prerequisite of**: TASK-BE-319 (단계 4 — 수신측 account/security 정적 토큰 제거 + 계약 spec 갱신). 단, account 수신측의 X-Internal-Token 제거는 account 의 **모든** 호출자 전환(아래 follow-up BE-318b/c + membership)이 선행 요건.
- **무중단 순서**: BE-317(이중 허용) → **본 task + 후속 호출측 전환** → BE-319(정적 토큰 제거).

## 호출측 전환 분할 (단계 3 footprint)

단계 3(호출측 전환)은 caller 별 IT footprint 가 커서 caller 단위로 분할한다. 본 task 는 가장 contained 한 security-service 만 다룬다.

- **본 task (3a)**: security → account (`AccountServiceClient`, 단일 호출 지점, X-Internal-Token → Bearer).
- **BE-318b (후속, 3b)**: admin → account(`AccountServiceClient`) + admin → security(`SecurityServiceClient`). admin 은 클라이언트 테스트 4종(Account/Security resilience+unit) + 다수 IT 영향 → 별도 task.
- **BE-318c (후속, 3c)**: auth → account(`AccountServiceClient`). 현재 **무인증**(auth 가 account `/internal` 호출 시 아무 헤더도 안 보냄 — account 가 token-configured 환경에서 401 날 잠재 갭) → Bearer 로 인증 추가. auth IT 6종이 account 를 호출(MockMvc 라 self-call 토큰 발급 불가) → 토큰 엔드포인트 처리 필요해 별도 task. auth `/internal` permitAll 수신측 전환과 함께 다루는 것이 자연스러움.
- **scope 외(별도 계열)**: admin → auth / account → auth (auth 수신측이 아직 `permitAll` → auth 수신측 dual-allow 이후), membership → account (TASK-BE-253 follow-up).

---

# Goal

security-service 가 account-service `/internal/accounts/{id}/lock` 를 호출할 때 GAP 발급 `client_credentials` 단기 JWT 를 `Authorization: Bearer` 로 첨부하고, 정적 `X-Internal-Token` 전송을 중단한다(account 수신측이 이중 허용 중이므로 무중단).

본 task 완료 시:
- security-service `AccountServiceClient`(auto-lock) 가 Bearer JWT 로 호출, X-Internal-Token 미전송.
- security-service 가 GAP `/oauth2/token` 에서 `security-service-client`(V0019) 로 토큰을 받아 **캐시 + 만료 전 갱신** 후 첨부.

# Scope

## In scope

- security-service `infrastructure/client/AccountServiceClient`(→account `/internal/accounts/{id}/lock`) 를 Bearer 로 전환(X-Internal-Token 제거).
- `GapClientCredentialsTokenProvider`: RestClient 로 `/oauth2/token` POST(Basic auth `security-service-client:secret`), `access_token`/`expires_in` 캐시, skew(60s) 전 갱신. **신규 의존성 0** — `spring-boot-starter-oauth2-client`(spring-security-web → default lock-everything chain 유발) 미사용, plain RestClient + Jackson.
- application.yml: `gap.internal-client.{token-uri,client-id,client-secret}` (community blueprint 의 `OIDC_TOKEN_URI`/`OIDC_ISSUER_URL` 기본값 재사용).
- 단위 테스트: 토큰 provider(Basic auth 발급 + 캐시 단일 호출), 클라이언트가 Bearer 첨부 + X-Internal-Token 미전송. IT: `DetectionE2EIntegrationTest` 의 auto-lock 경로가 토큰 엔드포인트(같은 WireMock 스텁) 경유.

## Out of scope (후속)

- admin/auth 호출측 전환 → **BE-318b / BE-318c**(위 분할 참조).
- 수신측 정적 토큰/permitAll 제거 + 계약 spec 갱신 → **BE-319**.
- mTLS / 완전 keyless → 후속 ADR.

# Acceptance Criteria

- **AC-1**: security-service `AccountServiceClient.lock(...)` 가 `Authorization: Bearer <GAP client_credentials JWT>` 를 첨부하고 `X-Internal-Token` 을 더 이상 보내지 않는다(account 수신측 200/정상).
- **AC-2**: `GapClientCredentialsTokenProvider` 가 `/oauth2/token (client_credentials)` 에서 Basic auth 로 access_token 을 받아 캐시하고, 만료(skew) 전 재사용한다(매 호출 재발급 금지).
- **AC-3**: 토큰 발급 실패 시 lock 호출은 기존 resilience(retry/circuit breaker) 경로로 명확히 실패 — 정적 토큰 silent fallback 없음.
- **AC-4**: 회귀 0 — account 수신측 이중 허용 덕에 기존 security IT(`DetectionE2EIntegrationTest` auto-lock SUCCESS 등) 유지.
- **AC-5**: 본 task 는 수신측 계약 spec(`*.md`) 을 변경하지 않는다(이중 허용 — 갱신은 BE-319).

# Related Specs

- `specs/contracts/http/internal/security-to-account.md` (인증 절 = X-Internal-Token; 호출측 구현만 — 계약 텍스트 갱신은 BE-319)
- `specs/services/security-service/architecture.md`
- ADR-005 § 무중단 마이그레이션 단계 3

# Related Contracts

- `POST /oauth2/token` (grant_type=client_credentials, `security-service-client`) — 토큰 발급.
- `POST /internal/accounts/{id}/lock` — 인증이 X-Internal-Token → Bearer 로 전환(수신측 이중 허용이라 계약 호환).

# Edge Cases

- 토큰 만료 경계: provider 가 skew(60s) 전 갱신. 동시 호출은 `synchronized` 로 단일 갱신.
- GAP 토큰 엔드포인트 장애: provider 가 실패 throw → lock 의 기존 retry/CB 가 처리. 캐시된 미만료 토큰이 있으면 계속 사용.
- 테스트: `DetectionE2EIntegrationTest` 는 account 를 stub 하는 WireMock 에 `/oauth2/token` 도 stub + `gap.internal-client.token-uri` 를 그쪽으로 → 실제 토큰 fetch → Bearer → stub 200 (e2e 검증). 단위 테스트는 mock provider.

# Failure Scenarios

- 캐시 미동작으로 매 호출 재발급 → AC-2 캐시 단위 테스트(`exactly(1)` verify).
- X-Internal-Token 제거 + Bearer 미첨부 → 수신측 401 → 단위 테스트가 Bearer 동반 + `withoutHeader("X-Internal-Token")` 단언(AC-1).
- security-service 에 oauth2-client starter 유입으로 default chain → starter 미사용으로 회피.

---

# Implementation Design Notes

- security `AccountServiceClient` 는 JDK `java.net.http.HttpClient` 사용 → Bearer 헤더 직접 세팅(`reqBuilder.header("Authorization", "Bearer " + tokenProvider.currentBearer())`), 기존 X-Internal-Token 블록 제거.
- `GapClientCredentialsTokenProvider` (security `infrastructure/client/`): `synchronized currentBearer()`, volatile 캐시, RestClient + Jackson. client-id 기본 `security-service-client`.
- community-service `OAuth2WebClientConfig`(starter 기반 WebClient) 가 idiomatic 선례이나, security-service 의 no-Spring-Security-web 아키텍처(BE-317 옵션 b) 보존 위해 hand-rolled 채택. (3a/3b/3c 공통 provider 의 libs 승격은 후속 리팩터 후보 — caller 별 중복 허용.)

## 구현 결과 (2026-05-30, 3-dim 검증)

- **AC-1**: `AccountServiceClientUnitTest` — lock 호출이 `Authorization: Bearer test-jwt` 첨부 + `withoutHeader("X-Internal-Token")` 단언. PASS.
- **AC-2**: `GapClientCredentialsTokenProviderTest` — Basic auth 발급 + 캐시 `exactly(1)` 단일 호출. PASS.
- **AC-3/4**: `DetectionE2EIntegrationTest` auto-lock 경로(`/oauth2/token` + lock 스텁) → SUCCESS 유지. PASS(CI Linux 포함).
- **AC-5**: 계약 spec 불변.

---

# Notes

- ADR-005 단계 3 의 첫 호출자(security). admin(BE-318b)·auth(BE-318c) 전환 후 BE-319 에서 account 수신측 X-Internal-Token 제거 가능(membership→account 전환도 선행 요건).
