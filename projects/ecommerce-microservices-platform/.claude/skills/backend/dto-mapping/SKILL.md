---
name: dto-mapping
description: Backend DTO/entity mapping between layers
category: backend
---

# Skill: DTO Mapping

Patterns for mapping between layers in Spring Boot services.

Prerequisite: read `specs/platform/coding-rules.md` before using this skill.

---

## Mapping Directions

```
Request DTO  →  Command       (controller → application)
Domain       →  Result        (application → controller)
Result       →  Response DTO  (controller layer)
Domain       ↔  JPA Entity    (infrastructure ↔ domain)
```

---

## Request → Command

Use `toCommand()` factory method on the request DTO.

```java
public record RegisterProductRequest(
    @NotBlank String name,
    @Positive long price,
    @NotEmpty @Valid List<RegisterVariantRequest> variants
) {
    public RegisterProductCommand toCommand() {
        List<VariantCommand> variantCommands = variants.stream()
            .map(v -> new VariantCommand(v.optionName(), v.stock(), v.additionalPrice()))
            .toList();
        return new RegisterProductCommand(name, description, price, categoryId, variantCommands);
    }
}
```

---

## Result → Response

Use `from()` static factory method on the response DTO.

```java
public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }
}
```

---

## Domain ↔ JPA Entity

Use a `@Component` mapper class in `infrastructure/persistence/`.

```java
@Component
class OrderJpaMapper {

    Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
            .map(this::toOrderItem)
            .toList();
        return Order.reconstitute(
            entity.getOrderId(), entity.getUserId(), items,
            entity.getStatus(), entity.getTotalPrice(),
            entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion()
        );
    }

    OrderJpaEntity toEntity(Order order) {
        return OrderJpaEntity.fromDomain(order);
    }
}
```

---

## Rules

- No mapping library (MapStruct, ModelMapper) — use manual mapping.
- `toCommand()` lives on the Request DTO (presentation → application direction).
- `from()` lives on the Response DTO (application → presentation direction).
- JPA mappers are `@Component` classes in the infrastructure layer.
- Domain entities use `reconstitute()` for rebuild from persistence, not public constructors.
- Do not pass JPA entities across layer boundaries.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Returning JPA entity from service | Map to a Result record in the application layer |
| Mapping logic in the controller | Put `toCommand()` on request DTO, `from()` on response DTO |
| Using a mapping library for simple records | Manual mapping is preferred in this codebase |
| Domain entity with public setters for mapping | Use `reconstitute()` factory method or builder |
