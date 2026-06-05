# Task ID

TASK-BE-253

# Title

community-service + membership-service OIDC 통합 (FROZEN 예외 — IdP 마이그레이션 한정)

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

GAP의 product-layer demo consumer 인 `community-service` 와 `membership-service` 가 ADR-001 (D1=A) 에 따른 표준 OIDC token 검증으로 동작하도록 마이그레이션한다.

이 두 서비스는 `PROJECT.md`에서 **FROZEN — 신규 기능 태스크 발행 금지** 로 선언되어 있다. 본 태스크는 그 선언의 **단발성 예외**로, 새 기능을 추가하지 않고 IdP 표준화에 따른 인증 검증 경로만 OIDC Resource Server 패턴으로 교체한다. 상품 도메인 변경 없음.

완료 시점:

1. 두 서비스가 `spring-boot-starter-oauth2-resource-server` 의존성을 사용해 GAP의 JWKS URI 기반으로 access token을 검증.
2. `tenant_id` claim이 `fan-platform` 인 토큰만 정상 처리 (cross-tenant 거부).
3. 기존 `account-service` 내부 호출 (community-to-account, community-to-membership) 은 service-to-service `client_credentials` token 으로 전환.
4. 기존 GAP 자체 발급 token (`POST /api/auth/login`) 도 SAS 발급 token 도 동일 JWKS로 검증되므로 D2-b deprecate 기간 내 양쪽 모두 동작.

---

# Scope

## In Scope

- `apps/community-service`, `apps/membership-service` 양쪽:
  - `org.springframework.boot:spring-boot-starter-oauth2-resource-server` 추가
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri = ${OIDC_ISSUER_URL}` 설정
  - `tenant_id` claim 검증 인터셉터 (`fan-platform` 만 허용; 검증 실패 시 403)
  - 기존 자체 JWT 검증 코드 제거 (deprecation 기간이라도 두 검증 경로 공존은 코드 복잡도만 증가시키므로 본 태스크 머지 시점에 일괄 교체)
- service-to-service 호출:
  - community → account, community → membership 호출 시 `client_credentials` grant 로 token 획득
  - `WebClient` 에 `ServerOAuth2AuthorizedClientExchangeFilterFunction` 적용
  - GAP에 두 서비스용 client 등록 (TASK-BE-252의 admin API 미준비 시 Flyway seed로 사전 등록):
    - `community-service-client` (`tenant_id=fan-platform`, `allowed_grants=[client_credentials]`, `allowed_scopes=[account.read, membership.read]`)
    - `membership-service-client` (동일 패턴)
- 기존 internal API 호출 시 `X-Internal-Token` 헤더 사용처를 OAuth2 access token 으로 대체 — `auth-to-account.md`, `community-to-account.md`, `community-to-membership.md` 계약 갱신.
- 통합 테스트: SAS 발급 token + 기존 자체 발급 token 양쪽 모두 검증 통과.

## Out of Scope

- community/membership-service에 새로운 기능 추가 — FROZEN 유지.
- fan-platform 외부 SPA 클라이언트 변경 — 별도 외부 작업.
- `account.read`, `membership.read` 외 신규 scope 정의 — 본 태스크에서 최소 2개만 정의.
- 기존 `X-Internal-Token` 인프라 코드 완전 제거 — D2-b deprecate 기간 동안 다른 호출 경로(예: admin → account)에서 잔존 가능. 본 태스크는 community/membership 두 서비스 한정.

---

# Acceptance Criteria

- [ ] community-service, membership-service 양쪽이 `spring-boot-starter-oauth2-resource-server` 의존성을 추가하고 자체 JWT 검증 코드 제거.
- [ ] 두 서비스의 모든 protected endpoint가 SAS 발급 OIDC token (Bearer) 으로 인증됨.
- [ ] `tenant_id != fan-platform` 토큰 호출 시 403.
- [ ] community → account, community → membership 호출이 `client_credentials` token으로 동작 (`Authorization: Bearer <token>`).
- [ ] GAP에 `community-service-client`, `membership-service-client` 두 client가 시드 데이터 또는 admin API로 등록됨.
- [ ] 회귀: 기존 GAP 자체 발급 token(`POST /api/auth/login`) 으로 호출해도 검증 통과 (deprecate 기간 호환성).
- [ ] cross-tenant 누출 회귀: tenantA(`wms`) 사용자 token으로 community-service 호출 시 403.
- [ ] `specs/contracts/http/internal/community-to-account.md`, `community-to-membership.md` 갱신 — 인증 헤더 표기 OAuth2 변경.
- [ ] `./gradlew :projects:global-account-platform:apps:community-service:check`, `membership-service:check` PASS.
- [ ] 두 서비스의 integration test PASS.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification. **FROZEN 예외 적용**: 본 태스크는 IdP 마이그레이션 한정의 단발성 예외이며 새 기능 추가 금지.

- `docs/adr/ADR-001-oidc-adoption.md` § 6 Option A 후속 태스크
- `specs/services/community-service/architecture.md`
- `specs/services/membership-service/architecture.md`
- `specs/contracts/http/internal/community-to-account.md`
- `specs/contracts/http/internal/community-to-membership.md`
- `specs/features/multi-tenancy.md` § "Cross-Tenant Security Rules"

# Related Skills

- `.claude/skills/backend/` OAuth2 Resource Server 관련 (있으면)

---

# Related Contracts

- `specs/contracts/http/internal/community-to-account.md` (인증 헤더 변경)
- `specs/contracts/http/internal/community-to-membership.md` (동일)

---

# Target Service

- `community-service`
- `membership-service`

---

# Architecture

- `infrastructure/security/`: OAuth2 Resource Server 설정 (JWKS URI 기반 검증), `tenant_id` claim 인터셉터.
- `infrastructure/client/`: `WebClient` 빈에 `client_credentials` 토큰 자동 첨부 필터.
- 자체 JWT 검증 코드 제거: 두 서비스의 기존 `JwtAuthenticationFilter` 등.

---

# Implementation Notes

- **JWKS URI 캐싱**: Resource Server 가 JWKS를 5분 단위로 캐싱 (Spring 기본). 키 회전 시 cache miss 후 새 키 자동 fetch.
- **Issuer URL 일치**: `OIDC_ISSUER_URL` 이 GAP의 discovery 문서 `issuer` 와 정확히 일치해야 함 (trailing slash 포함). 환경변수로 외부화.
- **Client secret 관리**: 두 client의 secret은 환경변수 + Spring `client-secret`. 시드 데이터에 BCrypt 해시 삽입, 평문은 secret manager (또는 `.env.local` for dev).
- **Scope 사용**: `account.read` scope는 account-service의 `/internal/accounts/*` GET에 매핑. account-service는 이 scope를 검증하지만, 본 태스크 범위에서는 community/membership 호출 측 변경 위주. account-service의 scope enforcement는 별도 cleanup task로 분리 가능 (단, 호출 측에서 scope 누락 시 403이 떨어져야 의미 있으므로 동일 PR에 account-service의 scope check도 추가하는 것을 권장).
- **`X-Internal-Token` 잔존 코드**: 두 서비스의 호출 코드에서 헤더 추가 부분만 제거. account-service 측 `X-Internal-Token` 검증 코드는 다른 호출자(admin → account 등)와 공유될 수 있으므로 본 태스크에서 제거하지 않음.

---

# Edge Cases

- **Token 만료 시 자동 갱신**: `client_credentials` token은 짧은 TTL (e.g. 10분). `WebClient` filter가 401 응답 시 토큰 재발급 후 재시도 — Spring Security OAuth2 Client 기본 동작.
- **GAP 가용성 저하**: token endpoint 호출 실패 시 community-service의 outbound 호출 전체 실패. circuit breaker 적용 검토 (resilience4j 이미 사용 중).
- **Token replay**: 동일 access token을 다른 instance가 재사용 가능 (stateless). DPoP 미도입 상태에서는 표준. mTLS 도입은 후속.
- **첫 기동 시 GAP 미준비**: community-service 기동 → token endpoint 호출 → GAP 미가용. 기동은 성공하되 첫 호출에서 실패 — graceful degradation 정책은 기존 패턴 유지.

---

# Failure Scenarios

- **Client secret 불일치**: client 등록 시점과 환경변수의 secret 불일치 → token endpoint 401. dev/staging에서 secret rotation 시 환경변수도 동기 갱신 필요.
- **Cross-tenant token 사용**: `wms` tenant token으로 community 호출 → 403. 가능한 시나리오: 운영자 실수, 보안 공격. 이벤트(`auth.token.tenant.mismatch`)로 기록.
- **JWKS endpoint timeout**: Resource Server fetch 5초 timeout 후 검증 실패 → 401. GAP 가용성에 강하게 의존.

---

# Test Requirements

- 단위 테스트:
  - `tenant_id` claim 검증 필터: `fan-platform` 통과, 그 외 403.
  - `WebClient` 토큰 자동 첨부 필터.
- 통합 테스트 (`@Tag("integration")`):
  - SAS 발급 token으로 community-service 보호 endpoint 호출.
  - GAP 자체 발급 token (`POST /api/auth/login`)으로 동일 호출 → 통과 (호환성).
  - `client_credentials` E2E: community-service → account-service.
  - cross-tenant 회귀: wms tenant token → community-service 호출 → 403.

---

# Definition of Done

- [ ] Implementation completed in both services
- [ ] Unit + integration tests added and passing
- [ ] `specs/contracts/http/internal/community-to-account.md`, `community-to-membership.md` 갱신
- [ ] FROZEN 예외 사유 PR 설명에 명시 (PROJECT.md re-open 아님)
- [ ] CI green
- [ ] Ready for review
