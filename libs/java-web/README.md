# libs/java-web

Framework-agnostic web utilities. Safe to depend on from **both** servlet
(Spring MVC) and reactive (WebFlux / Spring Cloud Gateway) services.

## Hosted classes

| Class | Purpose |
| --- | --- |
| `com.example.web.dto.ErrorResponse` | Plain record (`code`, `message`, `timestamp`) used as the JSON error envelope across all services. |
| `com.example.web.exception.AccessDeniedException` | Plain `RuntimeException` carrying a 403-equivalent semantic. Thrown by application code; mapped to HTTP by per-service exception handlers. |

## Allowed dependencies

- `com.fasterxml.jackson.core:jackson-databind` (for `ErrorResponse` JSON serialization)
- JUnit / AssertJ / Mockito (test only)

## Forbidden dependencies

The following must **NOT** be added here. Any servlet-stack helper belongs in
[`libs/java-web-servlet`](../java-web-servlet/) instead.

- `org.springframework:spring-web` / `spring-webmvc` / `spring-orm`
- `jakarta.servlet:jakarta.servlet-api`
- `org.springframework.boot:spring-boot-starter-web`

## Why the split exists

Before TASK-MONO-044a (2026-05-05), this module hosted
`CommonGlobalExceptionHandler` and pulled in `spring-web`/`spring-webmvc`/
`spring-orm`/`jakarta.servlet-api` as `implementation` dependencies. Reactive
gateway-service consumers (`Spring Cloud Gateway`) inherit those transitively,
which puts servlet API on their classpath. Spring Boot's
`SpringApplication.deduceFromClasspath()` then chose `WebApplicationType.SERVLET`
even though the app is reactive, breaking
`@ConditionalOnWebApplication(type = REACTIVE)` autoconfig and triggering
`BeanDefinitionOverrideException` (`conversionServicePostProcessor` registered
twice).

Splitting the servlet helpers out of this module restores reactive gateway
boot. Reactive consumers depend on `libs:java-web` only; servlet consumers
that want the shared `@ControllerAdvice` base add `libs:java-web-servlet`
alongside `libs:java-web`.

## Consumers

Used by every service in the monorepo (29 `build.gradle` files reference it).
Both servlet and reactive consumers depend on this module — it's the
framework-agnostic baseline.
