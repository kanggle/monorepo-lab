# Integration — iam-platform (IAM) OIDC

> 본 문서는 `scm-platform` 의 모든 서비스가 IAM 를 표준 OIDC IdP 로 사용하는 방식을 1쪽으로 요약한다.
> [IAM ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) 의 scm-platform 적용본이며,
> [wms-platform](../../../wms-platform/specs/integration/iam-integration.md) /
> [fan-platform](../../../fan-platform/specs/integration/iam-integration.md) 의 같은 통합 패턴을 따른다.

---

## Tenant Identity

- `tenant_id` = `scm`
- `tenant_type` = `B2B_ENTERPRISE` (enterprise buyer / supplier 포털 모델)
- v1 = backend only — self-service signup endpoint 사용 안 함. 모든 운영자 / 시스템 계정은
  관리자 API ([account internal provisioning](../../../iam-platform/specs/contracts/http/internal/account-internal-provisioning.md))
  로 생성한다.

---

## OIDC Endpoints (consumed by scm-platform)

| 항목 | 값 (dev 기본) | 환경 변수 |
|---|---|---|
| Issuer URL | `http://iam.local` | `OIDC_ISSUER_URL` |
| JWKS URI | `${OIDC_ISSUER_URL}/oauth2/jwks` | `JWT_JWKS_URI` |
| OIDC Discovery | `${OIDC_ISSUER_URL}/.well-known/openid-configuration` | n/a |
| Token endpoint | `${OIDC_ISSUER_URL}/oauth2/token` | n/a |
| Authorization endpoint | `${OIDC_ISSUER_URL}/oauth2/authorize` (v2 user-flow 도입 시) | n/a |

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

`scmplatform.oauth2.allowed-issuers` 는 D2-b deprecation 윈도우 동안 SAS issuer 와 legacy
`iam-platform` issuer 양쪽을 허용한다.

> **Edge Case E2 — JWKS endpoint 정렬**: V0013 시드 SQL 은 IAM 의 표준
> `/oauth2/jwks` 엔드포인트를 사용한다 (`/.well-known/jwks.json` 아님). scm-platform
> gateway 의 `application.yml` default 도 이 endpoint 와 정렬되어 있다 — TASK-MONO-035
> wms / fan-platform JWKS URI 정렬과 동일.

---

## OAuth Clients (등록은 IAM 의 시드 마이그레이션에서 생성)

V0013 시드 ([TASK-MONO-042](../../../../tasks/done/TASK-MONO-042-gap-v0013-scm-oidc-clients.md)):

| Client ID | Grant Types | PKCE | Redirect URIs | Flyway |
|---|---|---|---|---|
| `scm-platform-internal-services-client` | `client_credentials` | No | — | V0013 (TASK-MONO-042) |
| `scm-platform-user-flow-client` | `authorization_code` + `refresh_token` | 필수 | (TBD) | v2 DEFERRED |

`scm-platform-internal-services-client` 의 등록 메타:
- `client_authentication_methods`: `client_secret_basic`
- `access_token_time_to_live`: PT15M (900 s)
- `aud`: `scm-platform-internal-services-client`
- 부여 가능 scope: `scm.read`, `scm.write`

Secret 은 V0013 Flyway 시드에 BCrypt(strength=10) 해시로 저장.
dev 평문 secret 은 `scm-dev` ([`.env.example`](../../.env.example) 의 `OIDC_INTERNAL_CLIENT_SECRET` 참고).
production 은 `OIDC_INTERNAL_CLIENT_SECRET` 환경 변수로 교체 후 admin API 로 갱신.

V0013 SQL 위치 — [`projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0013__seed_scm_oidc_clients.sql`](../../../iam-platform/apps/auth-service/src/main/resources/db/migration/V0013__seed_scm_oidc_clients.sql).

> **Edge Case E1 — `sub` claim of client_credentials tokens**: `scm-platform-internal-services-client` 의
> 토큰은 `sub == client_id` (즉 `scm-platform-internal-services-client`) 이며 `email` /
> `roles` claim 이 없다. gateway 의 `JwtHeaderEnrichmentFilter` 가 `X-Account-Id` 헤더를
> `sub` 값으로 전달하므로 — 다운스트림 service 에서 `X-Account-Id` 가 client_id 일 수 있다.
> 사람 / 머신 사용자 구분이 필요한 경우 `X-Token-Type` (`user` | `client_credentials`) 헤더로 분기.

---

## Scopes

`<tenant>.<resource>.<action>` 명명 규칙. V0013 시점에는 광범위한 read/write 두 scope 만
등록 — 후속 service 별 세분화 (`scm.procurement.write` 등) 는 TASK-SCM-BE-002 / -003 시점에 결정.

| Scope | 설명 |
|---|---|
| `scm.read` | scm-platform 의 모든 read 리소스 (PO 조회, inventory snapshot, supplier catalog 등) |
| `scm.write` | scm-platform 의 mutation (PO 생성/확정/취소, ASN 입수, settlement period mutate 등) |

> **Edge Case E3 — scope vs role**: V0013 시드 토큰은 `scope` claim 만 가지고 `role` /
> `roles` claim 은 없다. `JwtHeaderEnrichmentFilter` 는 `role` / `roles` 부재 시 빈
> 문자열을 emit 하고 (downstream 이 명시적 deny), `scope` 값을 `X-Scopes` 헤더로 전달한다.
> 다운스트림 service 의 인가는 `X-Scopes` 기반 scope 검증으로 수행.

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`) 는 v2 user-flow 도입 시점에 적용.

---

## Token 검증 규칙 (각 scm-platform 서비스의 Resource Server 가 적용)

1. **서명 검증** — IAM 의 JWKS 로 RS256 서명 검증.
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (`JwtTimestampValidator`).
3. **Issuer 검증** — `AllowedIssuersValidator` 로 SAS issuer + legacy `iam-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Tenant 검증** — `TenantClaimValidator` 로 `tenant_id` claim 이 `scm` 또는 `*` (SUPER_ADMIN platform-scope) 인 경우만 통과. 그 외 (`wms`, `ecommerce`, `fan-platform`, 향후 `erp`/`mes`) → `tenant_mismatch` → 403 `TENANT_FORBIDDEN`.
5. **Scope 검증** — 다운스트림 service 의 `SecurityConfig` 가 `X-Scopes` 헤더 또는 SecurityContext 의 `Jwt.getClaimAsString("scope")` 로 enforce.

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `tenant_id != scm` (cross-tenant, 그리고 `*` 가 아님) | 403 | `TENANT_FORBIDDEN` |
| 유효 토큰이지만 scope/role 부족 | 403 | `FORBIDDEN` |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 을 따른다.

---

## platform-console Operator Read Consumer (ADR-MONO-013)

> 본 섹션은 [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
> (ACCEPTED, Model B) § D6 Phase 4 의 scm-side 인정 — **새 capability 가 아니라
> 기존 토큰 검증 규칙의 reality-alignment**. 규범적 계약은
> [`gateway-public-routes.md` § platform-console operator read consumer](../contracts/http/gateway-public-routes.md)
> 가 canonical; 본 섹션은 통합 요약이다.

- `platform-console` 은 scm 의 v2-deferred frontend 가 **아니다** — 별도
  ADR-MONO-013 프로젝트(Model B 단일 운영 콘솔). scm 의 read surface(procurement
  PO read + inventory-visibility read)를 **server-side** 로 호출한다.
- **사용 토큰 = IAM 자체 `platform-console-web` OIDC access token** (운영자
  Auth Code+PKCE 로그인, RS256). `scm-platform-internal-services-client`(V0013,
  client_credentials) 도 아니고, deferred `scm-platform-user-flow-client` 도
  아니다 — scm 에 새 OAuth client 를 등록하지 **않는다**.
- 이 토큰은 위 § Token 검증 규칙 1–5 + Edge Case E1/E3 의 **기존** 경로로
  검증된다: RS256/JWKS → issuer allowlist → `tenant_id ∈ { scm, * }`
  (`TenantClaimValidator`) → `JwtHeaderEnrichmentFilter` 가 사람 caller 를
  `X-Token-Type=user` 로 표면화. **gateway 코드/route/auth-model 변경 0**.
- **read-only**: PO write(`submit/confirm/cancel`) · webhook 은 console 소비
  대상 아님. scm 은 single-org 유지(`multi-tenant`/`audit-heavy` 미선언 불변) —
  tenant 격리는 IAM `tenant_id` claim + 기존 producer-side gate 가 권위.
- 소비측 의무(per-domain credential 규칙 등)는 platform-console
  [`console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md)
  § 2.4.6(`TASK-PC-FE-008`) / § 2.4.5 가 canonical. scm 은 consumer-only,
  `procurement-api.md`/`inventory-visibility-api.md` 무변경.

---

## dev smoke test

dev 토큰 발급:
```bash
curl -u scm-platform-internal-services-client:scm-dev \
     -d "grant_type=client_credentials&scope=scm.read" \
     http://iam.local/oauth2/token
```

응답 JWT decode 시 검증해야 할 claim:
- `iss` = `http://iam.local`
- `aud` = `scm-platform-internal-services-client`
- `tenant_id` = `scm`
- `scope` = `scm.read`
- `sub` = `scm-platform-internal-services-client`

발급 토큰을 gateway 에 전달:
```bash
curl -H "Authorization: Bearer ${TOKEN}" \
     http://scm.local/actuator/info
```
→ 200 OK + JSON body (route 가 actuator 라 인증만 통과하면 충분).

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정.
- [ ] v2 user-flow 도입 시점에 `scm-platform-user-flow-client` 의 V0NN 시드 추가 + redirect URI 갱신.
- [ ] `scm-platform-internal-services-client` 의 client_secret 을 secret manager 로 회전 (production).
- [ ] D2-b deprecation 윈도우 종료 시 `scmplatform.oauth2.allowed-issuers` 에서 `iam-platform` 제거.
- [ ] IAM 의 `scm` 테넌트 (V0015 account-service 시드) 는 TASK-MONO-042 에서 이미 등록됨.

---

## 참조

- [IAM ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — IAM IdP 승급
- [IAM consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) — 가이드 본문
- [IAM auth-api.md § OAuth2 / OIDC Endpoints](../../../iam-platform/specs/contracts/http/auth-api.md#oauth2--oidc-endpoints-standard-adr-001)
- [IAM multi-tenancy.md](../../../iam-platform/specs/features/multi-tenancy.md)
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- [wms-platform 의 동일 통합](../../../wms-platform/specs/integration/iam-integration.md) — reference pattern
- [fan-platform 의 동일 통합](../../../fan-platform/specs/integration/iam-integration.md) — reference pattern
- TASK-MONO-042 — IAM V0013/V0015 scm-platform OIDC 시드 (V0013 SQL: `scm-platform-internal-services-client`, V0015 SQL: `scm` tenant)
- TASK-SCM-BE-001 — 본 통합의 첫 구현 태스크 (gateway-service bootstrap)
- [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — platform-console (Model B) § D6 Phase 4: scm read consumer 거버넌스 (본 통합의 console consumer 인정 권위)
- [platform-console `console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md) § 2.4.5/§ 2.4.6 — 소비측 per-domain credential 계약 (consumer obligation canonical)
- TASK-SCM-BE-015 — 본 console read-consumer reconciliation 태스크
