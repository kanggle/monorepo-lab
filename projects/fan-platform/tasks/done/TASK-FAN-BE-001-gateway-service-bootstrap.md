# Task ID

TASK-FAN-BE-001

# Title

fan-platform gateway-service Spring Boot 부트스트랩 (OIDC + Traefik)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- deploy

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

fan-platform 의 첫 service 인 `gateway-service` 를 부트스트랩한다. 이 service 는 fan-platform 의 모든 외부 트래픽 진입점이며, **community / artist / membership 등 모든 백엔드 service 의 reference implementation 패턴** 이 된다 — 후속 service 부트스트랩 태스크가 이 코드를 복제 + 도메인 로직 추가하는 형태로 진행됨.

이 태스크 완료 후:

- `projects/fan-platform/apps/gateway-service/` 에 Spring Boot 3.4 + Spring Cloud Gateway 기반 reverse proxy 가 동작
- GAP IdP (`http://gap.local`) 의 OIDC JWKS 로 RS256 access token 검증
- `tenant_id=fan-platform` claim 만 통과 — 그 외 (`wms`, `ecommerce`, ...) 는 403 `TENANT_FORBIDDEN`
- Redis 기반 rate limit (key 패턴: `rate:<route>:<tenant>:<ip>`)
- Traefik label 통합 — `Host(\`fan-platform.local\`)` 로 라우팅
- 헬스 엔드포인트 `/actuator/health` + `/actuator/info` 노출
- 단위 + 슬라이스 테스트 + integration smoke test (Testcontainers MockWebServer JWKS)
- `docker-compose.yml` (project-level) 에서 gateway-service 가 traefik-net 에 join

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/fan-platform/apps/gateway-service/` 디렉토리 + `build.gradle` (Spring Boot starter, Spring Cloud Gateway, OAuth2 Resource Server, Redis, Lombok, libs:java-common/web/security/observability)
- `settings.gradle` 루트에 `'projects:fan-platform:apps:gateway-service'` include 추가

### 2. Application configuration

- `GatewayServiceApplication.java` (main class, Spring Boot 3.4)
- `application.yml` (default + `local` profile):
  - `spring.cloud.gateway.routes` — community-service / artist-service 라우트 (현재는 placeholder, 후속 service 부트스트랩 시 활성화)
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${OIDC_ISSUER_URL:http://gap.local}`
  - `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${JWT_JWKS_URI:${OIDC_ISSUER_URL}/.well-known/jwks.json}`
  - Redis: `spring.data.redis.host`, `port`, `password`
- `application-test.yml` (테스트 profile, MockWebServer JWKS 가정)

### 3. Security config

- `OAuth2ResourceServerConfig.java`:
  - `JwtDecoder` — JWKS URI 기반, RS256 only
  - `JwtAuthenticationConverter` — `roles` claim 추출 → Spring Security GrantedAuthority
  - `AllowedIssuersValidator` — wms 패턴 따라 SAS issuer + legacy `global-account-platform` issuer 양쪽 허용 (D2-b deprecation 호환)
  - `TenantClaimValidator` — `tenant_id=fan-platform` 만 통과, 그 외 reject (403 `TENANT_FORBIDDEN`)
- `SecurityConfig.java`:
  - `/actuator/health`, `/actuator/info` 인증 면제
  - `/.well-known/health` (Traefik 헬스 체크용 — 옵션) 면제
  - 그 외 모든 경로 인증 필수 + JWT 검증

### 4. Tenant gate filter

- `TenantGateFilter.java` (Gateway global filter):
  - JWT claim `tenant_id` 추출 → `fan-platform` 이 아니면 403
  - 다운스트림으로 `X-Tenant-Id`, `X-Account-Id` (= `sub` claim), `X-Roles` 헤더 전파

### 5. Rate limit

- Redis 기반 rate limit:
  - 글로벌 기본: 60 req / 분 / IP
  - 인증된 요청: 600 req / 분 / account
  - key 패턴: `rate:<routeId>:<tenant_id>:<ip-or-account>`
- Spring Cloud Gateway 의 `RequestRateLimiter` filter 사용 (Redis Lua script)
- fail-open: Redis 다운 시 트래픽 통과 (alarm 메트릭 노출)

### 6. Traefik 통합

- `docker-compose.yml` (project-level, `projects/fan-platform/docker-compose.yml`):
  - gateway-service 컨테이너:
    - `expose: ["8080"]`
    - Traefik 라벨:
      - `traefik.enable=true`
      - `traefik.http.routers.fan-platform.rule=Host(\`fan-platform.local\`)`
      - `traefik.http.services.fan-platform.loadbalancer.server.port=8080`
    - networks: `traefik-net` (external) + `fan-platform-net`
  - postgres / redis: `expose:` only, `fan-platform-net` 만 join

### 7. Tests

- 단위 테스트: `TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`, `TenantGateFilterTest` (wms 패턴 복제)
- 슬라이스 테스트: `OAuth2ResourceServerConfigTest` (`@WebFluxTest`)
- 통합 테스트: `GatewayBootstrapIntegrationTest` (`@SpringBootTest` + WireMock for JWKS):
  - `tenant_id=fan-platform` JWT → 다운스트림 mock 200 반환
  - `tenant_id=wms` JWT → 403 `TENANT_FORBIDDEN`
  - 만료 / 서명 불일치 JWT → 401 `UNAUTHORIZED`
- 모든 통합 테스트는 `@Tag("integration")` (Testcontainers 기반 — Docker 필요)

### 8. spec 작성

본 태스크에서 함께 작성:

- `projects/fan-platform/specs/services/gateway-service/architecture.md` — Service Type, Architecture Style, Internal Structure, Allowed/Forbidden Dependencies, Boundary Rules
- `projects/fan-platform/specs/integration/gap-integration.md` — wms 의 같은 파일 복제, `tenant_id=wms` → `tenant_id=fan-platform` 만 교체
- `projects/fan-platform/docker-compose.yml` — gateway + postgres + redis (community/artist 는 후속 태스크에서 추가)

## Out of Scope

- community-service / artist-service 부트스트랩 — 별도 태스크 (TASK-FAN-BE-002 / 003)
- 실제 도메인 라우트 (예: `/api/community/posts`) — 후속 태스크에서 라우트 활성화
- Frontend (Next.js) — TASK-FAN-FE-001 별도 태스크
- 멤버십 / notification / admin (v2 service)
- HTTPS / TLS — TASK-MONO-022 의 follow-up
- E2E 시나리오 (post 발행 → 피드 조회 등) — community/artist 완료 후 별도 INT 태스크
- TASK-MONO-024 (기존 3 프로젝트 마이그레이션) 와 별개 — 본 태스크는 fan-platform 만

---

# Acceptance Criteria

- [ ] `./gradlew :projects:fan-platform:apps:gateway-service:build` 통과
- [ ] `./gradlew :projects:fan-platform:apps:gateway-service:check` 통과 (단위 + 슬라이스 테스트)
- [ ] `./gradlew :projects:fan-platform:apps:gateway-service:integrationTest` 통과 (Docker 필요, `@Tag("integration")` 만)
- [ ] `pnpm traefik:up` + `docker compose --project-directory projects/fan-platform up -d` 후 `curl http://fan-platform.local/actuator/health` → 200 OK
- [ ] valid `tenant_id=fan-platform` JWT 로 인증 시 다운스트림 mock 통과 (통합 테스트로 검증)
- [ ] `tenant_id=wms` JWT 시도 → 403 `TENANT_FORBIDDEN`
- [ ] 인증 없는 요청 시도 → 401 `UNAUTHORIZED`
- [ ] Redis 기반 rate limit 동작 — 임계 초과 시 429
- [ ] Redis 다운 시 fail-open 으로 트래픽 통과 + 메트릭 노출
- [ ] specs/services/gateway-service/architecture.md 가 [platform/architecture-decision-rule.md](../../platform/architecture-decision-rule.md) 의 형식 따름
- [ ] specs/integration/gap-integration.md 가 [wms 의 동일 파일](../../projects/wms-platform/specs/integration/gap-integration.md) 패턴 따름

---

# Related Specs

- `PROJECT.md` § Service Map (v1) — gateway-service 의 책임
- `PROJECT.md` § GAP IdP Integration
- `rules/domains/fan-platform.md` § F2 (fail-closed), F7 (multi-tenant)
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md` — OIDC 표준
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` — 통합 가이드
- `projects/wms-platform/specs/integration/gap-integration.md` — 참조 패턴

# Related Skills

- `.claude/skills/backend/spring-boot-bootstrap/` (있다면)
- `.claude/skills/backend/oauth2-resource-server/` (있다면)
- `.claude/skills/infra/docker-compose-traefik/` (있다면)
- `.claude/skills/cross-cutting/multi-tenant-validation/` (있다면)

---

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 / OIDC Endpoints (참조)
- `projects/fan-platform/specs/contracts/http/community-api.md` (TASK-FAN-BE-002 에서 신규 — 본 태스크에서는 placeholder route 만)

---

# Target Service / Component

- `projects/fan-platform/apps/gateway-service/` (신규)
- `projects/fan-platform/specs/services/gateway-service/architecture.md` (신규)
- `projects/fan-platform/specs/integration/gap-integration.md` (신규)
- `projects/fan-platform/docker-compose.yml` (신규)
- `projects/fan-platform/.env.example` (신규)
- `settings.gradle` (루트, gateway-service include 추가)
- 루트 `package.json` 에 `fan-platform:up/down/ps/logs` 스크립트 추가 (다른 프로젝트 패턴 따름)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. Service Type = `rest-api` (Spring Cloud Gateway 도 본질은 REST API 게이트웨이).

Layered (thin):

```
projects/fan-platform/apps/gateway-service/src/main/java/com/example/fanplatform/gateway/
├── GatewayServiceApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── RoutingConfig.java                ← Gateway routes (community/artist placeholder)
├── filter/
│   ├── TenantGateFilter.java             ← global filter
│   └── HeaderEnrichmentFilter.java       ← X-Tenant-Id, X-Account-Id 전파
├── ratelimit/
│   └── RateLimitConfig.java              ← Redis rate limiter
├── security/
│   ├── OAuth2ResourceServerConfig.java
│   ├── AllowedIssuersValidator.java      ← wms 패턴 복제
│   └── TenantClaimValidator.java         ← tenant_id=fan-platform 만 허용
└── error/
    └── GatewayErrorHandler.java          ← 401/403/429 envelope
```

---

# Implementation Notes

- **wms-platform/apps/gateway-service 를 첫 reference 로 복제** + 다음만 변경:
  - 패키지: `com.wms.gateway` → `com.example.fanplatform.gateway`
  - `tenant_id=wms` → `tenant_id=fan-platform`
  - 라우트: master/inventory/inbound/outbound → community/artist (placeholder, 실제 service 부트스트랩 시 활성화)
- spring-cloud-gateway 와 spring-boot-starter-web 은 충돌 (WebFlux vs Servlet). gateway 는 **Reactive** 만 사용 (`spring-cloud-starter-gateway` 의존성).
- Redis rate limit 의 fail-open 정책은 [rules/traits/integration-heavy.md](../../rules/traits/integration-heavy.md) I3 / I8 따름. 메트릭 이름 `gateway_ratelimit_redis_unavailable_total`.
- Traefik 통합 시 외부 네트워크 `traefik-net` 가 사전 존재해야 함 (TASK-MONO-022 에서 신설됨). README 에 prerequisites 명시.
- 후속 community/artist service 부트스트랩 태스크가 본 태스크의 SecurityConfig·TenantClaimValidator·AllowedIssuersValidator 를 import 또는 복제. 첫 부트스트랩이라 어쩔 수 없이 일부 패턴 코드는 다른 서비스에서 동일하게 작성됨 — 추후 `libs/` 추출은 [rules of three](../../TEMPLATE.md) 적용 후 결정.

---

# Edge Cases

- **OIDC discovery 실패 (GAP 다운)**: gateway 시작 시 retry. 30초 후에도 실패하면 fail-fast (Spring Boot 부트 실패) — 운영자가 즉시 인지.
- **legacy issuer (global-account-platform) 토큰**: D2-b deprecation 윈도우 동안 통과. SAS issuer 와 양쪽 검증.
- **`tenant_id` claim 누락**: 401 `TOKEN_INVALID` (claim 자체가 필수 — `multi-tenancy.md` § JWT Changes).
- **rate limit Redis Lua 스크립트 오류**: fail-open + 메트릭. circuit breaker 검토 (v2).
- **다운스트림 service 미동작 (community-service 미부트스트랩 시점)**: 504 `SERVICE_UNAVAILABLE` 응답. 라우트는 placeholder 로 두되 503/504 envelope 일관성 보장.

---

# Failure Scenarios

- **JWKS rotation 시 stale 캐시로 401 폭주**: Spring Security 의 JwkSet 캐시 TTL 단축 (5분) + manual refresh 엔드포인트 (운영 시 옵션).
- **공유 Redis 의 rate limit key 가 다른 프로젝트와 충돌**: key 에 `tenant_id` 포함 + 프로젝트 prefix 명시 (예: `rate:fan-platform:<route>:<ip>`).
- **`tenant_id=*` (SUPER_ADMIN platform-scope) 토큰 거부 회귀**: SUPER_ADMIN 은 platform-scope 이므로 fan-platform 도 통과시켜야 함. `TenantClaimValidator` 가 `tenant_id IN ('fan-platform', '*')` 허용.

---

# Test Requirements

- 단위:
  - `TenantClaimValidatorTest` — `fan-platform`, `wms`, `*`, null 케이스
  - `AllowedIssuersValidatorTest` — SAS, legacy, 알 수 없는 issuer
  - `TenantGateFilterTest` — 통과 / 거부 / 헤더 전파 검증
- 슬라이스: `@WebFluxTest` 로 SecurityConfig 단독 검증
- 통합 (`@Tag("integration")`):
  - `GatewayBootstrapIntegrationTest` — WireMock JWKS + mock downstream
  - `GatewayRateLimitIntegrationTest` — Redis Testcontainer + 임계 초과 시 429
  - `GatewayHealthCheckIntegrationTest` — `/actuator/health` 200

---

# Definition of Done

- [ ] gateway-service 코드 작성 완료 (config / security / filter / ratelimit / error)
- [ ] 단위 + 슬라이스 + 통합 테스트 작성 + 통과
- [ ] specs/services/gateway-service/architecture.md + specs/integration/gap-integration.md 작성
- [ ] docker-compose.yml + .env.example 작성
- [ ] settings.gradle 루트 갱신
- [ ] `pnpm fan-platform:up` 으로 기동 + `curl http://fan-platform.local/actuator/health` 200 확인
- [ ] `valid JWT (tenant_id=fan-platform)` → 통과, `tenant_id=wms` → 403 시연 가능
- [ ] Ready for review
