# Task ID

TASK-MONO-104

# Title

GAP sweep dry-run cherry-pick bundle — security/gateway/admin 3 trivial polish (~27 LOC)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- gap
- refactor-cherry-pick
- sweep-dry-run-followup

---

# Goal

GAP services sweep dry-run (TASK-MONO-104 sister analysis, MONO-103 closure 후) 의 cherry-pick bundle closure.

7 service dry-run 결과:
- **5/7 day-one DDD/Hexagonal cohort** (community / membership / security / gateway / account)
- **2/7 retrofit-era 잠재 MID GO** (admin / auth) — 별 sweep task 후보로 deferred (port refactor + cast 제거 = contract change, 본 cherry-pick scope 외)

본 task = day-one cohort 의 small isolated cleanup 3 unit 묶음:
1. **security**: `AbstractAuthEventConsumer.nowIfNull` dead method 제거 (~-7 LOC)
2. **gateway**: `JwtAuthenticationFilter.writeUnauthorized/writeForbidden` boilerplate → `writeErrorResponse` 공통 helper (~-15 LOC)
3. **admin**: `AdminLoginService.bootstrapTokenService.issue + TTL` 2 site → `issueBootstrapToken` private helper (~-10 LOC)

총 ~-32 LOC, 3 file, 0 production behavior change.

# Scope

## In Scope

### 1. security-service — `AbstractAuthEventConsumer.nowIfNull` 제거

File: `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/consumer/AbstractAuthEventConsumer.java` L130-137

```java
/**
 * Convenience helper — kept for compatibility with callers that do not need
 * the detection use-case (not used internally).
 */
@SuppressWarnings("unused")
private static Instant nowIfNull(Instant t) {
    return t == null ? Instant.now() : t;
}
```

`@SuppressWarnings("unused")` 자체가 dead-code 신호. private static + 0 internal callers → 안전 제거. javadoc 도 함께 제거.

### 2. gateway-service — `writeErrorResponse` helper 추출

File: `projects/global-account-platform/apps/gateway-service/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java` L255-289

현재 `writeUnauthorized(exchange, message)` + `writeForbidden(exchange, message)` 2 method 가 동일 boilerplate:
- HTTP status set
- Content-Type JSON
- ErrorResponse.of(code, message)
- try/catch JsonProcessingException + fallback 문자열

추출:
```java
private Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                      HttpStatus status,
                                      String code,
                                      String message,
                                      String fallbackJsonBody) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    ErrorResponse errorResponse = ErrorResponse.of(code, message);
    try {
        byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    } catch (JsonProcessingException e) {
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(fallbackJsonBody.getBytes(StandardCharsets.UTF_8))));
    }
}

private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
    return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", message,
            "{\"code\":\"TOKEN_INVALID\",\"message\":\"Access token is missing, expired, or has an invalid signature\"}");
}

private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
    return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED", message,
            "{\"code\":\"TENANT_SCOPE_DENIED\",\"message\":\"Tenant scope mismatch\"}");
}
```

7 caller (L90/101/118/128/144/166/171) 미터치 — 기존 `writeUnauthorized`/`writeForbidden` signature 보존.

### 3. admin-service — `issueBootstrapToken` private helper 추출

File: `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/AdminLoginService.java` L103-121

현재 동일 패턴 2 site (L106-111 enrollment-required, L115-120 enrolled-but-never-verified):
```java
BootstrapTokenService.Issued issued = bootstrapTokenService.issue(
        operatorUuid,
        java.util.Set.of(BootstrapTokenService.SCOPE_ENROLL, BootstrapTokenService.SCOPE_VERIFY));
long ttl = java.time.Duration.between(Instant.now(), issued.expiresAt()).getSeconds();
if (ttl < 0) ttl = 0;
throw new EnrollmentRequiredException(issued.token(), ttl);
```

추출:
```java
private void buildEnrollmentRequired(UUID operatorUuid) {
    BootstrapTokenService.Issued issued = bootstrapTokenService.issue(
            operatorUuid,
            java.util.Set.of(BootstrapTokenService.SCOPE_ENROLL, BootstrapTokenService.SCOPE_VERIFY));
    long ttl = java.time.Duration.between(Instant.now(), issued.expiresAt()).getSeconds();
    if (ttl < 0) ttl = 0;
    throw new EnrollmentRequiredException(issued.token(), ttl);
}
```

2 site 모두 `buildEnrollmentRequired(operatorUuid);` 1 line 으로 단순화.

## Out of Scope

- **admin-service / auth-service MID GO sweep**: port 추가 + `instanceof RedisXxx` cast 제거 = contract change (`TokenBlacklist`/`LoginAttemptCounter` 인터페이스 변경). 별 task 별 cycle.
- **community/membership `AllowedIssuersValidator` 2-service duplication**: 2-site < 3-site 추출 threshold (메모리 `project_refactor_sweep_status.md` § policy). 다음 GAP service 추가 시 재검토.
- **account-service의 `TenantId.FAN_PLATFORM` 하드코드**: TASK-BE-228/229 TODO 로 tracked — refactor target 아님.
- **production behavior / 7 caller signature / public API / spec contract = 0 변경**.

# Acceptance Criteria

- [ ] `grep -c "nowIfNull" projects/global-account-platform/apps/security-service/src/main/java/com/example/security/consumer/AbstractAuthEventConsumer.java` = 0 (dead method 제거).
- [ ] `grep -c "private Mono<Void> writeErrorResponse" projects/global-account-platform/apps/gateway-service/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java` = 1 (helper 신설).
- [ ] `grep -c "writeUnauthorized\|writeForbidden" projects/global-account-platform/apps/gateway-service/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java` ≥ 7 (기존 7 caller 미터치).
- [ ] `grep -c "private void buildEnrollmentRequired" projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/AdminLoginService.java` = 1 (helper 신설).
- [ ] `grep -c "bootstrapTokenService\.issue" projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/AdminLoginService.java` = 1 (2 site → 1 in helper).
- [ ] `./gradlew :global-account-platform:apps:security-service:compileJava :global-account-platform:apps:gateway-service:compileJava :global-account-platform:apps:admin-service:compileJava` PASS (3 service compile 회귀 0).
- [ ] `.claude/hooks/__tests__/run-all.ps1` → 23 PASS (hook fixture 회귀 0).
- [ ] HARDSTOP fixture 영역 외 (production code 변경, fixture 검사 path 아님).
- [ ] production behavior / public API / spec contract / Service Type / Architecture Style = 0 변경.

# Related Specs

- [`projects/global-account-platform/specs/services/security-service/architecture.md`](../../../projects/global-account-platform/specs/services/security-service/architecture.md) — Hexagonal 선언 (변경 없음)
- [`projects/global-account-platform/specs/services/gateway-service/architecture.md`](../../../projects/global-account-platform/specs/services/gateway-service/architecture.md) — Layered + reactive (변경 없음)
- [`projects/global-account-platform/specs/services/admin-service/architecture.md`](../../../projects/global-account-platform/specs/services/admin-service/architecture.md) — Hexagonal (변경 없음)
- 메모리 reference: `project_refactor_sweep_status.md` § "Service Refactor Sweep — day-one DDD cohort" + GAP sweep dry-run 결과 누적

# Related Contracts

없음 (production behavior / public API / event payload 0 변경).

# Edge Cases

1. **gateway `writeErrorResponse` fallback string**: 기존 2 method 의 fallback JSON 본문이 다름 (`TOKEN_INVALID` vs `TENANT_SCOPE_DENIED` 코드 / message). helper 의 `fallbackJsonBody` 파라미터로 그대로 전달 → caller signature 변경 0.
2. **admin `buildEnrollmentRequired` 의 `throws`**: helper 가 `EnrollmentRequiredException` throw — caller 의 throws 선언 또는 try/catch 가 이미 있음 (private helper 라 새 throws 선언 불필요, Java 의 unchecked exception 인 경우 더 명확).
3. **security `nowIfNull` 의 `@SuppressWarnings`**: javadoc 의 "kept for compatibility" 주석은 의도된 dead-code 신호. 다른 file 에서 reflection 사용 안 함 (private static).
4. **HARDSTOP fixture 영향**: 본 변경은 production code (`.java`) → hook 가 `.java` 변경에 trigger 안 함 (architecture.md / shared file 만 detect). fixture 회귀 0.

# Failure Scenarios

A. **gateway helper extraction 시 caller signature 부주의 변경**: 7 caller (L90~171) 모두 기존 `writeUnauthorized(exchange, message)` / `writeForbidden(exchange, message)` signature 호출. 변경 시 컴파일 fail → 검출 가능.

B. **admin helper 의 `buildEnrollmentRequired` 가 `void` 아닌 다른 return**: 2 site 모두 `throw new EnrollmentRequiredException` 으로 끝남. helper 도 `void` + `throw` 형태로 명확. caller 에서 `return` 없이 `buildEnrollmentRequired(...)` 호출 후 자연스럽게 method exit.

C. **security `nowIfNull` 우발 외부 reference**: file 외부 `nowIfNull` 호출 grep — 0 hit 확인 후 제거.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (3 trivial polish, low judgment)
- D4 OVERRIDE 적용 — refactor cycle 의 자연 연장 (sweep dry-run cherry-pick), MONO-091/093~103 sibling (ADR-MONO-003a § D1.1)
- Lifecycle = ready → review 직접 (single-PR closure 20번째)
- 묶음 근거 = feedback_pr_bundling (3 GAP service 의 trivial cleanup, sweep dry-run 의 단일 closure)

**메모리 `project_refactor_sweep_status.md` 갱신 사항** (본 task 와 동반):
- GAP 5-service day-one cohort 확정 (community/membership/security/gateway/account)
- GAP 2-service retrofit-era 잠재 (admin/auth) — 별 sweep task 후보
- "GAP services" 의 next sweep target candidate (메모리 § "다음 sweep 의 자연 trigger" 의 (d) GAP services 검토) = 완료
- 5번째 cohort confirmed (4 → 5: wms/master + ecommerce/order + scm/procurement + ecommerce/product + GAP 5 service)

**다음 sweep target 후보** (메모리 갱신 시 명시):
- admin-service MID GO (16 file × 60 infra import, operator/totp aggregate port 누락)
- auth-service MID GO (3 port instanceof cast + resolveTenantType 중복 + OAuth JPA leak)
- 각 별 task 발행 시점 (user-explicit dispatch)
