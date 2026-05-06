# libs/java-web-servlet

Servlet-stack web utilities (Spring MVC). **Do NOT depend on this module from
reactive services** (Spring Cloud Gateway / WebFlux) — doing so puts
`jakarta.servlet-api` + `spring-webmvc` on the reactive classpath and Spring
Boot's `WebApplicationType` deduction will fall back to `SERVLET`, breaking
`@ConditionalOnWebApplication(type = REACTIVE)` autoconfig and producing
`BeanDefinitionOverrideException` at boot.

## Hosted classes

| Class | Purpose |
| --- | --- |
| `com.example.web.exception.CommonGlobalExceptionHandler` | Abstract `@ControllerAdvice` base providing default handlers for `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MissingRequestHeaderException`, `MissingServletRequestParameterException`, `IllegalArgumentException`, `ObjectOptimisticLockingFailureException`, and a fallback `Exception` handler. Subclasses (per service) annotate themselves `@RestControllerAdvice` and add service-specific `@ExceptionHandler` methods. |

## Dependencies

- `libs:java-web` (for `ErrorResponse` DTO returned by handlers)
- `org.springframework:spring-web` / `spring-webmvc` / `spring-orm`
- `jakarta.servlet:jakarta.servlet-api`
- `com.fasterxml.jackson.core:jackson-databind`

## Consumer wiring

Servlet services that want the shared `@ControllerAdvice` base extend
`CommonGlobalExceptionHandler` and add `implementation
project(':libs:java-web-servlet')` alongside the existing
`implementation project(':libs:java-web')`.

Servlet services that use `ErrorResponse` / `AccessDeniedException` but do
**not** extend `CommonGlobalExceptionHandler` keep `libs:java-web` only —
they don't need this module. Consumers track this dependency in their own
service-level Gradle build files; the canonical list lives there, not here
(library files stay project-agnostic per `TEMPLATE.md § Library vs Project
Boundary`).

## History

Split out of `libs/java-web` by TASK-MONO-044a (2026-05-05) after a regression
that leaked servlet API onto a reactive gateway classpath. See
`tasks/done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md` and the
incident report under `knowledge/incidents/` for details.
