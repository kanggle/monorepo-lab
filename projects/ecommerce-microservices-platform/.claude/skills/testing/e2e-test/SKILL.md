---
name: e2e-test
description: End-to-end integration testing
category: testing
---

# Skill: E2E Testing

Patterns for end-to-end integration tests across services.

Prerequisite: read `specs/platform/testing-strategy.md` before using this skill.

---

## Scope

E2E tests verify complete user flows across multiple services:
- API gateway routing
- Service-to-service communication via events
- Full request → response → side-effect chain

---

## Test Infrastructure

Use Docker Compose to run all services and dependencies.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class OrderFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("주문 생성 → 결제 처리 → 상태 변경 전체 흐름")
    void orderFlow_placeToPayment() {
        // 1. Create order
        ResponseEntity<OrderResponse> orderResponse = restTemplate.postForEntity(
            "/api/orders", orderRequest, OrderResponse.class);
        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = orderResponse.getBody().id();

        // 2. Wait for payment processing (async via Kafka)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<OrderResponse> result = restTemplate.getForEntity(
                "/api/orders/" + orderId, OrderResponse.class);
            assertThat(result.getBody().status()).isEqualTo("PAYMENT_COMPLETED");
        });
    }
}
```

---

## E2E vs Integration Test

| Aspect | Integration Test | E2E Test |
|---|---|---|
| Scope | Single service + its DB | Multiple services |
| Infrastructure | Testcontainers | Docker Compose (full stack) |
| Speed | Fast (seconds) | Slow (minutes) |
| When to use | Every task | Critical flows only |

---

## What Flows to Cover

- User signup → login → token refresh
- Product creation → search index sync
- Order placement → payment → status update
- Order cancellation → refund

---

## Waiting for Async Operations

Use Awaitility for event-driven flows:

```java
await()
    .atMost(Duration.ofSeconds(15))
    .pollInterval(Duration.ofSeconds(1))
    .untilAsserted(() -> {
        // assert the expected side effect
    });
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Asserting immediately after async operation | Use `await()` for event-driven flows |
| Too many E2E tests | Keep minimal — use integration tests for single-service logic |
| Flaky due to timing | Use generous timeouts and `pollInterval` |
| Test data leaking between tests | Use unique IDs per test, clean up after |
