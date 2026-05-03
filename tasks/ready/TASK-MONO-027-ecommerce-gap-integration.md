# Task ID

TASK-MONO-027

# Title

ecommerce-platform GAP OIDC integration — V0012 seed + gateway issuer/validators + compose env cutover

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- cross-project

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

ecommerce-microservices-platform 의 `gateway-service` 가 GAP (global-account-platform) 가 발행한 OIDC token 을 받기 시작한다. TASK-BE-131 (PR 머지됨, `tasks/done/TASK-BE-131-gateway-jwks-migration.md`) 로 ecommerce gateway 는 이미 RS256 / JWKS 검증 + `aud: ecommerce` + `account_type / roles` 클레임 강제까지 마쳐 있으나, `JWT_JWKS_URI` 환경변수가 임시값 `http://auth-service:8081/auth/.well-known/jwks.json` 을 가리켜 여전히 자체 ecommerce auth-service 가 발행한 토큰만 검증한다. 본 task 는 그 cutover 를 마무리해 ecommerce 가 wms / fan-platform 과 동일하게 GAP 표준 OIDC consumer 로 동작하게 한다.

cutover 완료 후:

- GAP DB 의 `oauth_clients` 에 ecommerce 용 OAuth client (web-store / admin-dashboard) + ecommerce 용 scope 가 V0012 시드로 등록.
- ecommerce gateway 의 `application.yml` 이 wms 패턴 (`issuer-uri` + `jwk-set-uri` + `audiences` + tenant claim validator + allowed-issuers validator) 을 따른다.
- ecommerce `docker-compose.yml` / `.env.example` 의 `JWT_JWKS_URI` 가 GAP gateway 의 `/oauth2/jwks` 를 가리키고 `OIDC_ISSUER_URL` 변수가 추가됨.
- ecommerce gateway 통합 테스트가 GAP 가 발행한 토큰 (WireMock JWKS + RS256 RSA key) 으로 통과.
- 기존 ecommerce auth-service 컨테이너는 본 task 에서 **남겨둔다** — 폐기는 후속 TASK-BE-132 가 담당. 본 task 가 머지된 시점부터 ecommerce auth-service 는 deprecated 상태이며 신규 토큰 검증 경로에서 사용되지 않는다.
- frontend (web-store / admin-dashboard) 의 NextAuth / OAuth redirect 경로 변경은 후속 TASK-FE-067 가 담당 (V0012 시드는 redirect URI 만 미리 등록).

본 task 는 cross-project (root tasks/) 로 분류한다 — `projects/global-account-platform/` (V0012 마이그레이션) 과 `projects/ecommerce-microservices-platform/` (gateway + compose + spec) 두 프로젝트가 같은 PR 안에서 atomic 하게 변경된다. TASK-MONO-019 (wms) / TASK-MONO-026 (fan-platform) 의 cross-project 패턴과 일관.

---

# Scope

## In Scope

### 1. GAP V0012 Flyway 시드 추가 — ecommerce OAuth clients + scopes

신규 파일: `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0012__seed_ecommerce_oidc_clients.sql`

다음 client 2건 + scope 들을 INSERT:

| Client ID | tenant_id | tenant_type | grant types | PKCE | redirect URIs | scopes |
|---|---|---|---|---|---|---|
| `ecommerce-web-store-client` | `ecommerce` | `B2C` | `authorization_code`, `refresh_token` | required (`require_proof_key=true`) | `http://localhost:3000/api/auth/callback/gap`, `http://web.ecommerce.local/api/auth/callback/gap` | `openid`, `profile`, `email`, `tenant.read`, `ecommerce.consumer` |
| `ecommerce-admin-dashboard-client` | `ecommerce` | `B2C` | `authorization_code`, `refresh_token` | required | `http://localhost:3001/api/auth/callback/gap`, `http://admin.ecommerce.local/api/auth/callback/gap` | `openid`, `profile`, `email`, `tenant.read`, `ecommerce.operator` |

(`tenant_type` 은 `B2C` 가 단일 — ecommerce 는 self-service consumer 가입 (web-store) + operator admin (admin-dashboard) 가 같은 테넌트 안에 공존하는 모델. operator 는 GAP `account_type=OPERATOR` 클레임으로 구분되며, 별도 테넌트 분리는 ADR-001 모델과 어긋남.)

Token TTLs (fan-platform V0011 패턴과 동일):
- `access_token_time_to_live`: PT15M (900 s)
- `refresh_token_time_to_live`: PT24H (86400 s)
- `authorization_code_time_to_live`: PT5M (300 s)

`client_authentication_methods`: `client_secret_basic` + PKCE 필수 (confidential client + PKCE 동시 사용 — fan-platform V0011 패턴).

Scopes (ecommerce 도메인용, V0008 시스템 scope 와 별도):

| scope | tenant_id | 설명 |
|---|---|---|
| `ecommerce.consumer` | `ecommerce` | web-store 사용자 권한 (`account_type=CONSUMER` 필수) |
| `ecommerce.operator` | `ecommerce` | admin-dashboard operator 권한 (`account_type=OPERATOR` 필수) |

system scope (`openid`, `profile`, `email`, `offline_access`, `tenant.read`) 는 V0008 / V0011 에서 이미 등록 — 시드 안 함.

BCrypt secret hash: dev plain text 는 `ecommerce-dev` (fan-platform V0011 의 `fan-platform-dev` 동일 hash 패턴, BCryptPasswordEncoder strength=10). production 은 `ECOMMERCE_WEB_STORE_CLIENT_SECRET` / `ECOMMERCE_ADMIN_DASHBOARD_CLIENT_SECRET` 환경 변수로 교체 후 admin API 로 갱신.

### 2. ecommerce/specs/integration/gap-integration.md 신설

wms / fan-platform 의 gap-integration.md 와 동일 구조로 작성:

- Tenant Identity (`tenant_id=ecommerce`, `B2C` 단일 — operator 는 `account_type` 로 구분)
- OIDC Endpoints + 환경 변수 표 (`OIDC_ISSUER_URL` / `JWT_JWKS_URI` / discovery / token / authorize)
- Spring Boot 설정 키 스니펫
- OAuth Clients 표 (V0012)
- Scopes 표
- Token 검증 규칙 5단계 (서명 / 표준 클레임 / Issuer / Tenant / Role)
- Error Responses 표
- 운영 체크리스트
- 참조 (ADR-001, consumer-integration-guide, wms gap-integration, V0012, TASK-MONO-027)

### 3. ecommerce gateway-service `application.yml` 갱신

현재 (PR #131 머지 상태):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWKS_URI:http://localhost:8088/.well-known/jwks.json}
          audiences: ecommerce
```

목표 (wms 패턴):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URL:http://localhost:8081}
          jwk-set-uri: ${OIDC_JWK_SET_URI:${JWT_JWKS_URI:${OIDC_ISSUER_URL:http://localhost:8088}/oauth2/jwks}}
          audiences: ecommerce

ecommerce:
  oauth2:
    allowed-issuers: ${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://localhost:8081},global-account-platform}
    required-tenant-id: ${OIDC_REQUIRED_TENANT_ID:ecommerce}
```

### 4. ecommerce gateway-service 코드 — TenantClaimValidator + AllowedIssuersValidator 추가

wms gateway-service (`projects/wms-platform/apps/gateway-service/src/main/java/com/wms/gateway/security/`) 의 `TenantClaimValidator.java` + `AllowedIssuersValidator.java` 를 ecommerce 패키지로 그대로 복사 — 로직 동일, 패키지명 / config 키 prefix 만 변경 (`wms.oauth2` → `ecommerce.oauth2`). 단위 테스트 (`*ValidatorTest.java`) 도 같이 복사.

`OAuth2ResourceServerConfig.java` (없으면 신설) 가 `JwtDecoder` bean 에 두 validator 를 `DelegatingOAuth2TokenValidator` 로 등록. TASK-BE-131 의 `SecurityConfig` 에서 사용하는 `oauth2ResourceServer().jwt()` chain 이 본 decoder 를 통과하도록 wiring.

기존 `AccountTypeEnforcementFilter` / `JwtHeaderEnrichmentFilter` (TASK-BE-131 done) 는 **변경 없음** — 본 task 는 issuer / tenant claim 검증을 추가하는 것이지 account_type / role 강제는 그대로.

### 5. ecommerce `docker-compose.yml` 갱신

`gateway-service` environment:
- `JWT_JWKS_URI` 제거 (또는 deprecated 주석 + GAP gateway 의 `/oauth2/jwks` 로 redirect)
- 신규 `OIDC_ISSUER_URL=http://gap-gateway:8080` (docker network 내부 GAP gateway DNS 이름) 추가
  - 또는 `http://gap.local` (Traefik hostname) — wms compose 패턴 확인 후 결정 (둘 중 하나)
- `OIDC_REQUIRED_TENANT_ID=ecommerce`
- `OIDC_ALLOWED_ISSUERS=${OIDC_ISSUER_URL},global-account-platform`

`auth-service` 의존성: 본 task 에서는 **유지** (TASK-BE-132 가 폐기 담당). 단 `gateway-service` 의 `depends_on` 에서 `auth-service` 는 제거 가능 — gateway 가 GAP JWKS 만 사용하므로.

### 6. ecommerce `.env.example` 갱신

신규 환경변수 추가:
- `OIDC_ISSUER_URL=http://gap.local` (또는 docker network 내부 URL)
- `OIDC_REQUIRED_TENANT_ID=ecommerce`
- `OIDC_ALLOWED_ISSUERS=http://gap.local,global-account-platform`
- `ECOMMERCE_WEB_STORE_CLIENT_SECRET=ecommerce-dev` (V0012 plain text)
- `ECOMMERCE_ADMIN_DASHBOARD_CLIENT_SECRET=ecommerce-dev` (V0012 plain text)

기존 `JWT_SECRET` 은 **유지** — auth-service 가 아직 살아있어 자체 검증에 필요. TASK-BE-132 가 제거 담당.

### 7. ecommerce gateway 통합 테스트 갱신

기존 `JwtAuthenticationFilterTest` 또는 `GatewaySecurityIntegrationTest` 가 `JWT_JWKS_URI` mock 을 사용했다면, GAP issuer + tenant claim 시나리오를 추가:

- `tenant_id=ecommerce` 토큰 → 200 통과
- `tenant_id=wms` 토큰 → 403 `TENANT_FORBIDDEN`
- `tenant_id` claim 누락 토큰 → 403
- `iss=global-account-platform` (legacy) 토큰 → 200 (allowed-issuers fallback)
- `iss=https://attacker.example.com` 토큰 → 401

WireMock JWKS pattern 은 wms `JwtTestHelper` (TASK-MONO-019 산출물) 를 reference 로.

### 8. GAP `account-service` 에 `ecommerce` tenant 시드 (선택)

GAP V0010 / V0011 패턴을 보면 wms / fan-platform 테넌트는 admin API 또는 별도 시드로 등록되어 있다고 가정. ecommerce 도 동일하게 등록 필요 — V0012 시드의 `tenant_id` foreign key 검증을 통과하기 위해. V0012 안에 `INSERT INTO tenants(tenant_id, tenant_type, ...)` 한 줄 추가하거나, 별도 V0012a 마이그레이션으로 분리. 구현자 판단.

## Out of Scope

- ecommerce auth-service 컴포넌트 (settings.gradle / docker-compose / k8s) **제거** — 후속 TASK-BE-132
- web-store / admin-dashboard 의 NextAuth provider 를 GAP authorize endpoint 로 변경 — 후속 TASK-FE-067
- ecommerce 사용자 데이터 (auth-service DB) 를 GAP DB 로 마이그레이션 — portfolio 범위 밖. production 마이그레이션은 별도 데이터 엔지니어링 task. v1 portfolio 시연은 빈 GAP DB + 새로 가입한 사용자만 인증.
- ecommerce 하위 도메인 서비스 (product-service / order-service / payment-service 등) 의 service-level `TenantClaimValidator` 추가 — gateway 만 검증해도 충분. service-level enforcement 는 v2 (defense-in-depth) 후속.
- production secret rotation 자동화 — dev 평문 시드만.

---

# Acceptance Criteria

- [ ] `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0012__seed_ecommerce_oidc_clients.sql` 신설 + auth-service `:check` 통과 (Flyway migration 검증).
- [ ] V0012 적용 후 GAP DB `oauth_clients` 에 `ecommerce-web-store-client` / `ecommerce-admin-dashboard-client` 2 row, `oauth_scopes` 에 `ecommerce.consumer` / `ecommerce.operator` 2 row, `tenants` 에 `ecommerce` row (없으면 추가) 존재.
- [ ] ecommerce `gateway-service` `application.yml` 이 wms 패턴 (`issuer-uri` + `jwk-set-uri` + `audiences` + `ecommerce.oauth2.allowed-issuers` + `ecommerce.oauth2.required-tenant-id`) 따름.
- [ ] ecommerce gateway-service 에 `TenantClaimValidator.java` + `AllowedIssuersValidator.java` + 단위 테스트 추가.
- [ ] gateway integration test: `tenant_id=ecommerce` 통과, `tenant_id=wms` 거부 (403), 미설정 거부, legacy `iss=global-account-platform` 통과.
- [ ] `docker-compose.yml` 의 `gateway-service` environment 에 `OIDC_ISSUER_URL` / `OIDC_REQUIRED_TENANT_ID` / `OIDC_ALLOWED_ISSUERS` 추가, `JWT_JWKS_URI` 제거 또는 deprecated.
- [ ] `.env.example` 갱신 — `OIDC_*` + `ECOMMERCE_*_CLIENT_SECRET` placeholder.
- [ ] `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md` 신설 (wms / fan-platform 패턴 동일 구조).
- [ ] `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:check` PASS.
- [ ] (옵션 — 매뉴얼) 로컬 e2e: `pnpm gap:up && pnpm ecommerce:up` → GAP 의 `/oauth2/token` 으로 client_credentials 발급 (`ecommerce-admin-dashboard-client` 사용) → 토큰 으로 ecommerce gateway `/api/products` 호출 → 200.

---

# Related Specs

- `tasks/done/TASK-MONO-019-wms-platform-oidc-resource-server-migration.md` — wms cross-project cutover reference
- `tasks/done/TASK-MONO-026-gap-v0011-fan-platform-oidc-clients.md` — fan-platform V0011 시드 reference
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-131-gateway-jwks-migration.md` — ecommerce gateway 가 이미 RS256/JWKS + audiences=ecommerce 검증까지 마침
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` — 신규 OIDC 소비자 가이드 (Phase 1~6)
- `projects/global-account-platform/specs/contracts/http/auth-api.md` — OIDC 표준 endpoints
- `projects/wms-platform/specs/integration/gap-integration.md` — wms 적용본 (가장 가까운 reference)
- `projects/fan-platform/specs/integration/gap-integration.md` — fan-platform 적용본 (V0011 패턴)
- `platform/contracts/jwt-standard-claims.md` — JWT 클레임 표준
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md` — D1=A 결정

# Related Skills

- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/backend/oauth2-resource-server/SKILL.md` (있으면)

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0012__seed_ecommerce_oidc_clients.sql` (신규)
- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md` (신규)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/security/TenantClaimValidator.java` (신규)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/security/AllowedIssuersValidator.java` (신규)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/config/OAuth2ResourceServerConfig.java` (신규 또는 갱신)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/resources/application.yml` (갱신)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/test/java/com/example/gateway/security/TenantClaimValidatorTest.java` (신규)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/test/java/com/example/gateway/security/AllowedIssuersValidatorTest.java` (신규)
- `projects/ecommerce-microservices-platform/docker-compose.yml` (갱신)
- `projects/ecommerce-microservices-platform/.env.example` (갱신)

(패키지명은 ecommerce gateway 의 기존 컨벤션 확인 후 조정 — 위 경로는 추정)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. ecommerce gateway 의 `Layered Architecture` 변경 없음 — security / config 레이어에 validator + decoder bean 추가.

GAP V0012 시드는 SQL 추가만 — auth-service architecture 변경 없음.

---

# Implementation Notes

- V0010 (wms) / V0011 (fan-platform) 의 INSERT 스니펫을 그대로 base 로 ecommerce 변경 (client_id, tenant_id, redirect URIs, scopes 만 교체).
- V0012 의 `tenants` row INSERT 는 ON CONFLICT DO NOTHING idempotent 로 작성.
- BCrypt hash: fan-platform V0011 의 `$2a$10$8K1p/a0dR1xqM8LjelOS.OEI7NJJkMvNKbFbMaWkVWzBJUY9qQ4hO` 가 `fan-platform-dev` 의 hash. ecommerce 는 동일 strength=10 hash 를 새로 생성 (`ecommerce-dev` 평문 → BCrypt 출력).
- wms 의 `TenantClaimValidator` / `AllowedIssuersValidator` 는 `com.wms.gateway.security` 패키지에 있음. ecommerce 는 `com.example.gateway.security` 또는 ecommerce gateway 가 사용하는 정확한 base package 확인 후 배치.
- ecommerce gateway 가 reactive (WebFlux) 임 — TASK-BE-131 architecture.md 명시. wms gateway 도 reactive — 두 validator 는 reactive 에서 동작 검증된 코드.
- `ecommerce.oauth2.required-tenant-id` 는 단일 tenant 만 — fan-platform 처럼 `tenant_id=*` (SUPER_ADMIN platform-scope) 도 통과시키려면 `TenantClaimValidator` 의 wildcard fallback 옵션 추가 검토 (현재 wms 는 wildcard 미지원, fan-platform 만 wildcard 지원).
- GAP gateway 의 `/oauth2/jwks` endpoint 가 docker network 내부에서 도달 가능한지 확인 — `gap-gateway:8080` 컨테이너명이 `traefik-net` 또는 ecommerce 의 docker network 에 join 되어 있어야. 둘 다 `traefik-net` external network 에 join 한 상태이므로 가능 (TASK-MONO-024 결과).

---

# Edge Cases

- **GAP DB가 V0010 + V0011 미실행 상태**: V0010 / V0011 모두 main 머지됨 (TASK-MONO-019 / 026 done). 본 task 는 V0012 — 안전.
- **redirect URI mismatch**: web-store / admin-dashboard 가 dev 시 `localhost:3000` / `localhost:3001` 사용. Traefik routing 은 `web.ecommerce.local` / `admin.ecommerce.local`. 두 set 모두 V0012 redirect URIs 에 등록.
- **`client_secret` plain text 노출**: dev 환경 한정. production 은 `ECOMMERCE_*_CLIENT_SECRET` env var 로 override 후 admin API 로 secret rotate.
- **legacy issuer fallback**: `OIDC_ALLOWED_ISSUERS` 가 D2-b deprecation 윈도우 (2026-08-01 종료) 동안 SAS issuer + `global-account-platform` 양쪽 허용. 윈도우 종료 시 `global-account-platform` 제거.
- **operator 가 web-store 를 통해 로그인**: GAP 토큰의 `account_type=OPERATOR` 면 ecommerce gateway 의 `AccountTypeEnforcementFilter` (TASK-BE-131) 가 `/api/admin/**` 만 통과시킨다. web-store 의 일반 경로 (`/api/products` 등) 는 403. 사용자가 admin-dashboard 로 가야 함. 이는 operator 가 GAP 에서 OPERATOR 권한으로 발급받은 토큰의 정상 동작.

---

# Failure Scenarios

- **마이그레이션 충돌**: V0012 충돌 (다른 task 가 동시에 V0012 사용) — 발행 시점에 V 번호 재확인. 현재 main 의 마지막 GAP migration 은 V0011 (PR #135) 이므로 V0012 가용.
- **GAP gateway 미가용 시 ecommerce gateway 콜드 스타트**: Spring Security `NimbusJwtDecoder` 의 issuer-uri 자동 discovery 가 콜드 스타트 시 GAP `/.well-known/openid-configuration` fetch — GAP 미가용이면 ecommerce gateway 도 시작 실패. mitigations: (a) `lazy issuer fetch`, (b) compose `depends_on` 으로 GAP gateway 우선 기동, (c) JWKS 캐시 만료 후 fetch 실패 시 503 (consumer-integration-guide § 운영 체크리스트).
- **tenant `ecommerce` 미등록**: V0012 의 client INSERT 가 `tenants` foreign key 위반으로 실패. → V0012 안에서 `tenants` INSERT 먼저 (or `ON CONFLICT DO NOTHING`).
- **V0012 secret hash 잘못 생성**: fan-platform V0011 의 hash 와 동일한 BCrypt(strength=10) 출력 형식이어야 SAS 가 인식. mitigations: 로컬 BCryptPasswordEncoder 로 `"ecommerce-dev"` hash 생성 후 sql 안에 명시 (해시 값을 주석으로 검증 한 줄 포함).

---

# Test Requirements

- 단위:
  - `TenantClaimValidatorTest` — `tenant_id=ecommerce` 통과, `tenant_id=wms` 거부, claim 누락 거부.
  - `AllowedIssuersValidatorTest` — SAS issuer 통과, legacy `global-account-platform` 통과, 다른 issuer 거부.
- 슬라이스 / 통합:
  - ecommerce gateway 의 기존 보안 통합 테스트가 GAP 발행 토큰 시나리오 추가 (위 acceptance 5번).
- e2e:
  - 옵션 — `pnpm gap:up + pnpm ecommerce:up` 로 양 프로젝트 기동 + GAP `/oauth2/token` (client_credentials) → ecommerce gateway 호출 → 200.

---

# Definition of Done

- [ ] V0012 Flyway 마이그레이션 SQL 작성 + auth-service `:check` 통과.
- [ ] ecommerce gateway code + spec + compose + .env 변경 완료.
- [ ] ecommerce gateway-service `:check` PASS (단위 + 슬라이스 + 통합 통합 테스트).
- [ ] gap-integration.md 신설 + 본 task 가 reference.
- [ ] cross-project atomic PR 형태로 발행 (commit prefix `feat!: TASK-MONO-027`).
- [ ] (옵션) 매뉴얼 e2e 로 GAP 발행 토큰 → ecommerce gateway 통과 시연.
- [ ] Ready for review.
