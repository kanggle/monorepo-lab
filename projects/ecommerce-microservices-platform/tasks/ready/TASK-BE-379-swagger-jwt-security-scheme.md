# TASK-BE-379 — Swagger JWT Bearer security scheme (core live services)

**Status:** ready
**Domain:** ecommerce · **Service:** order / product / payment / user-service · **Type:** backend API documentation (OpenAPI config)

> **auth-service 제외 (실측):** ecommerce `apps/auth-service` 는 `settings.gradle` 에서 빌드 제외됨
> (TASK-BE-132 decommissioned — IAM OIDC 로 대체, 소스만 history 보존). 빌드/배포되지 않으므로 Swagger 설정
> 추가는 무의미 → 본 task 대상에서 제외. 살아있는 auth 표면은 `iam-platform` auth-service (springdoc 미적용, 별도 task).

> **Task number:** BE-379 (global TASK-BE counter; iam-platform holds 376–378, ecommerce's previous was BE-375).

## Goal

각 ecommerce 서비스는 `springdoc-openapi-starter-webmvc-ui:2.7.0` 의존성만 가진 채 **기본 자동설정**으로만
Swagger UI(`/swagger-ui.html`, `/v3/api-docs`)를 노출하고 있다 — 커스텀 `@Bean OpenAPI` 설정 클래스가 0개라
**JWT Bearer 인증 스키마가 없어** Swagger UI 의 "Authorize" 버튼으로 보호된 API 를 토큰과 함께 호출할 수 없다.

핵심 4개 live 서비스(order, product, payment, user)에 `OpenApiConfig` 를 추가해 **HTTP Bearer(JWT) SecurityScheme +
API 메타정보(title/description/version)** 를 노출한다. 이로써 Swagger UI 에서 access token 을 한 번 입력하면
"Try it out" 요청에 `Authorization: Bearer <token>` 헤더가 자동 첨부된다.

## Scope

- 각 서비스의 config 패키지에 `OpenApiConfig` 1개 추가 (config 패키지 위치는 서비스별 기존 컨벤션을 따른다 —
  payment 는 `com.example.payment.config`, order/product/user 는 `...infrastructure.config`).
- `springdoc-openapi-starter-webmvc-ui` 가 끌어오는 `io.swagger.v3.oas.models.*` API 만 사용 — **신규 의존성 0개.**
- `SecurityScheme(type=HTTP, scheme=bearer, bearerFormat=JWT)` + 전역 `SecurityRequirement` + `Info` 메타.
- **순수 문서화 변경** — 런타임 보안 enforcement 무관. order/product/payment/user 4개 서비스는 Spring Security
  미적용 → Swagger UI(`/swagger-ui.html`, `/v3/api-docs`) 기본 개방, SecurityConfig 변경 불필요.

### Out of scope
- gateway-service 통합 문서(GroupedOpenApi aggregation) — 별도 task (사용자 선택상 본 task 제외).
- 나머지 8개 서비스(settlement/shipping/search/review/promotion/notification/batch-worker) — 패턴 확립 후 후속.
- per-endpoint `@SecurityRequirement`/`@Operation` 어노테이션 정교화 — 본 task 는 전역 스키마만.

## Acceptance Criteria
- 4개 서비스(`order/product/payment/user`)의 `:compileJava` GREEN (실측 완료 — BUILD SUCCESSFUL).
- 각 서비스 기동 시 `/v3/api-docs` JSON 에 `components.securitySchemes.bearerAuth` (type=http, scheme=bearer, bearerFormat=JWT) 가 포함된다.
- Swagger UI 우측 상단에 "Authorize" 버튼이 노출되고, 토큰 입력 후 보호된 엔드포인트 "Try it out" 호출에 Bearer 헤더가 첨부된다.
- 기존 테스트 전부 통과 (신규 빈은 자동설정 보강일 뿐 기존 동작 불변).

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/<service>/architecture.md`

## Related Contracts
- 계약 변경 없음 (문서화 전용, 런타임 API 표면 불변).

## Edge Cases
- 공개 엔드포인트도 전역 SecurityRequirement 로 인해 UI 에 자물쇠가 표시되나, springdoc 스키마는 문서화 전용이라
  실제 호출은 토큰 없이도 가능 — 동작 영향 없음. (정교화는 후속 per-endpoint 작업.)

## Failure Scenarios
- `io.swagger.v3.oas.models` import 가 컴파일되지 않으면 springdoc starter 가 누락된 것 — 5개 서비스 모두 `2.7.0` 보유 확인됨.
- 빈 이름 충돌 — 서비스별 고유 빈 메서드명(`<service>OpenAPI`) 사용으로 회피.
