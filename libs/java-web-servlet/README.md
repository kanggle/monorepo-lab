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
| `com.example.web.idempotency.BodyHashUtil` | Canonical (sorted-keys) JSON → SHA-256 request-body hash + `sha256hex` helper, used by REST Idempotency-Key filters. Round-trips through a module-free `CANONICAL_MAPPER` so a transitive `jackson-module-scala` cannot make `readValue(Object.class)` content-independent (the TASK-BE-342 bug). Project-agnostic — pure Jackson + SHA-256. |
| `com.example.web.idempotency.CachedBodyHttpServletRequestWrapper` | `HttpServletRequest` wrapper that caches the body at construction so both the idempotency filter and `DispatcherServlet` can read it (the servlet input stream is single-read). |

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

TASK-MONO-271 (2026-06-15) added the `com.example.web.idempotency` utilities,
extracting the byte-near-identical private copies that `inbound-service` and
`outbound-service` each carried. The copies had silently diverged (only
`outbound` received the TASK-BE-342 module-free-mapper fix), which is exactly
the drift a single shared implementation prevents.
