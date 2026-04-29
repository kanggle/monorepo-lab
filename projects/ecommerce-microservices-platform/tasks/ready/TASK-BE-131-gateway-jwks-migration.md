# Task ID

TASK-BE-131

# Title

ecommerce gateway-service JWT 검증 방식을 HS256 → JWKS/RSA(RS256)로 교체

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`gateway-service`의 JWT 검증 방식을 현재의 HS256(HMAC 대칭 서명, `jwt.secret` 하드코딩)에서 Global Account Platform의 JWKS 엔드포인트를 통한 RSA 비대칭 서명 검증으로 전환한다.

현재 구현체(`JwtAuthenticationFilter`)는 `io.jsonwebtoken` + HMAC secret을 사용한다. 전환 후에는 Spring Security OAuth2 Resource Server + JWKS URI를 사용하고, 신규 JWT 계약(`platform/contracts/jwt-standard-claims.md`)에 명시된 `aud`, `account_type`, `roles` 클레임을 완전히 검증한다.

---

# Scope

## In Scope

- `JwtAuthenticationFilter` 삭제 → Spring Security `oauth2ResourceServer(jwt { ... })` 방식으로 대체
- `SecurityConfig` (신규 또는 기존 수정): `aud: ecommerce` 검증 추가
- `AccountTypeEnforcementFilter` 신규 추가 (Spring Security GlobalFilter):
  - `/api/admin/**` 경로: `account_type == OPERATOR` 요구 → 아니면 403
  - 그 외 경로(인증 필요 경로): `account_type == CONSUMER` 요구 → 아니면 403
  - Public 경로(actuator 등): 통과
- `JwtHeaderEnrichmentFilter` 신규 추가 (Spring Security 인증 이후 동작):
  - `X-User-Id` ← `sub`
  - `X-User-Role` ← `roles` 배열 → comma-separated 문자열 (e.g. `CUSTOMER`, `ADMIN`)
  - `X-User-Email` ← `email`
  - `X-Account-Type` ← `account_type`
- `IdentityHeaderStripFilter`: 기존 `X-User-Id`, `X-User-Email`, `X-User-Role` 외에 `X-Account-Type`도 스트립
- `application.yml` 변경:
  - `jwt.secret` 제거
  - `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${JWT_JWKS_URI}` 추가
  - `spring.security.oauth2.resourceserver.jwt.audiences: ecommerce` 추가
- `docker-compose.yml`: `auth-service` 의존성 유지하되 `JWT_JWKS_URI=http://auth-service:8081/auth/.well-known/jwks.json` 환경변수 추가 (이관 완료 전 임시값; 이관 후 Global Account Platform URL로 교체)
- `JwtAuthenticationFilterTest` 삭제 → `AccountTypeEnforcementFilterTest`, `JwtHeaderEnrichmentFilterTest` 신규 추가
- `RouteService` 가 admin 경로 판별에도 사용될 수 있으면 재사용, 아니면 `AccountTypeEnforcementFilter` 내부에서 직접 path matching

## Out of Scope

- auth-service 폐기 (별도 TASK-BE-132)
- WMS gateway 변경 (별도 TASK-BE-042)
- ecommerce 하위 서비스의 `X-User-Role` 파싱 변경 (`roles` 배열 join 결과가 기존 단일 역할과 동일 문자열이므로 기존 `.contains()` 패턴 유지 가능)
- Global Account Platform 이관 자체

---

# Acceptance Criteria

- [x] `GET /actuator/health` → JWT 없이 200
- [x] `GET /api/products` (CONSUMER token, `aud: ecommerce`, `account_type: CONSUMER`, `roles: ["CUSTOMER"]`) → 200; downstream에 `X-User-Role: CUSTOMER`, `X-Account-Type: CONSUMER` 헤더 전달
- [x] `GET /api/admin/products` (OPERATOR token, `aud: ecommerce`, `account_type: OPERATOR`, `roles: ["ADMIN"]`) → 200; downstream에 `X-User-Role: ADMIN`, `X-Account-Type: OPERATOR` 헤더 전달
- [x] `GET /api/admin/products` (CONSUMER token) → 403
- [x] `GET /api/products` (OPERATOR token) → 403 (CONSUMER 전용 경로에 OPERATOR 진입 시)
- [x] JWT 없음 → 401
- [x] 만료된 JWT → 401
- [x] 잘못된 서명 → 401
- [x] `aud: wms` 토큰으로 ecommerce 게이트웨이 접근 → 403
- [x] `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Account-Type` 스푸핑 헤더 스트립 확인

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` — 클레임 구조, aud, account_type 게이트웨이 검증 규칙
- `specs/services/gateway-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/gateway-security.md` (있으면)
- `.claude/skills/service-types/identity-platform-setup/SKILL.md` (integration 참조)

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — 변경 없음 (이 태스크는 계약의 구현체)
- `specs/contracts/http/gateway-api.md` (있으면) — 변경 없음

---

# Target Service

- `gateway-service`

---

# Architecture

## Spring Security OAuth2 Resource Server 전환

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWKS_URI:http://localhost:8088/.well-known/jwks.json}
          audiences: ecommerce
```

```java
// SecurityConfig.java
http
  .oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
  .authorizeExchange(auth -> auth
    .pathMatchers(PUBLIC_PATHS).permitAll()
    .anyExchange().authenticated());
```

## AccountTypeEnforcementFilter

```java
// 인증 성공 후, 헤더 주입 전에 실행
// ReactiveSecurityContextHolder에서 Jwt 꺼내 account_type 확인
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (isPublicPath(exchange.getRequest().getPath().value())) {
        return chain.filter(exchange);
    }
    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> (JwtAuthenticationToken) ctx.getAuthentication())
        .flatMap(auth -> {
            String accountType = auth.getToken().getClaimAsString("account_type");
            String path = exchange.getRequest().getPath().value();
            if (isAdminPath(path) && !"OPERATOR".equals(accountType)) {
                return writeForbidden(exchange, "Admin access requires OPERATOR account");
            }
            if (!isAdminPath(path) && !"CONSUMER".equals(accountType)) {
                return writeForbidden(exchange, "Consumer paths require CONSUMER account");
            }
            return chain.filter(exchange);
        });
}

private boolean isAdminPath(String path) {
    return path.startsWith("/api/admin/");
}
```

## JwtHeaderEnrichmentFilter

```java
private ServerWebExchange enrich(ServerWebExchange exchange, Jwt jwt) {
    String subject = jwt.getSubject();
    String email = jwt.getClaimAsString("email");
    String accountType = jwt.getClaimAsString("account_type");
    List<String> roles = jwt.getClaimAsStringList("roles");
    String roleHeader = (roles != null && !roles.isEmpty())
        ? String.join(",", roles) : "";

    return exchange.mutate().request(
        exchange.getRequest().mutate()
            .header("X-User-Id", subject)
            .header("X-User-Email", email)
            .header("X-User-Role", roleHeader)
            .header("X-Account-Type", accountType)
            .build()
    ).build();
}
```

---

# Edge Cases

- `roles: []` (빈 배열) → `X-User-Role: ""` — downstream이 빈 역할로 403 처리
- `kid` 미인식 → Spring Security가 JWKS 재조회 → 실패 시 401
- JWKS 엔드포인트 응답 불가 → Spring Security 기본 동작: 401 (캐시 만료 전까지는 캐시 사용)
- `aud` 클레임이 배열인 경우 → Spring Security `audiences` 설정이 포함 여부 확인
- `/api/admin/` 경로에 CONSUMER 토큰 → AccountTypeEnforcementFilter가 403 반환 (Spring Security 인증 자체는 통과)
- 일반 경로에 OPERATOR 토큰 → AccountTypeEnforcementFilter가 403 반환

---

# Failure Scenarios

- JWKS URI 미설정 → `application.yml` 오류로 앱 시작 실패 (fail-fast)
- JWKS 엔드포인트 1시간 이상 불통 → 캐시된 키로 검증 → 이후 캐시 만료 시 503 (per jwt-standard-claims.md §Error Handling)
- `account_type` 클레임 부재 → `null` 처리 → 403

---

# Test Requirements

- **단위 테스트**: `AccountTypeEnforcementFilterTest` — admin path + OPERATOR 통과, admin path + CONSUMER 403, consumer path + OPERATOR 403, public path 통과
- **단위 테스트**: `JwtHeaderEnrichmentFilterTest` — 헤더 주입 검증, roles 배열 → comma-separated, 빈 roles → 빈 문자열
- **슬라이스 테스트**: `JwtAuthenticationFilterTest` (삭제) → 불필요 (Spring Security가 대체)
- E2E: 기존 `GatewayE2ETest`가 있다면 JWKS mock 사용으로 전환 (JwksMockServer 패턴 참조: `projects/wms-platform/apps/gateway-service/src/test/`)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
