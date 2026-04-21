---
name: validation
description: Backend input validation (DTO + domain)
category: backend
---

# Skill: Validation

Patterns for input validation in Spring Boot services.

Prerequisite: read `specs/platform/coding-rules.md` before using this skill.

---

## Request DTO Validation

Use Jakarta Bean Validation annotations on record fields. Apply `@Valid` in the controller.

```java
public record RegisterProductRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    String name,

    String description,

    @Positive(message = "Price must be positive")
    long price,

    @NotEmpty(message = "At least one variant is required")
    @Valid
    List<RegisterVariantRequest> variants
) {}
```

```java
@PostMapping
public ResponseEntity<ProductResponse> register(@Valid @RequestBody RegisterProductRequest request) {
    // validation errors are caught by GlobalExceptionHandler
}
```

---

## Nested Object Validation

Use `@Valid` on nested objects and collections to cascade validation.

```java
public record RegisterVariantRequest(
    @NotBlank(message = "Option name is required")
    String optionName,

    @PositiveOrZero(message = "Stock must be zero or positive")
    int stock,

    @PositiveOrZero(message = "Additional price must be zero or positive")
    long additionalPrice
) {}
```

---

## Common Annotations

| Annotation | Use For |
|---|---|
| `@NotBlank` | Required strings (rejects null, empty, whitespace) |
| `@NotNull` | Required non-string fields |
| `@NotEmpty` | Required collections (at least one element) |
| `@Size(min, max)` | String length or collection size bounds |
| `@Positive` | Numbers > 0 |
| `@PositiveOrZero` | Numbers >= 0 |
| `@Email` | Email format |
| `@Pattern` | Regex-based string validation |
| `@Valid` | Cascade validation to nested objects |

---

## Domain Validation

Validate business rules in the domain layer, not in DTOs. Throw business exceptions.

```java
public class Order {
    public static Order create(String userId, List<OrderItem> items, long totalPrice) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        if (totalPrice <= 0) {
            throw new InvalidOrderException("Total price must be positive");
        }
        // ...
    }
}
```

**Rules:**
- DTO validation = format/presence checks (annotations).
- Domain validation = business rule checks (code in domain classes).
- Do not duplicate domain rules in DTOs.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Using `@NotNull` for strings | Use `@NotBlank` — it also rejects empty and whitespace |
| Missing `@Valid` on nested objects | Nested validation does not cascade without `@Valid` |
| Business rule checks in controller | Move to domain or application layer |
| Missing `@Valid` on `@RequestBody` | Validation annotations are ignored without it |
| Custom validator for simple range checks | Use built-in `@Min`, `@Max`, `@Positive` instead |
