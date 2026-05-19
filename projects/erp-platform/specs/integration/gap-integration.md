# Integration — global-account-platform (GAP) OIDC

> 본 문서는 `erp-platform` 의 모든 서비스가 GAP 를 표준 OIDC IdP(SSO) 로 사용하는 방식을 1쪽으로 요약한다.
> [GAP ADR-001](../../../global-account-platform/docs/adr/ADR-001-oidc-adoption.md) 의 erp-platform 적용본이며,
> [scm-platform](../../../scm-platform/specs/integration/gap-integration.md) /
> [finance-platform](../../../finance-platform/specs/integration/gap-integration.md) 의 같은 통합 패턴을 따른다.

---

## Tenant Identity

- `tenant_id` = `erp`
- `tenant_type` = `B2B_ENTERPRISE` (내부-서비스 모델 — scm / finance 와 동일 type)
- v1 = backend only — self-service signup endpoint 사용 안 함 (internal-system 경계 — 외부 공개 트래픽 없음). 모든 운영자 / 시스템 계정은
  관리자 API ([account internal provisioning](../../../global-account-platform/specs/contracts/http/internal/account-internal-provisioning.md))
  로 생성한다.

---

## OIDC Endpoints (consumed by erp-platform)

| 항목 | 값 (dev 기본) | 환경 변수 |
|---|---|---|
| Issuer URL | `http://gap.local` | `OIDC_ISSUER_URL` |
| JWKS URI | `${OIDC_ISSUER_URL}/oauth2/jwks` | `JWT_JWKS_URI` |
| OIDC Discovery | `${OIDC_ISSUER_URL}/.well-known/openid-configuration` | n/a |
| Token endpoint | `${OIDC_ISSUER_URL}/oauth2/token` | n/a |
| Authorization endpoint | `${OIDC_ISSUER_URL}/oauth2/authorize` (콘솔 public client 도입 시) | n/a |

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

> **Edge Case — JWKS endpoint 정렬**: V0018 시드 SQL 은 GAP 의 표준
> `/oauth2/jwks` 엔드포인트를 사용한다 (`/.well-known/jwks.json` 아님). erp-platform
> 서비스의 `application.yml` default 도 이 endpoint 와 정렬되어 있다 — scm / finance /
> wms JWKS URI 정렬과 동일.

---

## OAuth Clients (등록은 GAP 의 시드 마이그레이션에서 생성)

V0018 시드 ([TASK-MONO-119](../../../../tasks/ready/TASK-MONO-119-erp-platform-bootstrap-artifact.md)):

| Client ID | Grant Types | PKCE | Redirect URIs | Flyway |
|---|---|---|---|---|
| `erp-platform-internal-services-client` | `client_credentials` | No | — | V0018 (TASK-MONO-119) |
| `erp-platform-user-flow-client` | `authorization_code` + `refresh_token` | 필수 | (TBD) | v2 DEFERRED |

`erp-platform-internal-services-client` 의 등록 메타:
- `client_authentication_methods`: `client_secret_basic`
- `access_token_time_to_live`: PT30M (1800 s)
- `aud`: `erp-platform-internal-services-client`
- 부여 가능 scope: `erp.read`, `erp.write`

Secret 은 V0018 Flyway 시드에 BCrypt(strength=10) 해시로 저장.
dev 평문 secret 은 `erp-dev` ([`.env.example`](../../.env.example) 의 `OIDC_INTERNAL_CLIENT_SECRET` 참고).
production 은 `OIDC_INTERNAL_CLIENT_SECRET` 환경 변수로 교체 후 admin API 로 갱신.

V0018 SQL 위치 — [`projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0018__seed_erp_oidc_client.sql`](../../../global-account-platform/apps/auth-service/src/main/resources/db/migration/V0018__seed_erp_oidc_client.sql).

> **Edge Case — `sub` claim of client_credentials tokens**: `erp-platform-internal-services-client` 의
> 토큰은 `sub == client_id` 이며 `email` / `roles` claim 이 없다. gateway 의 헤더
> enrichment 필터가 `X-Account-Id` 헤더를 `sub` 값으로 전달하므로 — 다운스트림
> service 에서 `X-Account-Id` 가 client_id 일 수 있다. 사람 / 머신 사용자 구분이
> 필요한 경우 `X-Token-Type` (`user` | `client_credentials`) 헤더로 분기.

---

## Scopes

`<tenant>.<resource>.<action>` 명명 규칙. V0018 시점에는 광범위한 read/write 두 scope 만
등록 — 후속 service 별 세분화 (`erp.masterdata.write` 등) 는 TASK-ERP-BE-001 시점에 결정.

| Scope | 설명 |
|---|---|
| `erp.read` | erp-platform 의 모든 read 리소스 (마스터 조회, 결재 조회, 통합 조회 등) |
| `erp.write` | erp-platform 의 mutation (마스터 생성/수정/폐기, 결재 상신/승인 등) |

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`) 는 콘솔 user-flow 도입 시점에 적용.

---

## Token 검증 규칙 (각 erp-platform 서비스의 Resource Server 가 적용)

1. **서명 검증** — GAP 의 JWKS 로 RS256 서명 검증.
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (`JwtTimestampValidator`).
3. **Issuer 검증** — SAS issuer + legacy `global-account-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Tenant 검증** — `tenant_id` claim 이 `erp` 또는 `*` (SUPER_ADMIN platform-scope) 인 경우만 통과. 그 외 (`wms`, `ecommerce`, `fan-platform`, `scm`, `finance`) → `tenant_mismatch` → 403 `TENANT_FORBIDDEN`.
5. **Scope 검증** — 다운스트림 service 의 `SecurityConfig` 가 `X-Scopes` 헤더 또는 SecurityContext 의 `Jwt.getClaimAsString("scope")` 로 enforce.
6. **internal-only 경계** — 외부(비-내부망·비-SSO) 트래픽은 게이트웨이/네트워크 정책에서 거부 (도메인 룰 E7).

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `tenant_id != erp` (cross-tenant, 그리고 `*` 가 아님) | 403 | `TENANT_FORBIDDEN` |
| 유효 토큰이지만 scope/role 부족 | 403 | `FORBIDDEN` |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 을 따른다.

---

## dev smoke test

dev 토큰 발급:
```bash
curl -u erp-platform-internal-services-client:erp-dev \
     -d "grant_type=client_credentials&scope=erp.read" \
     http://gap.local/oauth2/token
```

응답 JWT decode 시 검증해야 할 claim:
- `iss` = `http://gap.local`
- `aud` = `erp-platform-internal-services-client`
- `tenant_id` = `erp`
- `scope` = `erp.read`
- `sub` = `erp-platform-internal-services-client`

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정.
- [ ] 콘솔 user-flow 도입 시점에 `erp-platform-user-flow-client` 의 V0NN 시드 추가 + redirect URI 갱신.
- [ ] `erp-platform-internal-services-client` 의 client_secret 을 secret manager 로 회전 (production).
- [ ] D2-b deprecation 윈도우 종료 시 allowed-issuers 에서 `global-account-platform` 제거.
- [ ] GAP 의 `erp` 테넌트 (V0018 account-service 시드) 는 TASK-MONO-119 에서 등록됨.

---

## 참조

- [GAP ADR-001](../../../global-account-platform/docs/adr/ADR-001-oidc-adoption.md) — GAP IdP 승급
- [GAP auth-api.md § OAuth2 Clients](../../../global-account-platform/specs/contracts/http/auth-api.md)
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- [scm-platform 의 동일 통합](../../../scm-platform/specs/integration/gap-integration.md) — reference pattern
- [finance-platform 의 동일 통합](../../../finance-platform/specs/integration/gap-integration.md) — reference pattern
- TASK-MONO-119 — GAP V0018 erp-platform OIDC/tenant 시드 (V0018 auth: `erp-platform-internal-services-client`, V0018 account: `erp` tenant)
- TASK-ERP-BE-001 — 본 통합의 첫 구현 태스크 (masterdata-service bootstrap)
