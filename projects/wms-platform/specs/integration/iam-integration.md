# Integration — iam-platform (IAM) OIDC

> 본 문서는 `wms-platform` 의 모든 서비스가 IAM 를 표준 OIDC IdP 로 사용하는 방식을 1쪽으로 요약한다.
> ADR-001 (D1=A) 와 IAM 의 [consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) 의 wms 적용본이며,
> TASK-MONO-019 에서 이 통합이 구체화되었다.

---

## Tenant Identity

- `tenant_id` = `wms`
- `tenant_type` = `B2B_ENTERPRISE` (admin-provisioned 사용자만 존재. self-service 가입 없음)
- B2B 운영 사용자 (warehouse operator, master admin 등) 의 계정은 IAM 의 [account internal provisioning API](../../../iam-platform/specs/contracts/http/internal/account-internal-provisioning.md) 를 통해 생성된다.

---

## OIDC Endpoints (consumed by wms)

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

`wms.oauth2.allowed-issuers` 는 D2-b deprecation 윈도우 동안 SAS issuer 와 legacy `iam-platform` issuer 양쪽을 허용한다 (TASK-BE-253 패턴).

---

## OAuth Clients (등록은 IAM V0010 마이그레이션에서 시드)

| Client ID | Grant Types | 용도 |
|---|---|---|
| `wms-user-flow-client` | `authorization_code` + `refresh_token` (PKCE 필수) | wms web 콘솔 사용자 로그인 |
| `wms-internal-services-client` | `client_credentials` | 향후 wms 서비스 간 REST 호출 (현재 wms 내부 통신은 Kafka 이벤트 기반) |

Redirect URI placeholder: `http://localhost:9001/callback`. wms web 클라이언트가 도입되면 (TASK-MONO-020 follow-up) 정확한 콜백 URL 로 갱신.

Secret 은 Flyway 시드에 BCrypt 해시로 저장. 평문 secret 은 `WMS_USER_FLOW_CLIENT_SECRET`, `WMS_INTERNAL_SERVICES_CLIENT_SECRET` 환경 변수에서 주입.

---

## Scopes

`<tenant>.<resource>.<action>` 명명 규칙. 모두 `tenant_id=wms` scope 으로 IAM 의 `oauth_scopes` 에 등록 (V0010).

| Scope | 설명 |
|---|---|
| `wms.master.read` / `wms.master.write` | master-service 리소스 (창고, zone, location, SKU, lot) |
| `wms.inventory.read` / `wms.inventory.write` | inventory-service 리소스 (재고, 예약, 이동) |
| `wms.inbound.read` / `wms.inbound.write` | inbound-service 리소스 (ASN, 검수, putaway) |
| `wms.outbound.read` / `wms.outbound.write` | outbound-service 리소스 (오더, picking, packing, shipping) |
| `wms.notification.write` | notification-service 발송 |

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`) 는 IAM 의 시스템 scope (V0008) 에서 자동 적용.

---

## Token 검증 규칙 (각 wms 서비스의 Resource Server 가 적용)

1. **서명 검증** — IAM 의 JWKS 로 RS256 서명 검증.
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (JwtTimestampValidator).
3. **Issuer 검증** — `AllowedIssuersValidator` 로 SAS issuer + legacy `iam-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Tenant 검증** — `TenantClaimValidator` 로 `tenant_id` claim 이 `wms` 인 경우만 통과. 그 외 (`ecommerce`, `fan-platform`, 향후 `erp`/`scm`/`mes`) → `tenant_mismatch` → 403 `TENANT_FORBIDDEN`.
5. **Role / Scope 검증** — 서비스의 `SecurityConfig` 가 `@PreAuthorize` 또는 `requestMatchers().hasRole(...)` 으로 enforce.

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `tenant_id != wms` (cross-tenant) | 403 | `TENANT_FORBIDDEN` |
| 유효 토큰이지만 role/scope 부족 | 403 | `FORBIDDEN` |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 을 따른다.

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정.
- [ ] `wms-user-flow-client` 의 redirect URI 를 실제 wms web 도메인으로 갱신.
- [ ] `wms-internal-services-client` 의 client_secret 을 secret manager 로 회전.
- [ ] D2-b deprecation 윈도우 종료 시 `wms.oauth2.allowed-issuers` 에서 `iam-platform` 제거.
- [ ] IAM 의 `wms` 테넌트 등록 (admin-service [Tenant Lifecycle API](../../../iam-platform/specs/contracts/http/admin-api.md#tenant-lifecycle-task-be-256)).

---

## 참조

- [ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — IAM IdP 승급
- [IAM consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) — 가이드 본문
- [IAM auth-api.md § OAuth2 / OIDC Endpoints](../../../iam-platform/specs/contracts/http/auth-api.md#oauth2--oidc-endpoints-standard-adr-001)
- [IAM multi-tenancy.md](../../../iam-platform/specs/features/multi-tenancy.md)
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- TASK-MONO-019 — wms-platform OIDC Resource Server 전환 (본 통합의 구현 태스크)
