# Task ID

TASK-BE-318c

# Title

서비스 간(workload) 인증 무중단 전환 — 단계 3c (호출측 전환, auth-service): auth-service 의 account-service `/internal/accounts/**` 호출(현재 **무인증**)에 GAP `client_credentials` 단기 JWT(`Authorization: Bearer`)를 첨부한다. 잠재 인증 갭(account 가 token-configured 환경일 때 401) 해소. 수신측은 이미 이중 허용(BE-317)이므로 무중단. (ADR-005 옵션 A 단계 3)

# Status

done

# Owner

backend

# Task Tags

- code
- security

---

# Dependency Markers

- **depends on**: TASK-BE-317 (수신측 이중 허용 + client seed V0019, main 머지 완료), TASK-BE-318 (단계 3a — blueprint, main 머지 완료).
- **sibling**: TASK-BE-318b (admin caller). 독립.
- **prerequisite of**: TASK-BE-319 (account 수신측 X-Internal-Token 제거는 본 task + BE-318b + membership→account 전환이 모두 선행).

---

# Goal

auth-service `AccountServiceClient` 가 account-service `/internal/accounts/**`(status / social-signup / profile)를 호출할 때 GAP 발급 `client_credentials` 단기 JWT 를 `Authorization: Bearer` 로 첨부한다. 현재 이 client 는 **어떤 인증 헤더도 보내지 않아**, account 수신측이 token-configured(bypass off) 환경이면 BE-317 이후 `.authenticated()` 에 의해 401 이 날 수 있는 잠재 갭이 있다 — 본 task 가 이를 인증으로 해소한다.

# Scope

## In scope

- auth-service `infrastructure/client/AccountServiceClient` (→ account `/internal/accounts/{id}/status`, `/internal/accounts/social-signup`, `/internal/accounts/{id}/profile`) 에 Bearer 첨부(기존 무인증 → 인증). client = `auth-service-client`(V0019, secret 기본 "secret").
- `GapClientCredentialsTokenProvider` (auth 용): BE-318 복제. **auth-service 가 자기 SAS `/oauth2/token` 을 호출(self-call)** 해 토큰 발급. **lazy fetch**(첫 outbound 호출 시) 로 startup self-dependency 회피. starter 미사용(신규 의존성 0).
- application.yml: `gap.internal-client.{token-uri,client-id=auth-service-client,client-secret}`.
- 테스트: 토큰 provider 단위, client 의 Bearer 첨부, 회귀.

## Out of scope

- admin → account/security → **BE-318b**. 수신측 X-token 제거 + 계약 spec 갱신 → **BE-319**.
- auth-service **수신측** `/internal/**` permitAll → dual-allow 전환(별도 task). 본 task 는 auth 의 **호출측**(auth→account)만.

# Acceptance Criteria

- **AC-1**: auth `AccountServiceClient` 의 status/social-signup/profile 호출이 모두 `Authorization: Bearer <GAP JWT>` 첨부(account 수신측 정상 응답). 기존 무인증 → 인증.
- **AC-2**: 토큰 provider 가 `/oauth2/token (client_credentials)` Basic auth 로 발급·캐시하고 만료(skew) 전 재사용.
- **AC-3**: 토큰 발급 lazy — 부팅 시 auth 가 자기 SAS 에 의존하지 않음(startup self-dependency 없음).
- **AC-4**: 토큰 발급 실패 시 호출은 기존 resilience(retry/circuit breaker) 경로로 처리.
- **AC-5**: 회귀 0 — auth 의 기존 OAuth2/login/social IT 및 `AccountServiceClientUnitTest` 유지. self-call 이 SAS 체인과 간섭하지 않음.
- **AC-6**: 본 task 는 수신측 계약 spec(`*.md`) 을 변경하지 않는다(갱신은 BE-319).

# Related Specs

- `specs/contracts/http/internal/auth-to-account.md`, `auth-to-account-social.md` (현재 인증 절; 호출측 구현만 — 갱신은 BE-319)
- `specs/services/auth-service/architecture.md`
- ADR-005 § 무중단 마이그레이션 단계 3

# Related Contracts

- `POST /oauth2/token` (grant_type=client_credentials, `auth-service-client`).
- account `/internal/accounts/{id}/status`, `/internal/accounts/social-signup`, `/internal/accounts/{id}/profile`.

# Edge Cases

- **auth self-call**: auth 가 자기 `/oauth2/token` 호출 → SAS 가 client_credentials 정상 처리(BE-317 IT 입증). lazy 로 startup block 회피.
- **테스트의 토큰 엔드포인트**: auth IT 는 @SpringBootTest+MockMvc 라 실제 포트가 없어 self HTTP 호출(localhost:8081)이 닿지 않음. account 를 호출하는 auth IT(약 6종: AuthIntegrationTest / DeviceSessionIntegrationTest / OAuth2AuthCodePkceIntegrationTest / OAuth2RefreshTokenIntegrationTest / OAuthLoginIntegrationTest / PlatformConsoleOidcClientSeedIntegrationTest 등 account stub 보유분)은 (a) account WireMock 에 `/oauth2/token` 도 stub + `gap.internal-client.token-uri` 를 그쪽으로 돌리거나, (b) `GapClientCredentialsTokenProvider` 를 `@MockitoBean` 으로 대체(고정 토큰 반환). provider 는 lazy 라 account 호출을 trigger 하지 않는 IT 는 무관.
- `AccountServiceClientUnitTest` 는 `cachedRestClient` 를 reflection 으로 교체(HTTP/1.1) → 인터셉터 우회. 생성자 시그니처 변경(provider 인자 추가)에 맞춰 호출부만 수정하고, Bearer 단언은 별도(인터셉터 단위 또는 mock provider) 로.

# Failure Scenarios

- 무인증 유지 시 account token-configured 환경에서 401 → 본 task 의 Bearer 첨부로 해소(AC-1).
- startup 에 self-call 발생 → eager 발급 금지(lazy) → AC-3.
- auth IT 토큰 엔드포인트 미처리로 대량 IT 실패 → Edge Cases (a)/(b) 로 선제 처리. 착수 시 account 호출 trigger IT 를 먼저 식별할 것.

---

# Implementation Design Notes

- Blueprint = TASK-BE-318(security). auth `AccountServiceClient` 는 RestClient(ResilienceClientFactory) 기반 — `.mutate().requestInterceptor(bearerInterceptor)` 로 build 양 경로(env-based + test-only 생성자)에 인터셉터 적용 권장. 인터셉터: `request.getHeaders().setBearerAuth(provider.currentBearer())`.
- 호출자 인벤토리(코드 검증 2026-05-30): auth AccountServiceClient = **무인증**(X-Internal-Token / Authorization 둘 다 없음).
- **착수 전 권고**: auth IT footprint 가 커서 토큰 엔드포인트 처리 전략(WireMock stub vs @MockitoBean)을 먼저 정하고, account 호출을 실제 trigger 하는 IT 를 grep 으로 식별(`stubFor` + `/internal/accounts` / `social-signup`).

---

# Notes

- ADR-005 단계 3 의 auth caller. account 수신측 X-Internal-Token 제거(BE-319)는 본 task + BE-318b + membership→account 전환이 모두 끝나야 가능.
