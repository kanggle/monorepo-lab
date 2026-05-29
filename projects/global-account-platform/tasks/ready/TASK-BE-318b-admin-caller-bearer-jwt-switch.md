# Task ID

TASK-BE-318b

# Title

서비스 간(workload) 인증 무중단 전환 — 단계 3b (호출측 전환, admin-service): admin-service 의 account-service `/internal/accounts/**` 및 security-service `/internal/security/**` 호출을 정적 `X-Internal-Token` 에서 GAP `client_credentials` 단기 JWT(`Authorization: Bearer`)로 전환한다. 수신측은 이미 이중 허용(BE-317)이므로 무중단. admin → auth 호출은 본 task 범위 밖(유지). (ADR-005 옵션 A 단계 3)

# Status

ready

# Owner

backend

# Task Tags

- code
- security

---

# Dependency Markers

- **depends on**: TASK-BE-317 (수신측 이중 허용 + `client_credentials` client seed V0019, main 머지 완료), TASK-BE-318 (단계 3a security caller — blueprint, main 머지 완료).
- **sibling**: TASK-BE-318c (auth caller). 독립 — 순서 무관.
- **prerequisite of**: TASK-BE-319 (수신측 X-Internal-Token 제거). security 수신측 X-token 제거는 본 task(유일한 security 호출자=admin) 완료가 선행 요건. account 수신측 X-token 제거는 본 task + BE-318c + membership→account 전환이 모두 선행.

---

# Goal

admin-service 가 account-service / security-service 의 `/internal/**` 를 호출할 때 GAP 발급 `client_credentials` 단기 JWT 를 `Authorization: Bearer` 로 첨부하고, 해당 호출의 정적 `X-Internal-Token` 전송을 중단한다(수신측 이중 허용이라 무중단). admin → auth 호출은 auth 수신측이 아직 `permitAll` 이므로 X-Internal-Token 을 **유지**한다.

# Scope

## In scope

- admin-service `infrastructure/client/AccountServiceClient` (→ account `/internal/accounts/**`) 를 Bearer 로 전환(X-Internal-Token 제거).
- admin-service `infrastructure/client/SecurityServiceClient` (→ security `/internal/security/**`) 를 Bearer 로 전환.
- 두 client 모두 `admin-service-client`(V0019, secret 기본 "secret") 사용.
- `GapClientCredentialsTokenProvider` (admin 용): BE-318 security 구현을 복제 — RestClient 로 `/oauth2/token` POST(Basic auth `admin-service-client`), `access_token`/`expires_in` 캐시 + skew(60s) 전 갱신, lazy fetch. **`spring-boot-starter-oauth2-client` 미사용**(spring-security-web default chain 회피), 신규 의존성 0.
- application.yml: `gap.internal-client.{token-uri,client-id=admin-service-client,client-secret}`.
- 테스트: 토큰 provider 단위, 두 client 의 Bearer 첨부 + X-Internal-Token 미전송, 회귀.

## Out of scope

- admin-service `AuthServiceClient` (→ auth `/internal/auth/**`): auth 수신측 `permitAll` → X-Internal-Token **유지**. auth 수신측 dual-allow 도입 이후 별도 전환.
- auth → account → **BE-318c**. 수신측 X-token 제거 + 계약 spec 갱신 → **BE-319**.

# Acceptance Criteria

- **AC-1**: admin `AccountServiceClient` 호출이 `Authorization: Bearer <GAP JWT>` 첨부 + X-Internal-Token 미전송(account 수신측 정상 응답).
- **AC-2**: admin `SecurityServiceClient` 호출이 Bearer 첨부 + X-Internal-Token 미전송(security 수신측 정상 응답).
- **AC-3**: 토큰 provider 가 `/oauth2/token (client_credentials)` Basic auth 로 발급·캐시하고 만료(skew) 전 재사용(매 호출 재발급 금지).
- **AC-4**: admin `AuthServiceClient`(→auth) 는 변경 없이 X-Internal-Token 유지(회귀 0).
- **AC-5**: 토큰 발급 실패 시 호출은 기존 resilience 경로로 명확히 실패(silent fallback 없음).
- **AC-6**: 회귀 0 — 기존 admin 클라이언트 테스트(`AccountServiceClientResilienceTest`/`AccountServiceClientUnitTest`/`SecurityServiceClientCircuitBreakerTest`/`SecurityServiceClientUnitTest`) 및 account/security 호출을 trigger 하는 admin IT 유지.
- **AC-7**: 본 task 는 수신측 계약 spec(`*.md`) 을 변경하지 않는다(이중 허용 — 갱신은 BE-319).

# Related Specs

- `specs/contracts/http/internal/admin-to-account.md` (인증 절 = X-Internal-Token; 호출측 구현만 — 갱신은 BE-319). admin→security 계약(있다면)도 동일.
- `specs/services/admin-service/architecture.md`
- ADR-005 § 무중단 마이그레이션 단계 3

# Related Contracts

- `POST /oauth2/token` (grant_type=client_credentials, `admin-service-client`).
- account `/internal/accounts/**`, security `/internal/security/**` — 인증 X-Internal-Token → Bearer(수신측 이중 허용이라 계약 호환).

# Edge Cases

- admin 의 세 client(Account/Security/Auth)가 동일 property `admin.downstream.internal-token` 을 공유 → account/security client 만 Bearer 로 바꾸고 auth client 의 X-Internal-Token 은 유지(혼합 상태 정상).
- admin client HTTP 메커니즘(RestClient/RestTemplate 등) 확인 후 그에 맞게 Bearer 첨부(인터셉터 또는 per-call header).
- 토큰 만료 경계/엔드포인트 장애: BE-318 provider 와 동일(synchronized 단일 갱신, 실패 throw → 기존 resilience).
- 테스트: account/security 를 WireMock 으로 stub 하는 admin IT 는 같은 WireMock 에 `/oauth2/token` 도 stub + `gap.internal-client.token-uri` 를 그쪽으로(BE-318 `DetectionE2EIntegrationTest` 처리 참고). provider 는 lazy 라 호출 trigger 안 하는 IT 는 무관.

# Failure Scenarios

- 캐시 미동작으로 매 호출 재발급 → AC-3 캐시 단위 테스트.
- X-token 제거 + Bearer 미첨부 → 수신측 401 → client 단위 테스트가 Bearer 동반 + `withoutHeader(X-Internal-Token)` 단언.
- admin 에 oauth2-client starter 유입으로 default chain 위험 → starter 미사용으로 회피(admin 은 이미 Spring Security 보유 — 단 hand-rolled provider 로 통일).
- auth client 까지 실수로 Bearer 전환 → auth permitAll 이라 무해하나 scope 위반 → AC-4 로 가드.

---

# Implementation Design Notes

- Blueprint = TASK-BE-318(security) 구현. `apps/security-service/.../infrastructure/client/GapClientCredentialsTokenProvider.java` 를 admin 패키지로 복제(default client-id=`admin-service-client`).
- admin client HTTP 방식 먼저 확인: RestClient 면 `.mutate().requestInterceptor(...)` 또는 per-call `.header("Authorization", "Bearer "+provider.currentBearer())`.
- 호출자 인벤토리(코드 검증 2026-05-30): admin AccountServiceClient/SecurityServiceClient = X-Internal-Token(`admin.downstream.internal-token`=`${INTERNAL_API_TOKEN:}`), admin AuthServiceClient = X-Internal-Token(동상, 유지).
- 공통 provider 의 libs 승격은 후속 리팩터 후보(caller 별 중복 허용; libs 변경은 monorepo-level task).

---

# Notes

- ADR-005 단계 3 의 admin caller. 본 task + BE-318c(auth) + membership→account 전환 완료 후 BE-319 에서 account 수신측 X-Internal-Token 제거 가능. security 수신측은 본 task 완료 시 제거 가능(유일 호출자=admin).
