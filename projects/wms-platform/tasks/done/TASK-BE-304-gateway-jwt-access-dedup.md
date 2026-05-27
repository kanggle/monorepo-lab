# TASK-BE-304 — gateway-service ReactiveJwtAccess utility extract (F-L6-1 dedup)

Status: done

## Goal

`gateway-service` 의 `/refactor-code` dry-run 결과 식별된 F-L6-1 duplication 단일 finding 의 closure. 2 GlobalFilter (`JwtHeaderEnrichmentFilter` + `AccountTypeValidationFilter`) 가 공유하는 `ReactiveSecurityContextHolder` → `JwtAuthenticationToken` cast 5-line 패턴을 utility class 로 추출.

dry-run 결과 base rate (gateway-service 14 main file, 792 LOC scope):
- L0 (layer-violation) = 0
- L1 (auth placement) = 0
- L2 (dead-code) = 0
- L3 (long-method) = 0 (max 24 LOC GatewayErrorHandler.serialize, threshold 30 LOC 미만)
- L4 (pattern-mismatch) = 0
- **L6 (duplication) = 1** (본 task scope)
- (F-S1-1 spec drift = separate BE-305 후보, refactor-spec territory)

## Scope

In:

- 신규 utility class `com.wms.gateway.security.ReactiveJwtAccess.java`:
  - `public final class` + `private constructor` (utility pattern, BE-300 / BE-301 의 ProjectionConsumerSupport / JwtHelper 답습)
  - `public static Mono<JwtAuthenticationToken> currentToken()` — 5-line cast chain
  - `public static Mono<Jwt> currentJwt()` — `currentToken().map(JwtAuthenticationToken::getToken)`
- `JwtHeaderEnrichmentFilter.filter` L29-33 의 5-line cast chain → `ReactiveJwtAccess.currentJwt()` 단일 호출. `enrich(exchange, jwt)` 가 `Jwt` 받도록 signature 유지 (이미 그렇게 정의됨). `.defaultIfEmpty(exchange)` + `.flatMap(chain::filter)` 호출자 보존.
- `AccountTypeValidationFilter.filter` L32-36 의 5-line cast chain → `ReactiveJwtAccess.currentToken()` 단일 호출 (or `currentJwt()` 사용 후 `.getClaimAsString` 직접). `.flatMap(auth -> { ... })` 본문 보존. `.defaultIfEmpty(Boolean.TRUE)` + `.flatMap(proceed -> ...)` 호출자 보존.

Out:

- F-S1-1 architecture.md package layout drift (별 task BE-305 refactor-spec 후보; `config/SecurityConfig` vs spec L78 `security/SecurityConfig` + 새 file 5개 catalog 정렬).
- 다른 filter (`IdentityHeaderStripFilter` / `RequestIdFilter` / `RetryAfterFilter`) 변경 0 — `ReactiveSecurityContextHolder` 사용처가 아님.
- Security validator (`AllowedIssuersValidator` / `TenantClaimValidator`) 변경 0.
- Route / CORS 설정 변경 0.
- Test 코드 변경 = mechanical fixture update 만 (test what 의 verify unchanged).
- API / contract / DB / event 변경 0 (애초에 gateway-service 가 stateless).

## Acceptance Criteria

AC-1. 신규 `com.wms.gateway.security.ReactiveJwtAccess.java`:
- `public final class ReactiveJwtAccess` + `private ReactiveJwtAccess()` (utility pattern)
- `public static Mono<JwtAuthenticationToken> currentToken()` — 4-line body identical to existing `ReactiveSecurityContextHolder.getContext().map(ctx -> ctx.getAuthentication()).filter(JwtAuthenticationToken.class::isInstance).cast(JwtAuthenticationToken.class)`
- `public static Mono<Jwt> currentJwt()` — `currentToken().map(JwtAuthenticationToken::getToken)`
- Javadoc 짧게 (utility 의 책임 + 사용처 cross-ref)

AC-2. `JwtHeaderEnrichmentFilter.filter` body 가 cast chain 제거 + `ReactiveJwtAccess.currentJwt()` 호출. `.map(jwt -> enrich(exchange, jwt)).defaultIfEmpty(exchange).flatMap(chain::filter)` chain 보존. `enrich(exchange, Jwt)` private method signature byte-unchanged.

AC-3. `AccountTypeValidationFilter.filter` body 가 cast chain 제거 + `ReactiveJwtAccess.currentToken()` 호출 (또는 `currentJwt()` + claim 직접 access). `.flatMap` + `.defaultIfEmpty(Boolean.TRUE)` + 최종 `.flatMap(proceed -> ...)` chain 보존. `errorHandler.write` argument byte-identical.

AC-4. 2 filter 의 `getOrder()` byte-unchanged (-1 for JwtHeaderEnrichmentFilter, -2 for AccountTypeValidationFilter).

AC-5. Header 주입 + claim 검증 logic byte-identical:
- `JwtHeaderEnrichmentFilter`: X-User-Id / X-Actor-Id / X-User-Email / X-User-Role / X-Account-Type 5개 header 모두 동일 조건 + 동일 순서 + `resolveRole` 우선순위 (roles list → role string → "")
- `AccountTypeValidationFilter`: `"OPERATOR".equals(accountType)` 조건 + FORBIDDEN("FORBIDDEN", "WMS access requires OPERATOR account") 응답 동일

AC-6. `./gradlew :projects:wms-platform:apps:gateway-service:check --rerun-tasks` 로컬 BUILD SUCCESSFUL.

AC-7. CI 19/20 GREEN authoritative (`Integration (master-service + notification-service, Testcontainers)` + GAP IT + 4 E2E job 포함). FAILURE = 0.

AC-8. cross-service drift 없음 — `projects/wms-platform/apps/` 의 gateway-service 외 다른 6 service + `projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/` + `libs/` 변경 0. (**39회째 zero-retrofit invariant** 검증.)

## Related Specs

- `projects/wms-platform/specs/services/gateway-service/architecture.md` (특히 § JWT Validation, § Package Layout)
- `projects/wms-platform/specs/integration/gap-integration.md`
- `platform/api-gateway-policy.md`
- `platform/refactoring-policy.md` § Allowed Refactoring Categories: Reduce Duplication (Medium risk; 본 task = Low risk because no behavior change risk in 5-line cast extract)
- `rules/common.md` § Architecture Style
- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md`

## Related Contracts

- 본 task 는 contract 변경 0. gateway-service 는 stateless edge — published event 없음.

## Edge Cases

- **No JWT (public path)**: `ReactiveSecurityContextHolder.getContext()` 가 빈 context 또는 anonymous AuthN 반환 시 `JwtAuthenticationToken.class::isInstance` filter 가 통과 안 함 → empty Mono. 2 filter caller 의 `.defaultIfEmpty(...)` 로 fallback 처리 (각각 `exchange` / `Boolean.TRUE`) **보존 의무**. utility 자체는 empty Mono 그대로 반환.
- **`enrich` method signature**: 이미 `enrich(ServerWebExchange exchange, Jwt jwt)` 시그니처 — utility 가 `Mono<Jwt>` 반환하면 `.map(jwt -> enrich(exchange, jwt))` 그대로 가능.
- **`AccountTypeValidationFilter` flatMap body 가 `auth.getToken().getClaimAsString(...)` 사용**: utility 가 `Mono<Jwt>` 직접 반환하면 `.flatMap(jwt -> Mono.just("OPERATOR".equals(jwt.getClaimAsString("account_type"))))` 으로 간소화 가능. 또는 `currentToken()` 사용 + 기존 `auth.getToken()` 보존. 둘 다 동작 동일.
- **Spring 의존성**: `ReactiveSecurityContextHolder`, `JwtAuthenticationToken`, `Jwt` 모두 spring-security-oauth2-resource-server-reactive. 기존 import 그대로.

## Failure Scenarios

- **Empty Mono fallback 누락**: utility 가 `.defaultIfEmpty(...)` 를 utility 내부에 추가하면 caller 의 fallback 의도 무시. utility 는 raw empty Mono 반환 + caller 가 `.defaultIfEmpty` 보존.
- **Filter ordering 변경**: `getOrder()` 가 시스템 전체 filter chain 우선순위 결정 — refactor 가 영향 0 임을 grep 으로 검증.
- **Header 주입 byte-shift**: `enrich(...)` 본문은 unchanged — utility 가 `Jwt` 객체만 전달, header 주입 logic 은 filter 안에 그대로.
- **Test 영향**: 기존 통합 테스트 (`JwtAuthIntegrationTest` 또는 `JwtHeaderEnrichmentFilterTest`) 가 utility 사용으로 깨질 가능성 — utility 가 public static method 라 unit test 에서 mock 안 함. caller 의 통합 동작 그대로 검증.

## Approach Notes

- Refactoring policy § Allowed Refactoring Categories: Extract Method (Low risk; cast chain 5 line extract = no behavior change). 본 task 는 Reduce Duplication 의 mildest form (utility helper extract).
- BE-301 `JwtHelper` (inventory) + BE-300 `ProjectionConsumerSupport` (admin) 의 utility 패턴 답습. wms cluster 안에서 3번째 utility class (3 service × 1 utility = consistent pattern).
- 사용처 명시 의무: utility 의 javadoc 가 "Used by `JwtHeaderEnrichmentFilter` + `AccountTypeValidationFilter` (3 future caller 가능성 = Spring Security ResourceServer 패턴 채택 시)" 단어를 포함하지 않고, 의도성 위주로 작성.
- 단일 finding closure 의 효율: BE-303 (3 finding) + BE-304 (1 finding) 의 pattern 차이 — gateway-service 의 작은 surface (14 file) 가 정상 (Layered + stateless edge).

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + finding 검증)
- 구현 권장=Sonnet 4.6 (utility extract 1 class + 2 caller 5-line replace, mechanical)
