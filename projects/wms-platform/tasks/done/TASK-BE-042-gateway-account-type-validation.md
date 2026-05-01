# Task ID

TASK-BE-042

# Title

WMS gateway-service JWT 클레임 검증 강화 — account_type + aud + X-Account-Type 헤더

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

WMS `gateway-service`는 이미 JWKS/RSA 기반 JWT 검증을 사용하고 있으나, `platform/contracts/jwt-standard-claims.md`에 추가된 두 가지 검증이 누락되어 있다:

1. **`account_type: OPERATOR` 강제**: CONSUMER 계정(쇼핑 고객)이 WMS 게이트웨이를 통과하면 안 된다. 현재는 유효한 JWT면 통과된다.
2. **`X-Account-Type` 헤더 주입 누락**: `JwtHeaderEnrichmentFilter`가 `account_type` 클레임을 downstream에 전달하지 않는다.

추가로 `spring.security.oauth2.resourceserver.jwt.audiences: wms` 설정을 추가하여 `aud` 클레임도 Spring Security 레벨에서 검증한다.

---

# Scope

## In Scope

- `SecurityConfig.java` 수정: `audiences: wms` 추가
  ```yaml
  spring.security.oauth2.resourceserver.jwt.audiences: wms
  ```
- `AccountTypeValidationFilter` 신규 추가 (GlobalFilter, Spring Security 인증 후 실행):
  - JWT에서 `account_type` 클레임 추출
  - `account_type != OPERATOR` → 403 `FORBIDDEN`
  - Public 경로(actuator) → 통과
- `JwtHeaderEnrichmentFilter` 수정:
  - `X-Account-Type` ← `account_type` 헤더 주입 추가
- `IdentityHeaderStripFilter` 수정:
  - `X-Account-Type` 헤더도 스트립 목록에 추가
- `AccountTypeValidationFilterTest` 신규 추가
- `JwtHeaderEnrichmentFilterTest` 수정: `X-Account-Type` 주입 검증 케이스 추가
- `IdentityHeaderStripFilterTest` 수정: `X-Account-Type` 스트립 확인

## Out of Scope

- JWKS URI 변경 (현재 `${JWT_JWKS_URI}` env var 이미 설정됨; Global Account Platform 이관 시 별도로 env값만 교체)
- `roles` 클레임 처리 변경 (`JwtHeaderEnrichmentFilter`가 이미 `roles` 배열 → comma-separated 처리 중)
- ecommerce gateway 변경 (별도 TASK-BE-131)

---

# Acceptance Criteria

- [x] `GET /actuator/health` → JWT 없이 200
- [x] OPERATOR 토큰 (`account_type: OPERATOR`, `aud: wms`) → 200; `X-Account-Type: OPERATOR` 헤더 downstream 전달
- [x] CONSUMER 토큰 (`account_type: CONSUMER`, `aud: wms`) → 403, code = `FORBIDDEN`
- [x] `aud: ecommerce` 토큰 → 401 (Spring Security audience 미스매치)
- [x] JWT 없음 → 401
- [x] 만료된 JWT → 401
- [x] `X-Account-Type` 스푸핑 헤더 스트립 확인 (IdentityHeaderStripFilter)
- [x] `X-User-Role`은 기존 동작(roles 배열 → comma-separated) 유지

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` — §Gateway Enforcement Rules §6 (account_type 검증), §Post-Validation Injection (X-Account-Type)
- `specs/services/gateway-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/gateway-security.md` (있으면)

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — 변경 없음 (이 태스크는 계약의 구현체)

---

# Target Service

- `gateway-service` (wms-platform)

---

# Architecture

## application.yml 변경

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWKS_URI:http://localhost:8088/.well-known/jwks.json}
          audiences: wms   # 신규 추가
```

## AccountTypeValidationFilter

```java
@Component
@Order(-1)  // JwtHeaderEnrichmentFilter(-1)와 같거나 직전에 실행
public class AccountTypeValidationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                if (!(ctx.getAuthentication() instanceof JwtAuthenticationToken auth)) {
                    return chain.filter(exchange); // public route: no auth context
                }
                String accountType = auth.getToken().getClaimAsString("account_type");
                if (!"OPERATOR".equals(accountType)) {
                    return writeForbidden(exchange, "WMS access requires OPERATOR account");
                }
                return chain.filter(exchange);
            })
            .switchIfEmpty(chain.filter(exchange)); // no security context = public route
    }
}
```

## JwtHeaderEnrichmentFilter 수정 (추가 1줄)

```java
// 기존 subject, email, role 주입 뒤에 추가
String accountType = jwt.getClaimAsString("account_type");
if (accountType != null) {
    builder.header("X-Account-Type", accountType);
}
```

## IdentityHeaderStripFilter 수정

```java
// 기존 스트립 목록에 추가
h.remove("X-Account-Type");
```

---

# Edge Cases

- `account_type` 클레임 부재 → `null` → 403 (OPERATOR 아님)
- `account_type: OPERATOR` + `aud: ecommerce` → Spring Security가 audience 불일치로 401 (account_type filter 도달 전)
- Public 경로 (actuator): SecurityContextHolder에 auth context 없음 → `switchIfEmpty` → 통과
- 멀티 역할 OPERATOR (`roles: ["WMS_OPERATOR", "OUTBOUND_MANAGER"]`) → `X-User-Role: WMS_OPERATOR,OUTBOUND_MANAGER` 기존 동작 유지

---

# Failure Scenarios

- `account_type` 클레임 없는 레거시 JWT → `null` 체크 → 403
- `audiences` 설정 후 기존 테스트 JWT에 `aud` 없으면 → `JwksMockServer` 기반 테스트 JWT에 `aud: wms` 추가 필요

---

# Test Requirements

- **단위 테스트**: `AccountTypeValidationFilterTest`
  - OPERATOR → 통과
  - CONSUMER → 403
  - public path (auth context 없음) → 통과
- **단위 테스트**: `JwtHeaderEnrichmentFilterTest` 수정 — `X-Account-Type: OPERATOR` 주입 확인
- **단위 테스트**: `IdentityHeaderStripFilterTest` 수정 — `X-Account-Type` 스트립 확인
- E2E: 기존 E2E 테스트의 mock JWT에 `account_type: OPERATOR`, `aud: wms` 클레임 추가 (JwtTestHelper 수정)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
