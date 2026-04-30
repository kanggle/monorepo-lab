---
name: exception-handling
description: Backend exception classes and global error handling
category: backend
---

# Skill: Exception Handling

Patterns for exception classes and global error handling in Spring Boot services.

Prerequisite: read `platform/error-handling.md` before using this skill.

---

## Exception Class Pattern

Extend `RuntimeException`. Use a descriptive message. Place in `application/exception/`.

Reference template: [`templates/ExceptionClass.java`](templates/ExceptionClass.java)

**Rules:**
- One exception class per distinct error condition.
- Do not use generic `RuntimeException` or `IllegalStateException` for business errors.
- Exception messages are for logs, not for API responses — the handler maps them to error codes.

---

## Global Exception Handler

Each service has a `@RestControllerAdvice` in `presentation/advice/`.

Reference template: [`templates/GlobalExceptionHandler.java`](templates/GlobalExceptionHandler.java)

---

## ErrorResponse (Shared)

Use the shared `ErrorResponse` record from `libs/java-web`. Do not redeclare per service.

Reference template: [`templates/ErrorResponse.java`](templates/ErrorResponse.java)

---

## Handler Ordering

Order handlers from most specific to least specific:

1. Business exceptions (`InvalidCredentialsException`, `ResourceNotFoundException`)
2. Validation exceptions (`MethodArgumentNotValidException`, `ConstraintViolationException`)
3. Security exceptions (`AccessDeniedException`, `AuthenticationException`)
4. Catch-all (`Exception`)

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Returning exception message directly in response | Map to an error code; use `ErrorResponse.of()` |
| Logging at WARN for unexpected errors | Use `log.error()` with the full stack trace |
| Catching and re-throwing with `new RuntimeException(e)` | Let the global handler catch the original exception |
| Missing handler for validation errors | Always handle `MethodArgumentNotValidException` |
| Missing handler for malformed JSON / wrong field types (e.g. non-UUID string for `UUID` field) | Always handle `HttpMessageNotReadableException` → 400 `VALIDATION_ERROR` with fixed message `"Malformed request body"`. Without it, Jackson failures fall through to the catch-all `Exception` handler and return 500 |
| Echoing Jackson's exception message into the response | Use the fixed `"Malformed request body"` string. The underlying message can leak the offending JSON snippet, internal Java type names (`java.util.UUID`, `InvalidFormatException`), or stack trace fragments |
| Exposing internal details (stack trace, SQL) in response | Return only error code and safe message |
