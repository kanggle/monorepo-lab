---
name: testing-backend
description: Backend test writing
category: backend
---

# Skill: Backend Testing

Testing patterns for Spring Boot backend services in this repository.

Prerequisite: read `platform/testing-strategy.md` before using this skill.

---

## Test Types

For test type definitions and required coverage, see `platform/testing-strategy.md`.

---

## Unit Tests (Service / Domain)

Use `@ExtendWith(MockitoExtension.class)`. No Spring context.

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService 단위 테스트")
class LoginServiceTest {

    @InjectMocks
    private LoginService loginService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenGenerator tokenGenerator;
}
```

**Mockito STRICT_STUBS rules:**
- Every `given(...)` stub must be used by at least one test method.
- Unused stubs cause `UnnecessaryStubbingException` at runtime.
- Do not add stubs "just in case". Add them only in the test that needs them.
- When return types change (e.g., `String` → `SomeResult`), update the stub return value in every affected test at the same time.

**Stub return types must match the interface:**
```java
// Correct: matches RegistrationResult return type
given(sessionRegistry.registerSession(eq(userId), anyString(), eq(604800L)))
    .willReturn(new RegistrationResult("new-hash", null));

// Wrong: returns null for a record-returning method
given(sessionRegistry.registerSession(...)).willReturn(null); // compiles but semantically wrong
```

---

## Controller Slice Tests

Use `@WebMvcTest` with `MockMvc`. Always import `SecurityConfig` and `GlobalExceptionHandler`.

```java
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("AuthController 슬라이스 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @Test
    void login_validRequest_returns200() throws Exception {
        given(loginService.login(any())).willReturn(new LoginResult("token", "refresh", 3600L));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password1!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("token"));
    }
}
```

---

## Integration Tests (Testcontainers)

Use `@SpringBootTest` + `@Testcontainers`. Use real PostgreSQL and Redis, never H2.

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class AuthSignupLoginIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("auth_db")
        .withUsername("auth_user")
        .withPassword("auth_pass");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> "integration-test-secret-key-min-32chars!!");
    }
}
```

**Data isolation rules:**
- Use `UUID.randomUUID()` or unique emails per test to avoid cross-test collisions.
- Clean up test-specific Redis keys in `@BeforeEach` using `redisTemplate.delete(key)`.
- Do not rely on `@Transactional` rollback in integration tests — use real cleanup or unique data.

---

## Infrastructure Unit Tests (e.g., Redis adapter)

When testing an infrastructure class directly (without Spring context), use `@ExtendWith(MockitoExtension.class)` and mock the template/ops.

```java
@ExtendWith(MockitoExtension.class)
class RedisUserSessionRegistryUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SessionProperties sessionProperties;

    @Mock
    private ZSetOperations<String, String> zSetOps;
}
```

Do not duplicate utility logic from the implementation in tests. Use the same utility class (e.g., `TokenKeyHasher.sha256Hex()`).

---

## Test Method Naming

Pattern: `{scenario}_{condition}_{expectedResult}`

```java
void registerSession_addsSessionAndReturnsNoEviction()
void login_emailNotFound_throws()
void login_wrongPassword_throws()
void rotateSession_replacesOldWithNew()
```

---

## @DisplayName

Use Korean display names to describe the business behavior being tested.

```java
@DisplayName("최대 세션 수 초과 시 가장 오래된 세션이 제거된다")
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Unused stub → `UnnecessaryStubbingException` | Remove the stub or move it to the test that uses it |
| `given(...).willReturn(null)` for a record return type | Return the actual record with appropriate field values |
| Private helper in test that duplicates production code | Use the production utility class directly |
| Sharing mutable state between `@Test` methods | Initialize state in `@BeforeEach` |
| Using H2 for persistence integration tests | Use Testcontainers with `postgres:16-alpine` |
| Asserting on wrong field after return type change | Update all stubs and assertions together when changing return types |
