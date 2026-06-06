# Integration — iam-platform (GAP) OIDC

> 본 문서는 `fan-platform` 의 모든 서비스가 GAP 를 표준 OIDC IdP 로 사용하는 방식을 1쪽으로 요약한다.
> ADR-001 (D1=A) 와 GAP 의 [consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) 의 fan-platform 적용본이며,
> wms-platform 의 [동일 통합](../../../wms-platform/specs/integration/iam-integration.md) 과 같은 패턴을 따른다.

---

## Tenant Identity

- `tenant_id` = `fan-platform`
- `tenant_type` = `B2C_CONSUMER` (self-service 가입 + admin-provisioned 운영자가 공존)
- 일반 팬 사용자 계정은 GAP 의 [self-service signup endpoint](../../../iam-platform/specs/contracts/http/auth-api.md) 로 생성된다.
- B2C 운영자 (artist 등록 / 모더레이션 / B2C admin 콘솔 사용자) 의 계정은 GAP 의 [account internal provisioning API](../../../iam-platform/specs/contracts/http/internal/account-internal-provisioning.md) 를 통해 생성된다.

---

## OIDC Endpoints (consumed by fan-platform)

| 항목 | 값 (dev 기본) | 환경 변수 |
|---|---|---|
| Issuer URL | `http://iam.local` | `OIDC_ISSUER_URL` |
| JWKS URI | `${OIDC_ISSUER_URL}/oauth2/jwks` | `JWT_JWKS_URI` |
| OIDC Discovery | `${OIDC_ISSUER_URL}/.well-known/openid-configuration` | n/a |
| Token endpoint | `${OIDC_ISSUER_URL}/oauth2/token` | n/a |
| Authorization endpoint | `${OIDC_ISSUER_URL}/oauth2/authorize` | n/a |

Spring Boot 설정 키:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URL}
          jwk-set-uri: ${JWT_JWKS_URI}
```

`fanplatform.oauth2.allowed-issuers` 는 D2-b deprecation 윈도우 동안 SAS issuer 와 legacy `iam-platform` issuer 양쪽을 허용한다 (TASK-BE-253 패턴).

---

## OAuth Clients (등록은 GAP 의 시드 마이그레이션에서 생성)

| Client ID | Grant Types | PKCE | Redirect URIs | Flyway |
|---|---|---|---|---|
| `fan-platform-user-flow-client` | `authorization_code` + `refresh_token` | 필수 (`require_proof_key=true`) | `http://localhost:3000/api/auth/callback/iam`, `http://fan-platform.local/api/auth/callback/iam` | V0011 (TASK-MONO-026) |
| `fan-platform-internal-services-client` | `client_credentials` | No | — | v2 DEFERRED |

`fan-platform-user-flow-client` 는 confidential client (secret + PKCE 동시 사용) 로 등록됨:
- `client_authentication_methods`: `client_secret_basic`
- `require_proof_key`: `true`
- `access_token_time_to_live`: PT15M (900 s)
- `refresh_token_time_to_live`: PT24H (86400 s)

Secret 은 V0011 Flyway 시드에 BCrypt(strength=10) 해시로 저장.
dev 평문 secret 은 `fan-platform-dev` (`.env.example` 의 `OIDC_CLIENT_SECRET` 참고).
production 은 `FAN_PLATFORM_USER_FLOW_CLIENT_SECRET` 환경 변수로 교체 후 admin API 로 갱신.

---

## Scopes

`<tenant>.<resource>.<action>` 명명 규칙. 모두 `tenant_id=fan-platform` scope 으로 GAP 의 `oauth_scopes` 에 등록.

| Scope | 설명 |
|---|---|
| `fan-platform.community.read` / `fan-platform.community.write` | community-service 리소스 (post, comment, reaction, feed) |
| `fan-platform.artist.read` / `fan-platform.artist.write` | artist-service 리소스 (artist profile, follow, fandom metadata) |
| `fan-platform.membership.read` / `fan-platform.membership.write` | membership-service 리소스 (구독 / 티어 접근). **spec 확정 TASK-FAN-BE-008; 구현 FAN-BE-009.** scope 는 사전 등록 상태. |
| `fan-platform.notification.write` | notification-service 발송 (**v2 DEFERRED** — service 미구현, scope 만 사전 등록) |

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`) 는 GAP 의 시스템 scope 에서 자동 적용.

---

## Token 검증 규칙 (각 fan-platform 서비스의 Resource Server 가 적용)

1. **서명 검증** — GAP 의 JWKS 로 RS256 서명 검증.
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (JwtTimestampValidator).
3. **Issuer 검증** — `AllowedIssuersValidator` 로 SAS issuer + legacy `iam-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Tenant 검증** — `TenantClaimValidator` 로 `tenant_id` claim 이 `fan-platform` 또는 `*` (SUPER_ADMIN platform-scope) 인 경우만 통과. 그 외 (`wms`, `ecommerce`, 향후 `erp`/`scm`/`mes`) → `tenant_mismatch` → 403 `TENANT_FORBIDDEN`.
5. **Role / Scope 검증** — 서비스의 `SecurityConfig` 가 `@PreAuthorize` 또는 `requestMatchers().hasRole(...)` 으로 enforce.

---

## membership-service — OIDC RS + workload identity (TASK-FAN-BE-008)

`membership-service` (spec 확정 FAN-BE-008, 구현 FAN-BE-009) 는 두 방향으로 GAP 를 소비한다:

1. **End-user 요청** — 다른 fan-platform 서비스와 동일하게 OAuth2 Resource Server 패턴으로 GAP 의 RS256 access token 을 검증하고 `tenant_id ∈ { fan-platform, * }` 만 통과시킨다 (위 § Token 검증 규칙 동일 적용). 적용 경로 = `/api/fan/memberships/**`.
2. **Workload identity (피호출)** — `membership-service` 의 내부 access-check 엔드포인트 `GET /internal/membership/access` 는 community-service 가 **GAP `client_credentials` JWT** (ADR-MONO-005 workload identity) 로 호출한다. membership-service 는 수신측으로서 issuer + 서명 + 인식된 internal client 신원/role 을 검증하며, end-user 토큰·무토큰은 401/403 으로 거부한다. 이 경로는 게이트웨이 미노출 (내부 docker 망 전용). 위 표의 `fan-platform-internal-services-client` (`client_credentials`) 가 그 호출자 client 의 등록 슬롯이다.

상세 = `specs/services/membership-service/architecture.md` § Internal Access-Check Contract + `specs/contracts/http/membership-api.md`.

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `tenant_id != fan-platform` (cross-tenant, 그리고 `*` 가 아님) | 403 | `TENANT_FORBIDDEN` |
| 유효 토큰이지만 role/scope 부족 | 403 | `FORBIDDEN` |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 을 따른다.

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정.
- [ ] `fan-platform-user-flow-client` 의 redirect URI 를 실제 fan-platform-web 도메인으로 갱신.
- [ ] `fan-platform-internal-services-client` 의 client_secret 을 secret manager 로 회전.
- [ ] D2-b deprecation 윈도우 종료 시 `fanplatform.oauth2.allowed-issuers` 에서 `iam-platform` 제거.
- [ ] GAP 의 `fan-platform` 테넌트 등록 (admin-service [Tenant Lifecycle API](../../../iam-platform/specs/contracts/http/admin-api.md#tenant-lifecycle-task-be-256)).

---

## 참조

- [ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — GAP IdP 승급
- [GAP consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) — 가이드 본문
- [GAP auth-api.md § OAuth2 / OIDC Endpoints](../../../iam-platform/specs/contracts/http/auth-api.md#oauth2--oidc-endpoints-standard-adr-001)
- [GAP multi-tenancy.md](../../../iam-platform/specs/features/multi-tenancy.md)
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- [wms-platform 의 동일 통합](../../../wms-platform/specs/integration/iam-integration.md) — reference pattern
- TASK-FAN-BE-001 — fan-platform OIDC Resource Server 부트스트랩 (본 통합의 구현 태스크)
