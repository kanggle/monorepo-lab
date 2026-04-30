---
name: springboot-api
description: Implement Spring Boot REST API
category: backend
---

# Skill: Spring Boot API Implementation

Patterns for implementing REST API endpoints in this repository.

Prerequisite: read `specs/services/<service>/architecture.md` before using this skill.

---

## Layer Rules

For layer responsibilities and dependency directions, see `specs/services/<service>/architecture.md`.

---

## Code Patterns

### Controller → Application Service

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        LoginCommand command = new LoginCommand(
            request.email(), request.password(),
            httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        LoginResult result = loginService.login(command);
        return ResponseEntity.ok(LoginResponse.from(result));
    }
}
```

### Infrastructure Interface Pattern

If the application layer needs a value that only the infrastructure layer can compute, the interface must return it as part of a result record — not expose the utility method.

```java
// Correct: domain interface returns the computed value
public interface UserSessionRegistry {
    record RegistrationResult(String newSessionId, String evictedSessionId) {}
    RegistrationResult registerSession(UUID userId, String refreshToken, long ttlSeconds);
}

// Wrong: application imports infrastructure utility
import com.example.auth.infrastructure.util.TokenKeyHasher; // FORBIDDEN
```

---

## Command / Result Pattern

Use dedicated records for application layer input/output. Do not pass domain entities across layer boundaries.

```java
// Input: Command record (application layer)
public record LoginCommand(String email, String password, String ipAddress, String userAgent) {}

// Output: Result record (application layer)
public record LoginResult(String accessToken, String refreshToken, long expiresIn) {}
```

---

## Dependency Directions

Follow the allowed dependency directions declared in `specs/services/<service>/architecture.md`.

---

## Command / Result Naming

| Type | Pattern | Example |
|---|---|---|
| Command (input DTO) | `{UseCase}Command` | `LoginCommand` |
| Result (output DTO) | `{UseCase}Result` | `LoginResult` |
| Request (HTTP body) | `{UseCase}Request` | `LoginRequest` |
| Response (HTTP body) | `{UseCase}Response` | `LoginResponse` |

For other naming conventions, follow `platform/naming-conventions.md`.

---

## Response HTTP Status

Follow `platform/error-handling.md` for HTTP status codes and error response format.
