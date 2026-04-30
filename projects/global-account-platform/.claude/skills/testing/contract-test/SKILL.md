---
name: contract-test
description: API and event contract testing
category: testing
---

# Skill: Contract Testing

Patterns for verifying API and event contracts between services.

Prerequisite: read `platform/testing-strategy.md` before using this skill.

---

## Purpose

Contract tests verify that:
- API responses match the schema in `specs/contracts/api/`.
- Event payloads match the schema in `specs/contracts/events/`.
- Changes to one service do not break consumers.

---

## API Contract Test

Verify that the controller returns the expected response structure.

```java
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductQueryService productQueryService;

    @Test
    @DisplayName("GET /api/products/{id} 응답이 계약과 일치한다")
    void getProduct_responseMatchesContract() throws Exception {
        given(productQueryService.getProduct("prod-1")).willReturn(mockProductDetail);

        mockMvc.perform(get("/api/products/prod-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").exists())
            .andExpect(jsonPath("$.price").isNumber())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.variants").isArray())
            .andExpect(jsonPath("$.createdAt").exists());
    }
}
```

---

## Event Contract Test

Verify that published events match the expected envelope structure.

```java
@Test
@DisplayName("ProductCreated 이벤트 페이로드가 계약과 일치한다")
void productCreatedEvent_matchesContract() {
    ProductEvent event = ProductEvent.of("ProductCreated", payload);

    assertThat(event.eventId()).isNotNull();
    assertThat(event.eventType()).isEqualTo("ProductCreated");
    assertThat(event.occurredAt()).isNotNull();
    assertThat(event.payload().productId()).isNotNull();
    assertThat(event.payload().name()).isNotBlank();
    assertThat(event.payload().price()).isPositive();
}
```

---

## Consumer Contract Test

Verify that the consumer can deserialize and process events from the producer.

```java
@Test
@DisplayName("OrderPlaced 이벤트를 정상적으로 역직렬화한다")
void deserializeOrderPlacedEvent() throws Exception {
    String json = """
        {
            "eventId": "550e8400-e29b-41d4-a716-446655440000",
            "eventType": "OrderPlaced",
            "occurredAt": "2024-01-01T00:00:00Z",
            "payload": {
                "orderId": "order-1",
                "userId": "user-1",
                "totalPrice": 50000
            }
        }
        """;

    OrderPlacedEvent event = objectMapper.readValue(json, OrderPlacedEvent.class);

    assertThat(event.eventId()).isNotNull();
    assertThat(event.payload().orderId()).isEqualTo("order-1");
    assertThat(event.payload().totalPrice()).isEqualTo(50000);
}
```

---

## When to Write Contract Tests

- When adding a new API endpoint.
- When adding a new event type.
- When modifying an existing response or event schema.
- When a consumer starts consuming a new event from another service.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Testing logic instead of structure | Contract tests verify field presence and types, not business logic |
| Not testing error responses | Verify error response format matches `ErrorResponse` contract |
| Hardcoded dates in assertions | Assert existence (`isNotNull`) not exact values |
| Missing contract test for new event | Every new event type needs a serialization/deserialization test |
