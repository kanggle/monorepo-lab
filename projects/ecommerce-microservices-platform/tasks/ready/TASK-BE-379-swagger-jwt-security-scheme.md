# TASK-BE-379 — Swagger JWT Bearer security scheme (10 live services) + gateway aggregated UI

**Status:** ready
**Domain:** ecommerce · **Service:** order / product / payment / user / settlement / shipping / search / review / promotion / notification-service (10) + gateway-service (aggregation) · **Type:** backend API documentation (OpenAPI config)

> **auth-service 제외 (실측):** ecommerce `apps/auth-service` 는 `settings.gradle` 에서 빌드 제외됨
> (TASK-BE-132 decommissioned — IAM OIDC 로 대체, 소스만 history 보존). 빌드/배포되지 않으므로 Swagger 설정
> 추가는 무의미 → 본 task 대상에서 제외. 살아있는 auth 표면은 `iam-platform` auth-service (springdoc 미적용, 별도 task).

> **Task number:** BE-379 (global TASK-BE counter; iam-platform holds 376–378, ecommerce's previous was BE-375).

## Goal

각 ecommerce 서비스는 `springdoc-openapi-starter-webmvc-ui:2.7.0` 의존성만 가진 채 **기본 자동설정**으로만
Swagger UI(`/swagger-ui.html`, `/v3/api-docs`)를 노출하고 있다 — 커스텀 `@Bean OpenAPI` 설정 클래스가 0개라
**JWT Bearer 인증 스키마가 없어** Swagger UI 의 "Authorize" 버튼으로 보호된 API 를 토큰과 함께 호출할 수 없다.

HTTP 컨트롤러를 가진 **live 서비스 10개**(order, product, payment, user, settlement, shipping, search, review,
promotion, notification)에 `OpenApiConfig` 를 추가해 **HTTP Bearer(JWT) SecurityScheme + API 메타정보
(title/description/version)** 를 노출한다. 이로써 Swagger UI 에서 access token 을 한 번 입력하면
"Try it out" 요청에 `Authorization: Bearer <token>` 헤더가 자동 첨부된다.

**batch-worker 제외 (실측):** `@RestController`/`@Controller` 0개 (Kafka consumer 전용 worker) → 노출할 HTTP API 가
없어 Swagger 설정 무의미. springdoc 의존성은 보유하나 본 task 대상 아님.

## Scope

- 각 서비스의 config 패키지에 `OpenApiConfig` 1개 추가 (config 패키지 위치는 서비스별 기존 컨벤션을 따른다 —
  payment·search 는 `com.example.<svc>.config`, notification 은 신규 `com.example.notification.config`,
  나머지(order/product/user/settlement/shipping/review/promotion)는 `...infrastructure.config`).
- `springdoc-openapi-starter-webmvc-ui` 가 끌어오는 `io.swagger.v3.oas.models.*` API 만 사용 — **신규 의존성 0개.**
- `SecurityScheme(type=HTTP, scheme=bearer, bearerFormat=JWT)` + 전역 `SecurityRequirement` + `Info` 메타.
- **순수 문서화 변경** — 런타임 보안 enforcement 무관. 10개 서비스 모두 Spring Security 미적용 → Swagger UI
  (`/swagger-ui.html`, `/v3/api-docs`) 기본 개방, SecurityConfig 변경 불필요.

### Gateway aggregated Swagger UI (dev/demo-gated)
단일 진입점(`http://<gateway>/swagger-ui.html`)에서 10개 서비스 문서를 드롭다운으로 묶는다. gateway 는 Spring Cloud
Gateway(WebFlux reactive) 이므로 **webflux** starter 사용.
- `springdoc-openapi-starter-webflux-ui:2.7.0` 추가 (gateway build.gradle).
- **기본 OFF (prod-safe)** — `gateway.swagger-aggregation.enabled` (env `GATEWAY_SWAGGER_AGGREGATION_ENABLED`,
  기본 `false`) 로 게이트. 이 포트폴리오는 docker-compose / k8s 가 모두 `SPRING_PROFILES_ACTIVE=prod` 로 기동해
  별도 dev 프로파일이 없으므로, 프로파일 대신 **명시적 enable 플래그**로 "기본 off + 데모 opt-in" 을 실현.
- `SwaggerAggregationConfig` (`@ConditionalOnProperty`) — flag ON 일 때만 `/api-docs/<svc>` → 다운스트림
  `/v3/api-docs` 프록시 라우트 10개를 등록(`RouteLocator` bean, YAML `/api/**` 라우트에 가산).
- `application.yml` — `springdoc.api-docs/swagger-ui.enabled` 를 flag 에 바인딩(OFF 시 UI/endpoint 미서빙) +
  `springdoc.swagger-ui.urls` 드롭다운 10개.
- `SecurityConfig.PUBLIC_PATHS` 에 `/swagger-ui**`, `/v3/api-docs/**`, `/webjars/**`, `/api-docs/**` permit
  추가 — 항상 permit 이나 flag OFF 시 springdoc 비활성 + 프록시 라우트 미등록이라 prod 노출 0 (no handler / no route → 404).
- 데모 활성화: 게이트웨이 컨테이너에 `GATEWAY_SWAGGER_AGGREGATION_ENABLED=true` (미커밋 데모 overlay 에 1줄).

### Out of scope
- ecommerce auth-service(빌드 제외) · batch-worker(HTTP 컨트롤러 없음) — 위 박스 참조.
- iam-platform auth-service — 별도 프로젝트, springdoc 의존성 미보유 → 별건 task.
- per-endpoint `@SecurityRequirement`/`@Operation` 어노테이션 정교화 — 본 task 는 전역 스키마만.

## Acceptance Criteria
- 10개 서비스의 `:compileJava` GREEN (실측 완료 — BUILD SUCCESSFUL, order/product/payment/user + settlement/shipping/search/review/promotion/notification).
- gateway-service `:check` GREEN (실측 완료 — 컴파일 + 유닛 테스트; Docker 차단으로 `@Tag("integration")` 은 CI Linux 권위).
- flag OFF(기본) 기동 시 `/swagger-ui.html` 404 (prod 안전); flag ON 시 통합 UI 드롭다운에서 10개 서비스 문서 렌더 +
  Authorize 토큰 입력 → "Try it out" Bearer 첨부 (런타임 검증은 데모 스택 gated — 단독 기동은 OIDC issuer 디스커버리 의존).
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
- `io.swagger.v3.oas.models` import 가 컴파일되지 않으면 springdoc starter 가 누락된 것 — 10개 서비스 모두 `2.7.0` 보유 확인됨.
- 빈 이름 충돌 — 서비스별 고유 빈 메서드명(`<service>OpenAPI`) 사용으로 회피.
