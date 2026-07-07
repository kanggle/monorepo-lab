# Integration — iam-platform (IAM) OIDC

> 본 문서는 `ecommerce-microservices-platform` 의 `gateway-service` 가 IAM 를 표준 OIDC IdP 로 사용하는 방식을 1쪽으로 요약한다.
> ADR-001 (D1=A) 와 IAM 의 [consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) 의 ecommerce 적용본이며,
> wms-platform / fan-platform 의 [동일 통합](../../../wms-platform/specs/integration/iam-integration.md) 과 같은 패턴을 따른다.
> TASK-MONO-027 에서 본 통합이 구체화된다 (V0012 시드 + gateway issuer/validators + compose env cutover).

---

## Tenant Identity

- `tenant_id` = `ecommerce` (레거시 단일 스토어 슬러그 = default-tenant). **ADR-MONO-030 Step 2 이후**: gateway 는 **entitlement-trust** 로 진화 — JWKS 검증 토큰의 **임의 well-formed `tenant_id`** 를 수용하고 row 로 격리한다 ([multi-tenancy-and-marketplace.md](../features/multi-tenancy-and-marketplace.md) §2.4). entitlement 결정은 IAM 발행 시점, ecommerce 는 row 격리만 집행 (두 권위, 무중첩).
- `tenant_type` = `B2C` (self-service consumer 가입 + admin-provisioned operator 공존)
- 일반 consumer 계정 (web-store) 은 IAM 의 self-service signup endpoint 로 생성된다.
- operator 계정 (platform-console) 은 IAM 의 [account internal provisioning API](../../../iam-platform/specs/contracts/http/internal/account-internal-provisioning.md) 또는 admin operator API 로 생성된다.
- `roles` 클레임으로 web-store / platform-console 권한을 분리한다 — 소비자 토큰은 `CUSTOMER`, 운영자(assume-tenant 파생) 토큰은 도메인 운영자 롤 `ECOMMERCE_OPERATOR` 을 보유한다. gateway `AccountTypeEnforcementFilter` 가 `/api/admin/**` 경로에 `roles ∋ ECOMMERCE_OPERATOR` 강제 (TASK-BE-131; ADR-MONO-035 4b 로 roles-only 전환 — 레거시 `account_type` 클레임 제거됨).

---

## OIDC Endpoints (consumed by ecommerce)

| 항목 | 값 (dev 기본) | 환경 변수 |
|---|---|---|
| Issuer URL | `http://iam.local` | `OIDC_ISSUER_URL` |
| JWKS URI | `${OIDC_ISSUER_URL}/oauth2/jwks` | `JWT_JWKS_URI` (legacy 호환) |
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
          jwk-set-uri: ${OIDC_JWK_SET_URI:${JWT_JWKS_URI:${OIDC_ISSUER_URL}/oauth2/jwks}}
          audiences: ecommerce
```

`ecommerce.oauth2.allowed-issuers` 는 D2-b deprecation 윈도우 (~2026-08-01) 동안 SAS issuer 와 legacy `iam-platform` issuer 양쪽을 허용한다 (TASK-BE-253 패턴).

---

## OAuth Clients (등록은 IAM V0012 마이그레이션에서 시드)

| Client ID | Grant Types | PKCE | Redirect URIs | Flyway |
|---|---|---|---|---|
| `ecommerce-web-store-client` | `authorization_code` + `refresh_token` | 필수 (`require_proof_key=true`) | `http://localhost:3000/api/auth/callback/iam`, `http://web.ecommerce.local/api/auth/callback/iam` | V0012 (TASK-MONO-027) |
| `ecommerce-admin-dashboard-client` (RETIRED — admin-dashboard app removed, TASK-MONO-259; operator UI now in platform-console. Client seed retire migration deferred.) | `authorization_code` + `refresh_token` | 필수 | `http://localhost:3001/api/auth/callback/iam`, `http://admin.ecommerce.local/api/auth/callback/iam` | V0012 (TASK-MONO-027) |
| `ecommerce-internal-services-client` | `client_credentials` | No | — | **ACTIVE (TASK-BE-410)** — internal service-to-service auth. First consumer: batch-worker → order-service `POST /api/internal/orders/confirm-paid-stale` (stale paid-order forward-confirm). Token minted/cached caller-side via `IamClientCredentialsTokenProvider` (mirrors product-service BE-402). Seed: `ecommerce-internal-services-client` row in IAM (Flyway V0012 follow-up / dev `.env` secret `ECOMMERCE_INTERNAL_SERVICES_CLIENT_SECRET`). |

두 user-flow client 는 confidential (secret + PKCE 동시 사용) 로 등록:
- `client_authentication_methods`: `client_secret_basic`
- `require_proof_key`: `true`
- `access_token_time_to_live`: PT15M (900 s)
- `refresh_token_time_to_live`: PT24H (86400 s)

Secret 은 V0012 Flyway 시드에 BCrypt(strength=10) 해시로 저장. dev 평문 secret 은 `ecommerce-dev` (`.env.example` 의 `ECOMMERCE_WEB_STORE_CLIENT_SECRET`). production 은 환경 변수 override 후 admin API 로 갱신.

---

## Scopes

`<tenant>.<resource>.<action>` 또는 `<tenant>.<role>` 명명 규칙. 모두 `tenant_id=ecommerce` scope 으로 IAM 의 `oauth_scopes` 에 등록 (V0012).

| Scope | 설명 |
|---|---|
| `ecommerce.consumer` | web-store 사용자 권한 (IAM 가 `roles ∋ CUSTOMER` 토큰 발급) |
| `ecommerce.operator` | platform-console operator 권한 (IAM assume-tenant 가 `roles ∋ ECOMMERCE_OPERATOR` 토큰 파생) |

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`, `tenant.read`) 는 IAM 의 시스템 scope (V0008 / V0011) 에서 자동 적용.

ecommerce 도메인의 세분화된 resource scope (`ecommerce.product.read`, `ecommerce.order.write` 등) 는 v2 후속 — service-level enforcement 도입 시 함께. v1 은 gateway 에서 `roles` + tenant 검증으로 충분.

---

## Token 검증 규칙 (ecommerce gateway-service 가 적용)

1. **서명 검증** — IAM 의 JWKS 로 RS256 서명 검증 (Spring Security `NimbusJwtDecoder` 자동).
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (`JwtTimestampValidator`).
3. **Issuer 검증** — `AllowedIssuersValidator` 로 SAS issuer + legacy `iam-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Audience 검증** — `audiences: ecommerce` 로 `aud` 클레임 검증 (Spring Security 자동).
5. **Tenant 검증** — `TenantClaimValidator` (entitlement-trust, ADR-MONO-030 §2.4) 로 **임의 well-formed `tenant_id`** 를 수용; **blank/missing 만** `tenant_mismatch` → 403 `TENANT_FORBIDDEN`. (레거시 고정슬러그 `ecommerce` = dual-accept 윈도우의 default-tenant. 도메인간 격리는 다운스트림 row 필터로 집행 — 게이트가 아님.)
6. **Role 강제** — `AccountTypeEnforcementFilter` (TASK-BE-131; ADR-MONO-035 4b-2a 로 roles-only 전환 — `account_type` OR-branch 제거) 가 `/api/admin/**` 경로에 `roles ∋ ECOMMERCE_OPERATOR` 강제, 그 외 인증 필요 경로에 `roles ∋ CUSTOMER` 강제.
   - **operator-on-public 예외 (TASK-BE-380)** — promotion-api.md / shipping-api.md / notification-api.md 는 *운영자(Admin)* 엔드포인트를 **public 경로 트리**(`/api/promotions`, `/api/shippings`, `/api/notifications`)에 두고 서비스단에서 `X-User-Role == ECOMMERCE_OPERATOR` 으로 게이팅한다(`/api/admin/**` 아님). 따라서 게이트웨이는 이 세 read 트리에 한해 `CUSTOMER` 와 `ECOMMERCE_OPERATOR` 을 **둘 다** 수용한다(엔드포인트별 operator/consumer 구분은 서비스가 집행). prefix-only `non-/api/admin → CONSUMER` 규칙이면 운영자가 서비스 도달 전에 403 되는 라이브 갭(platform-console PC-FE-086/088/089 흡수)을 해소. 그 외 public 트리(`/api/products`, `/api/orders`, `/api/search`, `/api/users` 등)는 종전대로 `CUSTOMER` 전용.
   - **`X-User-Role` 다중값 계약 (TASK-BE-393)** — `JwtHeaderEnrichmentFilter` 는 `roles` 클레임 배열을 **콤마 결합** 문자열로 `X-User-Role` 헤더에 주입한다 (예: `ECOMMERCE_OPERATOR,ERP_OPERATOR,SCM_OPERATOR`). 다중 도메인에 등록된 운영자는 여러 롤을 갖는다. **서비스단 operator 게이팅은 반드시 토큰-멤버십 검사**(`X-User-Role` 를 `,` 로 분리·trim 후 `ECOMMERCE_OPERATOR` 과 `equalsIgnoreCase` 비교)를 사용해야 한다 — 단순 문자열 동등 비교(`"ECOMMERCE_OPERATOR".equalsIgnoreCase(header)`)는 다중 도메인 운영자를 모두 403 으로 잠그는 버그다. `contains("ECOMMERCE_OPERATOR")` 형태의 서브스트링 검사도 금지 (`SUPERADMIN` 등 미래 롤의 오수용 위험). 구현 참조: 각 서비스의 `private static boolean hasAdminRole(String userRole)` 헬퍼.
7. **Header Enrichment** — `JwtHeaderEnrichmentFilter` (TASK-BE-131) 가 downstream 으로 `X-User-Id`, `X-User-Email`, `X-User-Role` (`roles` 배열 comma-join), `X-Tenant-Id` (멀티테넌트 컨텍스트 전파, ADR-MONO-030 §2.2 M2 layer 2) 헤더 주입. (`X-Account-Type` 은 ADR-MONO-035 4b 로 주입 중단 — 다운스트림 리더 없음; `IdentityHeaderStripFilter` strip 엔트리는 inert defense-in-depth 로 잔존.) 클라이언트가 위조한 동일 헤더는 `IdentityHeaderStripFilter` 가 먼저 제거.

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `iss` 가 allowed-issuers 미포함 | 401 | `UNAUTHORIZED` |
| `aud` 가 `ecommerce` 아님 | 401 | `UNAUTHORIZED` |
| `tenant_id` blank / missing | 403 | `TENANT_FORBIDDEN` (entitlement-trust: 임의 well-formed `tenant_id` 는 통과) |
| `/api/admin/**` 인데 `roles ∌ ECOMMERCE_OPERATOR` | 403 | `FORBIDDEN` (AccountTypeEnforcementFilter) |
| 일반 경로인데 `roles ∌ CUSTOMER` (operator-on-public 트리 `/api/{promotions,shippings,notifications}` 에서는 `ECOMMERCE_OPERATOR` 도 통과 — TASK-BE-380) | 403 | `FORBIDDEN` (AccountTypeEnforcementFilter) |
| 유효 토큰이지만 도메인 권한 부족 | 403 | downstream 서비스가 결정 |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 따름.

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정 (gateway-facing 외부 URL — 컨테이너 내부 DNS 그대로 사용 시 `iss` mismatch).
- [ ] `ecommerce-web-store-client` 의 redirect URI 를 실제 production 도메인으로 갱신 (admin API 또는 V0012 시드 갱신 마이그레이션). (`ecommerce-admin-dashboard-client` 은 RETIRED — admin-dashboard 앱 제거, TASK-MONO-259; operator UI 는 platform-console.)
- [ ] 두 client_secret 을 secret manager 로 회전.
- [ ] D2-b deprecation 윈도우 종료 시 `ecommerce.oauth2.allowed-issuers` 에서 `iam-platform` 제거.
- [ ] IAM 의 `ecommerce` 테넌트 등록 (V0012 시드 또는 admin-service [Tenant Lifecycle API](../../../iam-platform/specs/contracts/http/admin-api.md)).
- [x] frontend (web-store) 의 NextAuth provider 가 `ecommerce-web-store-client` client_id + IAM authorize endpoint 를 사용하도록 cutover (TASK-FE-067). (운영자 UI 는 platform-console — admin-dashboard 앱 제거, TASK-MONO-259.)
- [x] 자체 ecommerce auth-service 컴포넌트 제거 (TASK-BE-132).

---

## Migration Path (현 위치 → target)

ecommerce 는 standalone 시점에 자체 HS256 auth-service 를 운영했다. 통합은 다음 순서로 진행됐다:

| Stage | Task | 상태 |
|---|---|---|
| 1. ecommerce gateway HS256 → RS256/JWKS 전환 | TASK-BE-131 | ✅ done (PR 머지) — `aud=ecommerce`, `account_type/roles` 클레임 강제, AccountTypeEnforcementFilter, JwtHeaderEnrichmentFilter |
| 2. ecommerce gateway 가 IAM 토큰을 받기 시작 (V0012 시드 + issuer/validators + compose env cutover) | TASK-MONO-027 | ✅ done |
| 3. ecommerce auth-service 컴포넌트 제거 (compose / settings.gradle / k8s / .env) | TASK-BE-132 | ✅ done |
| 4. web-store NextAuth (admin-dashboard RETIRED — TASK-MONO-259) → IAM authorize | TASK-FE-067 | ✅ done |
| 5. ecommerce 사용자 데이터 IAM 마이그레이션 (production) | (별도 데이터 task) | portfolio 범위 밖 |

---

## 참조

- [ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — IAM IdP 승급
- [IAM consumer-integration-guide.md](../../../iam-platform/specs/features/consumer-integration-guide.md) — 가이드 본문
- [IAM auth-api.md § OAuth2 / OIDC Endpoints](../../../iam-platform/specs/contracts/http/auth-api.md)
- [IAM multi-tenancy.md](../../../iam-platform/specs/features/multi-tenancy.md)
- [wms-platform 의 동일 통합](../../../wms-platform/specs/integration/iam-integration.md) — primary reference pattern
- [fan-platform 의 동일 통합](../../../fan-platform/specs/integration/iam-integration.md) — V0011 시드 reference
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- [TASK-BE-131](../../tasks/done/TASK-BE-131-gateway-jwks-migration.md) — ecommerce gateway RS256/JWKS 전환 (선행 완료)
- TASK-MONO-027 — ecommerce IAM 통합 cutover (본 통합의 구현 task)
- TASK-BE-132 — ecommerce auth-service 폐기 (027 후속)
- TASK-FE-067 — frontend NextAuth → IAM authorize cutover (027 후속)
