---
name: e2e-test
description: End-to-end integration testing
category: testing
---

# Skill: E2E Testing

Patterns for end-to-end integration tests across services.

Prerequisite: read `platform/testing-strategy.md` before using this skill. No single project spec — concrete flows are declared per project under `specs/services/<service>/architecture.md` § Testing.

---

## Scope

E2E tests verify complete user flows across multiple services:
- API gateway routing
- Service-to-service communication via events
- Full request → response → side-effect chain

---

## Test Infrastructure

Use Docker Compose to run all services and dependencies.

## `@Tag("smoke")` / `@Tag("full")` — mandatory, not optional

Every class extending an e2e base class MUST carry `@Tag("smoke")` or `@Tag("full")` (class-level, or method-level when the class is mixed) **in addition to** the base class's `@Tag("e2e")` umbrella. This is `platform/testing-strategy.md` § E2E Smoke vs Full / § Rules (ADR-MONO-010 D4) — read it before using this skill; this section is a pointer, not a restatement.

**Why this is not optional**: an untagged class is not "ungraded" — it is **silently classified as `full`** (the conservative default), which means it runs **nightly and on push-to-main only, never on a PR**. Write a happy-path smoke test without the tag and it still passes locally and in CI — just never in the fast lane a reviewer is looking at. A regression the test was meant to catch on every PR instead surfaces a day later on `main`, and the PR that broke it merges green in the meantime. **A test that doesn't run on this diff is reported as if it did.**

```java
@Tag("smoke")
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

This example qualifies as `smoke` under the classification rubric (happy path, deterministic `await` under 30s, regression-shaped) — hence `@Tag("smoke")` above. If your test trips any of the `full` triggers (burst/load, container-pause, ≥3 state transitions, cross-project consumption, DLQ/circuit-breaker), tag it `@Tag("full")` instead. See `platform/testing-strategy.md` § Classification rubric for the full S1–S4 / F1–F6 checklist — do not guess; check the list.

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
