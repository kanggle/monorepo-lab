# Task ID

TASK-SCM-BE-001

# Title

scm-platform gateway-service Spring Boot 부트스트랩 (OIDC + Traefik)

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

scm-platform 의 첫 service 인 `gateway-service` 를 부트스트랩한다. 이 service 는 scm-platform 의 모든 외부 트래픽 진입점이며, **procurement / inventory-visibility 등 모든 백엔드 service 의 reference implementation 패턴** 이 된다 — 후속 service 부트스트랩 태스크가 이 코드를 복제 + 도메인 로직 추가하는 형태로 진행됨.

이 태스크 완료 후:

- `projects/scm-platform/apps/gateway-service/` 에 Spring Boot 3.4 + Spring Cloud Gateway 기반 reverse proxy 가 동작
- GAP IdP (`http://gap.local`) 의 OIDC JWKS 로 RS256 access token 검증 (TASK-MONO-042 V0013 시드된 `scm-platform-internal-services-client` 발급 토큰)
- `tenant_id=scm` claim 만 통과 — 그 외 (`wms`, `ecommerce`, `fan-platform`, ...) 는 403 `TENANT_FORBIDDEN`
- Redis 기반 rate limit
- Traefik label 통합 — `Host(\`scm.local\`)` 로 라우팅
- 헬스 엔드포인트 `/actuator/health` + `/actuator/info` 노출
- 단위 + 슬라이스 테스트 + integration smoke test (Testcontainers + WireMock JWKS)
- `docker-compose.yml` (project-level) 의 gateway-service 활성화 (TASK-MONO-040 의 placeholder 주석 제거)

본 task 는 **TASK-MONO-040 의 후속** 이며, **TASK-MONO-042 의 의존자** — V0013 시드 client 가 발급한 토큰을 본 gateway 가 검증한다.

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/scm-platform/apps/gateway-service/` 디렉토리 + `build.gradle` (Spring Boot starter, Spring Cloud Gateway, OAuth2 Resource Server, Redis, Lombok, libs:java-common/web/security/observability)
- 루트 `settings.gradle` 에 `'projects:scm-platform:apps:gateway-service'` include 추가

### 2. Application configuration

- `GatewayServiceApplication.java` (main class, Spring Boot 3.4)
- `application.yml` (default + `local` profile):
  - `spring.cloud.gateway.routes` — `/api/procurement/**` / `/api/inventory-visibility/**` placeholder (실 라우트 활성화는 후속 BE-002/003)
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${OIDC_ISSUER_URL:http://gap.local}`
  - `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${JWT_JWKS_URI:${OIDC_ISSUER_URL}/oauth2/jwks}`
  - Redis: `spring.data.redis.host`, `port`, `password`
- `application-test.yml` (테스트 profile)

### 3. Security config

- `OAuth2ResourceServerConfig.java`:
  - `JwtDecoder` — JWKS URI 기반, RS256 only
  - `JwtAuthenticationConverter` — `scope` / `roles` claim → Spring Security GrantedAuthority
  - `AllowedIssuersValidator` — wms/fan-platform 패턴 답습 (SAS issuer + legacy 양쪽 호환)
  - `TenantClaimValidator` — `tenant_id=scm` 만 통과, 그 외 reject (403 `TENANT_FORBIDDEN`)
- `SecurityConfig.java`:
  - `/actuator/health`, `/actuator/info` 인증 면제
  - 그 외 모든 경로 인증 필수 + JWT 검증

### 4. Tenant gate filter

- `TenantGateFilter.java` (Gateway global filter):
  - JWT claim `tenant_id` 추출 → `scm` 이 아니면 403
  - 다운스트림으로 `X-Tenant-Id`, `X-Account-Id` (= `sub` claim), `X-Scopes` (또는 `X-Roles`) 헤더 전파
  - `scm-platform-internal-services-client` 의 client_credentials 토큰은 `sub` 가 client_id — 본 케이스에서는 `X-Account-Id` 가 client_id 로 매핑됨 명시

### 5. Rate limit

- Redis 기반 rate limit:
  - 글로벌 기본: 60 req / 분 / IP
  - 인증된 요청: 600 req / 분 / account-or-client
  - key 패턴: `rate:<routeId>:<tenant_id>:<ip-or-account>`
- Spring Cloud Gateway 의 `RequestRateLimiter` filter (Redis Lua)
- fail-open: Redis 다운 시 트래픽 통과 (alarm 메트릭 노출)

### 6. Traefik 통합

- `projects/scm-platform/docker-compose.yml` 갱신:
  - TASK-MONO-040 의 gateway-service 주석 처리된 블록 활성화 (build context, environment, labels, networks)
  - postgres / redis: `expose:` only (TASK-MONO-040 그대로)

### 7. Tests

- 단위: `TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`, `TenantGateFilterTest` (fan-platform 패턴 복제)
- 슬라이스: `OAuth2ResourceServerConfigTest` (`@WebFluxTest`)
- 통합: `GatewayBootstrapIntegrationTest` (`@SpringBootTest` + WireMock JWKS):
  - `tenant_id=scm` JWT → 다운스트림 mock 200
  - `tenant_id=wms` JWT → 403 `TENANT_FORBIDDEN`
  - 만료 / 서명 불일치 → 401 `UNAUTHORIZED`
  - client_credentials 토큰 (sub = client_id) 도 통과 — scm 의 v1 주 사용 패턴
- 모든 통합 테스트 `@Tag("integration")` (Docker 필요)

### 8. spec 작성

본 task 에서 함께 작성:

- `projects/scm-platform/specs/services/gateway-service/architecture.md` — Service Type (rest-api), Architecture Style (Layered, Spring Cloud Gateway 특성상 Hexagonal 부적합), Internal Structure, Allowed/Forbidden Dependencies, Boundary Rules
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` — public route catalog (현재는 placeholder, BE-002+ 시 채움)
- `projects/scm-platform/specs/integration/gap-integration.md` — fan-platform / wms 의 같은 파일 패턴 답습, `tenant_id=scm` + V0013 client 정보 명시

### 9. CI 통합

- 루트 `.github/workflows/ci.yml` 의 Build & Test step gradle 리스트에 추가:
  - `:projects:scm-platform:apps:gateway-service:check`
- `:bootJar` 검증은 별도 step 없이 `:check` 와 함께 (fan-platform / wms 패턴) — 또는 별도 boot-jars (scm) job 추가는 후속 task

## Out of Scope

- **procurement-service / inventory-visibility-service 부트스트랩** — 별도 task (TASK-SCM-BE-002 / TASK-SCM-BE-003).
- **실 도메인 라우트** (예: `/api/procurement/po`) 의 다운스트림 동작 — 라우트는 gateway 에 placeholder 로만 존재, BE-002 활성화 시점에 다운스트림 service 가 응답.
- **Frontend** — scm v1 = backend only. user-flow PKCE OIDC client 도 미발행 (TASK-MONO-042 Out of Scope 명시).
- **HTTPS / TLS** — TASK-MONO-022 후속.
- **E2E 시나리오** — procurement 도메인 모델 등장 후 별도 INT task.
- **`scripts/sync-portfolio.sh` PROJECT_REMOTES 등록** — 첫 v1 publish 시점.

---

# Acceptance Criteria

## Build / Test

1. `./gradlew :projects:scm-platform:apps:gateway-service:build` 통과
2. `./gradlew :projects:scm-platform:apps:gateway-service:check` 통과 (단위 + 슬라이스, Docker 미필요)
3. `./gradlew :projects:scm-platform:apps:gateway-service:integrationTest` 통과 (Docker 필요, `@Tag("integration")`)
4. 루트 CI 의 Build & Test gradle 리스트에 본 모듈 추가 + 통과

## Runtime

5. `pnpm traefik:up` + `pnpm scm:up` 후 `curl http://scm.local/actuator/health` → 200 OK
6. valid `tenant_id=scm` JWT (V0013 client_credentials 발급) 로 인증 시 다운스트림 mock 통과
7. `tenant_id=wms` (또는 다른 tenant) JWT → 403 `TENANT_FORBIDDEN`
8. 인증 없는 요청 → 401 `UNAUTHORIZED`
9. 만료 / 서명 불일치 JWT → 401 `UNAUTHORIZED`

## Token 통합

10. dev 토큰 발급 smoke test:
    ```
    curl -u scm-platform-internal-services-client:scm-dev \
         -d "grant_type=client_credentials&scope=scm.read" \
         http://gap.local/oauth2/token
    ```
    → 200 + JWT 응답. JWT decode 시 `tenant_id=scm`, `aud=scm-platform-internal-services-client`, `scope=scm.read`.
11. 위 발급 토큰을 gateway 에 전달 시 (placeholder 라우트 / 또는 actuator/info) 인증 통과.

## Spec 무결성

12. `architecture.md` Service Type / Architecture Style 명시.
13. `gap-integration.md` 의 V0013 client_id / scope 가 본 gateway 의 `OIDC_REQUIRED_TENANT_ID` 환경변수와 정합.
14. `gateway-public-routes.md` placeholder 라우트가 docker-compose / application.yml 와 정합.

## Library 경계 / Hexagonal

15. gateway 는 Spring Cloud Gateway 의 routing-centric 구조라 Hexagonal 강제 안 함 — Layered 명시 (Architecture Decision Rule 준수).
16. domain logic 0 — gateway 는 routing/auth/rate-limit 만, 비즈니스 도메인 코드 금지.

## CI

17. CI 의 Build & Test step 에서 gateway-service `:check` 통과.

---

# Related Specs

- [TASK-MONO-040](../../../tasks/done/TASK-MONO-040-scm-platform-bootstrap.md) (done) — scm-platform skeleton 부트스트랩. 본 task 는 그 후속.
- [TASK-MONO-042](../../../tasks/done/TASK-MONO-042-gap-v0013-scm-oidc-clients.md) (done) — GAP V0013 시드. 본 task 의 토큰 발급원.
- [TASK-FAN-BE-001](../../../projects/fan-platform/tasks/done/TASK-FAN-BE-001-gateway-service-bootstrap.md) (done) — reference implementation. 본 task 는 그 패턴 답습 + tenant_id 만 교체.
- [TASK-MONO-019](../../../tasks/done/TASK-MONO-019-wms-platform-oidc-resource-server-migration.md) (done) — OAuth2 Resource Server + AllowedIssuersValidator 패턴.
- [`PROJECT.md`](../../PROJECT.md) — domain / traits / Service Map / GAP integration.
- [`rules/domains/scm.md`](../../../rules/domains/scm.md) — scm 도메인 mandatory rules (gateway 단계에서는 S6 supplier credentials 암호화 외 직접 적용 룰 없음).
- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`
- `.claude/skills/` — `oauth2-resource-server-config`, `spring-cloud-gateway-routing` (있다면)

# Related Contracts

본 task 에서 신설:

- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (placeholder)

GAP 측 contract (이미 존재):

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients (V0013 행, TASK-MONO-042 머지).

---

# Edge Cases

1. **client_credentials 토큰의 `sub` claim** — V0013 `scm-platform-internal-services-client` 의 토큰은 `sub` 가 client_id 자체. `X-Account-Id` 헤더 전파 시 client_id 로 매핑. 다운스트림 service 가 사람 사용자 / 머신 사용자 구분 시 `X-Token-Type` (또는 `X-Sub-Type`) 추가 검토 (본 task 에서 결정).
2. **JWKS endpoint URL** — TASK-MONO-035 의 fan-platform/wms JWKS URI 정렬 (`/oauth2/jwks` vs `/.well-known/jwks.json`) — V0013 SQL 도 동일 endpoint 사용. application.yml default 값을 `/oauth2/jwks` 로 통일.
3. **scope 검증 vs role 검증** — V0013 의 토큰은 `scope=scm.read scm.write` claim 만 가짐. role claim 없을 수 있음. JwtAuthenticationConverter 가 양쪽 모두 처리.
4. **placeholder 라우트의 response** — procurement / inventory-visibility service 가 아직 없으므로 `/api/procurement/**` 호출 시 어떻게 응답? 옵션: (a) 503 Service Unavailable (라우트 정의 + downstream 부재), (b) 라우트 자체 미정의 → 404. (a) 가 더 명확. 본 task 에서 결정.
5. **AllowedIssuersValidator 의 issuer 목록** — wms/fan-platform 의 패턴: SAS issuer + legacy global-account-platform issuer. scm 의 application.yml 도 동일 list — V0013 시점에는 SAS issuer 만 있어도 충분하나 호환성 차원에서 list 로 둠.

---

# Failure Scenarios

## A. JWKS endpoint 응답 캐시 불일치

GAP key rotation 시 gateway 의 JWKS 캐시가 stale → 새 토큰 401. 해결: `spring.security.oauth2.resourceserver.jwt.cache-duration` 적절 설정 + JWKS endpoint 의 cache-control 헤더 준수. fan-platform / wms 패턴 답습.

## B. Redis 다운 시 rate limit fail-closed 동작

기본 spring-cloud-gateway 의 RequestRateLimiter 는 Redis 실패 시 fail-closed (요청 차단). production 운영성을 위해 fail-open 으로 override (filter wrapper) — fan-platform 의 같은 fix 답습.

## C. tenant_id claim 누락

token 의 `tenant_id` claim 자체가 없는 경우 — V0013 시드는 `tenant_id=scm` 자동 부여하므로 정상 케이스에서는 발생 안 함. 그러나 legacy / 외부 발급 토큰 시도 시 가능 — TenantClaimValidator 가 `claim missing` 도 reject (403).

## D. integration test 의 Docker 환경 (Windows)

WSL2 + Docker Desktop 환경에서 Testcontainers 동작 (메모리 `project_testcontainers_docker_desktop_blocker.md` resolved). WireMock 사용 시 HTTP/2 NEGOTIATE 이슈 — TASK-MONO-023a 패턴 (HTTP/1.1 강제).

## E. Spring Cloud Gateway 의 `--enable-preview` 등 JDK 21 옵션

gateway-service `:bootRun` / `:test` 시 root build.gradle 의 Java 21 옵션 (sealed types 등) 호환성. fan-platform / wms 가 통과한 옵션을 그대로 답습.

---

# Notes

- **Recommended impl model**: **Opus** — security config + reactive Gateway + Tenant filter + integration test 의 동시 작성. 분석=Opus 4.7 / 구현 권장=Opus.
- **dependency 표현**:
  - `선행`: TASK-MONO-040 (done), TASK-MONO-042 (done).
  - `후속`: TASK-SCM-BE-002 (procurement-service skeleton — 본 task 의 placeholder 라우트 활성화), TASK-SCM-BE-003 (inventory-visibility-service).
- **Phase 4 evaluation 영향**: 본 task 는 `projects/scm-platform/apps/` 안에서만 작업 — 라이브러리 layer 변경 0. ADR-MONO-002 D3 churn 안정 평가에 직접적 입력 없음 (오히려 평가 데이터 — "라이브러리를 만지지 않고도 새 service 가 부트스트랩 가능했나" 의 검증).
- **CI 변경**: 루트 `.github/workflows/ci.yml` 의 Build & Test gradle 리스트에 1줄 추가 — 이건 `.github/workflows/` 가 라이브러리 영역이라 D3 평가 입력. 단 1줄 추가로 churn 영향 minimal.
