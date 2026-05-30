# Task ID

TASK-BE-318d

# Title

서비스 간(workload) 인증 무중단 전환 — 단계 3d (호출측 전환, membership-service): membership-service 의 account-service `/internal/accounts/{id}/status` 호출을 정적 `X-Internal-Token` 에서 GAP `client_credentials` 단기 JWT(`Authorization: Bearer`)로 전환한다. 수신측은 이미 이중 허용(BE-317)이므로 무중단. **이로써 account `/internal/**` 의 모든 호출자가 Bearer 가 되어 BE-319b(account 수신측 정적 토큰 제거)가 unblock 된다.** (ADR-005 옵션 A 단계 3)

# Status

ready

# Owner

backend

# Task Tags

- code
- security

---

# Dependency Markers

- **depends on**: TASK-BE-317(account 수신측 이중 허용 + `client_credentials` client seed). `membership-service-client` 는 V0009 에 **이미 사전 등록**(client_credentials, scopes `account.read`/`membership.read`; V0009 주석: "membership→account 마이그레이션용 사전 등록, BE-253 범위 밖") — 신규 seed 불필요.
- **blueprint**: TASK-BE-318c(auth caller, main 머지 #941). membership AccountStatusClient 도 `ResilienceClientFactory` RestClient 기반이라 동일 패턴(hand-rolled provider + per-call `setBearerAuth`) 적용.
- **sibling 선례**: TASK-BE-253(community→account/membership) 는 OAuth2 starter(WebClient) 로 전환. membership 은 RestClient+CircuitBreaker 구조 보존 위해 hand-rolled 채택(신규 의존성 0).
- **prerequisite of**: **TASK-BE-319b**(account 수신측 X-Internal-Token 제거). account 수신측 제거 선행 = TASK-BE-318(security, 완료) + BE-318b(admin, 완료) + BE-318c(auth, 완료) + BE-253(community, 완료) + **본 task(membership)**. 본 task 완료 시 account 호출자 전원 Bearer → BE-319b 진행 가능.

---

# Goal

membership-service `AccountStatusClient` 가 account-service `/internal/accounts/{id}/status` 를 호출할 때 GAP 발급 `client_credentials` 단기 JWT 를 `Authorization: Bearer` 로 첨부하고, 정적 `X-Internal-Token`(`membership.account-service.internal-token`) 전송을 중단한다(account 수신측이 이중 허용 중이므로 무중단).

# Scope

## In scope

- membership-service `infrastructure/client/AccountStatusClient` (→account `/internal/accounts/{id}/status`) 를 Bearer 로 전환(X-Internal-Token 제거).
- `GapClientCredentialsTokenProvider` (membership): BE-318c 복제 — plain `RestClient` + Jackson 으로 `/oauth2/token` POST(Basic auth `membership-service-client`), `access_token`/`expires_in` 캐시 + skew(60s) 전 갱신, lazy fetch. **신규 의존성 0**(`spring-boot-starter-oauth2-client` 미사용 — membership 은 resource-server 만 보유, client chain 도입 회피; RestClient+CB 구조 보존).
- application.yml: `gap.internal-client.{token-uri,client-id=membership-service-client,client-secret=membership-service-secret}`. `membership.account-service.internal-token` 제거.
- 테스트: 토큰 provider 단위(`GapClientCredentialsTokenProviderTest`), account status 호출을 trigger 하는 IT 에 `@MockitoBean GapClientCredentialsTokenProvider`(고정 bearer) + test yml `internal-token` 제거.

## Out of scope

- account 수신측 X-Internal-Token 제거 + 계약 spec 갱신 → **BE-319b**.
- membership **수신측** `/internal/membership/**`(community 가 호출, 이미 Bearer/resource-server) → 변경 없음.
- mTLS / 완전 keyless → 후속 ADR.

# Acceptance Criteria

- **AC-1**: membership `AccountStatusClient.check(...)` 가 `Authorization: Bearer <GAP client_credentials JWT>` 를 첨부하고 `X-Internal-Token` 을 더 이상 보내지 않는다(account 수신측 정상).
- **AC-2**: `GapClientCredentialsTokenProvider` 가 `/oauth2/token (client_credentials)` 에서 Basic auth 로 access_token 을 받아 캐시하고 만료(skew) 전 재사용한다.
- **AC-3**: 토큰 발급 실패 시 status 조회는 기존 CircuitBreaker/fail-closed(`AccountStatusUnavailableException`) 경로로 처리 — silent fallback 없음.
- **AC-4**: 회귀 0 — account 수신측 이중 허용 덕에 기존 membership IT(Subscription 활성/재활성 등) 유지.
- **AC-5**: 본 task 는 수신측 계약 spec 을 변경하지 않는다(갱신은 BE-319b). `membership-service-client`(V0009) 사용, 신규 seed 없음.

# Related Specs

- `specs/contracts/http/internal/` (membership→account status; 호출측 구현만 — 계약 텍스트 갱신은 BE-319b)
- `specs/services/membership-service/architecture.md`
- ADR-005 § 무중단 마이그레이션 단계 3

# Related Contracts

- `POST /oauth2/token` (grant_type=client_credentials, `membership-service-client`).
- account `/internal/accounts/{id}/status` — 인증이 X-Internal-Token → Bearer(수신측 이중 허용이라 계약 호환).

# Edge Cases

- 토큰 만료 경계: provider 가 skew(60s) 전 갱신, `synchronized` 단일 갱신.
- GAP 토큰 엔드포인트 장애: provider 실패 throw → `AccountStatusClient` 의 기존 CircuitBreaker/catch 가 `AccountStatusUnavailableException`(fail-closed) 로 처리.
- 테스트: account 를 WireMock 으로 stub 하는 membership IT 는 `@MockitoBean GapClientCredentialsTokenProvider`(고정 bearer) 로 토큰 fetch 우회. provider lazy 라 status 호출 안 trigger 하는 IT 무관.
- `membership-service-client` secret: V0009 컨벤션상 `membership-service-secret`(community-service-client=`community-service-secret` 와 동일 패턴). membership 은 gap e2e 미포함이라 e2e 검증 없음 — 컨벤션 신뢰.

# Failure Scenarios

- 캐시 미동작으로 매 호출 재발급 → AC-2 캐시 단위 테스트.
- X-token 제거 + Bearer 미첨부 → 수신측 401 → IT 가 Bearer 동반 status 호출 정상 동작으로 가드.
- membership 에 oauth2-client starter 유입으로 chain 교란 → starter 미사용으로 회피.

---

# Implementation Design Notes

- Blueprint = TASK-BE-318c. membership `AccountStatusClient` 는 `ResilienceClientFactory.buildRestClient` RestClient → `.headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))` per-call, 기존 `applyInternalToken` + `internalToken` 제거.
- `GapClientCredentialsTokenProvider`(membership `infrastructure/client/`): `synchronized currentBearer()`, volatile 캐시, RestClient + Jackson. client-id 기본 `membership-service-client`, client-secret 기본 `membership-service-secret`.
- 호출자 인벤토리(코드 검증 2026-05-30): membership AccountStatusClient = X-Internal-Token(`membership.account-service.internal-token`=`${ACCOUNT_SERVICE_INTERNAL_TOKEN:}`). 이것이 account `/internal/**` 의 **마지막 X-token 호출자**(security/admin/auth/community 는 전부 Bearer 완료).

---

# Notes

- ADR-005 단계 3 의 마지막 account caller(membership). 본 task 완료로 account `/internal/**` 호출자 전원 Bearer → **BE-319b(account 수신측 정적 토큰 제거) unblock**.
