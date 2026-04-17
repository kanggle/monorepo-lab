---
name: fixture-management
description: Test data and fixture management
category: testing
---

# Skill: Fixture Management

Patterns for managing test data and fixtures.

Prerequisite: read `platform/testing-strategy.md` before using this skill.

---

## Fixture Principles

- Each test creates its own data — no shared fixtures across tests.
- Use unique values (UUID, random email) to avoid collisions in integration tests.
- Keep fixtures minimal — only the fields needed for the specific test.

---

## Unit Test Fixtures

Create test data inline or in helper methods within the test class.

```java
class OrderServiceTest {

    private Order createTestOrder(String userId, OrderStatus status) {
        return Order.reconstitute(
            UUID.randomUUID().toString(), userId,
            List.of(createTestItem()), status, 50000L,
            null, Instant.now(), Instant.now(), null, null, null, 0L
        );
    }

    private OrderItem createTestItem() {
        return new OrderItem(UUID.randomUUID().toString(), "Product A",
            UUID.randomUUID().toString(), "Option A", 25000L, 2);
    }
}
```

---

## Integration Test Fixtures

Use unique emails/IDs to avoid cross-test collisions.

```java
@Test
void signup_and_login() {
    String email = "test-" + UUID.randomUUID() + "@example.com";
    String password = "Password1!";

    // signup
    restTemplate.postForEntity("/api/auth/signup",
        new SignupRequest(email, password, "Test User"), Void.class);

    // login
    ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
        "/api/auth/login", new LoginRequest(email, password), LoginResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
}
```

---

## Frontend Test Fixtures

Define mock data objects matching API response types.

```typescript
const mockProduct: ProductDetail = {
  id: 'prod-1',
  name: 'Test Product',
  price: 10000,
  status: 'ACTIVE',
  variants: [{ id: 'var-1', optionName: 'Default', stock: 100, additionalPrice: 0 }],
  createdAt: '2024-01-01T00:00:00Z',
};

const mockProductsResponse: PaginatedResponse<ProductSummary> = {
  content: [mockProduct],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  size: 20,
};
```

---

## Redis Cleanup

For tests that use Redis, clean up keys in `@BeforeEach`.

```java
@BeforeEach
void cleanRedis() {
    Set<String> keys = redisTemplate.keys("session:*");
    if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
    }
}
```

---

## Rules

- Do not use `@Transactional` rollback as a cleanup strategy in integration tests.
- Do not share mutable fixture state between `@Test` methods.
- Use `@BeforeEach` for per-test setup, not `@BeforeAll`.
- Frontend mocks must match `@repo/types` interfaces exactly.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Shared test user across tests | Create unique user per test with `UUID.randomUUID()` |
| Fixture defined in `@BeforeAll` and mutated | Use `@BeforeEach` for mutable fixtures |
| Frontend mock missing required fields | Match the full TypeScript type definition |
| Redis data leaking between tests | Clean up in `@BeforeEach` |
