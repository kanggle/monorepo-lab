---
name: refactoring
description: Backend refactoring patterns
category: backend
---

# Skill: Backend Refactoring

Refactoring patterns for Java 21 / Spring Boot backend services in this repository.

Prerequisite: read `specs/platform/refactoring-policy.md` before using this skill.

---

## Baseline Check

Before any refactoring, always run:

```bash
./gradlew :apps:<service>:test
```

If tests fail, fix them first. Never refactor on a red baseline.

---

## Extract Method

When a method exceeds ~30 lines or has comments separating logical blocks.

```java
// Before
public OrderResult placeOrder(PlaceOrderCommand cmd) {
    // validate
    if (cmd.items().isEmpty()) throw new InvalidOrderException("empty items");
    if (cmd.items().size() > 100) throw new InvalidOrderException("too many items");

    // calculate
    Money total = Money.ZERO;
    for (var item : cmd.items()) {
        total = total.add(item.price().multiply(item.quantity()));
    }

    // save
    var order = Order.create(cmd.userId(), cmd.items(), total);
    return orderRepository.save(order).toResult();
}

// After
public OrderResult placeOrder(PlaceOrderCommand cmd) {
    validateItems(cmd.items());
    Money total = calculateTotal(cmd.items());
    var order = Order.create(cmd.userId(), cmd.items(), total);
    return orderRepository.save(order).toResult();
}

private void validateItems(List<OrderItem> items) {
    if (items.isEmpty()) throw new InvalidOrderException("empty items");
    if (items.size() > 100) throw new InvalidOrderException("too many items");
}

private Money calculateTotal(List<OrderItem> items) {
    Money total = Money.ZERO;
    for (var item : items) {
        total = total.add(item.price().multiply(item.quantity()));
    }
    return total;
}
```

---

## Move to Correct Layer

When domain logic lives in the wrong layer. Check `specs/services/<service>/architecture.md` for layer definitions.

```java
// Before: business rule in controller (wrong layer)
@PostMapping("/orders")
public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest req) {
    if (req.items().stream().anyMatch(i -> i.quantity() <= 0)) {
        throw new InvalidOrderException("quantity must be positive");
    }
    // ...
}

// After: business rule moved to domain/service layer
// Controller — only delegates
@PostMapping("/orders")
public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest req) {
    var result = orderService.placeOrder(req.toCommand());
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(result));
}

// Service or Domain — owns the rule
public OrderResult placeOrder(PlaceOrderCommand cmd) {
    validatePositiveQuantities(cmd.items());
    // ...
}
```

---

## Remove Dead Code

Identify and remove:
- Unused private methods (no callers)
- Unused imports
- Unused fields or local variables
- Commented-out code blocks (per `coding-rules.md`: no commented-out code in production)
- Classes with no references

**Verification**: after removal, compile and run tests. If anything breaks, the code was not dead — revert.

---

## Reduce Duplication

When the same logic appears in 3+ places, extract it.

```java
// Before: duplicated validation in multiple services
// In OrderService
if (userId == null || userId.isBlank()) throw new InvalidUserException("userId required");

// In PaymentService
if (userId == null || userId.isBlank()) throw new InvalidUserException("userId required");

// After: extracted to shared validation in the same service's package
// NOT in libs/ — keep in the service unless truly cross-service
private void requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
        throw new InvalidUserException("userId required");
    }
}
```

**Warning**: Do not extract to `libs/` unless the logic is genuinely shared across multiple services. See `specs/platform/shared-library-policy.md`.

---

## Simplify Conditional

Replace nested if/else with guard clauses or pattern matching.

```java
// Before: deeply nested
public Discount calculateDiscount(Order order) {
    if (order != null) {
        if (order.isVip()) {
            if (order.total().isGreaterThan(Money.of(100000))) {
                return Discount.of(15);
            } else {
                return Discount.of(10);
            }
        } else {
            return Discount.of(0);
        }
    }
    throw new IllegalArgumentException("order is null");
}

// After: guard clauses + flat structure
public Discount calculateDiscount(Order order) {
    if (order == null) throw new IllegalArgumentException("order is null");
    if (!order.isVip()) return Discount.of(0);
    if (order.total().isGreaterThan(Money.of(100000))) return Discount.of(15);
    return Discount.of(10);
}
```

Java 21 pattern matching:

```java
// Before
if (event instanceof OrderCreated) {
    handleCreated((OrderCreated) event);
} else if (event instanceof OrderCancelled) {
    handleCancelled((OrderCancelled) event);
}

// After
switch (event) {
    case OrderCreated e -> handleCreated(e);
    case OrderCancelled e -> handleCancelled(e);
    default -> throw new UnsupportedEventException(event.getClass().getSimpleName());
}
```

---

## Replace Pattern (Architecture Alignment)

When code uses a pattern that does not match the declared architecture.

Example: service declared as Layered Architecture but has hexagonal-style port interfaces.

```java
// Before: port interface in a layered service (wrong pattern)
public interface OrderPort {
    OrderResult placeOrder(PlaceOrderCommand cmd);
}

// After: direct service class (layered pattern)
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderResult placeOrder(PlaceOrderCommand cmd) {
        // ...
    }
}
```

Always check `specs/services/<service>/architecture.md` to confirm the correct pattern.

---

## Anti-patterns to Avoid

| Anti-pattern | Why |
|---|---|
| Refactoring without tests | No safety net — behavior change undetectable |
| Mixing refactoring with feature work | Makes rollback impossible, obscures intent |
| Premature abstraction | Extracting shared code for 1-2 usages adds complexity without value |
| Moving domain logic to `libs/` | Violates shared library policy |
| Renaming public API fields | This is a contract change, not refactoring |
| Changing test assertions during refactoring | Tests verify behavior is unchanged — changing assertions defeats the purpose |

---

## Post-refactoring Checklist

- [ ] All tests pass without test logic changes
- [ ] No new compiler warnings
- [ ] Architecture layer direction correct
- [ ] Naming follows `naming-conventions.md`
- [ ] No dead code left behind
- [ ] Commit message starts with `refactor(<service>):`
