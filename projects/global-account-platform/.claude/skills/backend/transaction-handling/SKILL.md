---
name: transaction-handling
description: Backend @Transactional usage and boundaries
category: backend
---

# Skill: Transaction Handling

Patterns for `@Transactional` usage in Spring Boot services.

Prerequisite: read `platform/coding-rules.md` before using this skill. See also `database/transaction-boundary/SKILL.md` for layer-level boundary design.

---

## Transaction Boundaries

Transactions are managed at the **application service** layer, not in controllers or repositories.

### Write Operations

```java
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public void processPayment(String orderId, String userId, long amount) {
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            return; // idempotency check
        }
        Payment payment = Payment.create(orderId, userId, amount);
        paymentRepository.save(payment);
    }
}
```

### Read-Only Operations

```java
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResult<OrderSummary> getOrders(String userId, OrderStatus status, PageQuery pageQuery) {
        return orderRepository.findByUserIdAndStatus(userId, status, pageQuery);
    }
}
```

---

## Rules

| Rule | Detail |
|---|---|
| Write service methods | `@Transactional` (default propagation, default isolation) |
| Read-only service methods | `@Transactional(readOnly = true)` |
| Controllers | Never `@Transactional` |
| Repositories | Never `@Transactional` — inherit from the service |
| Event publishing | Must happen within the same transaction (outbox pattern) |

---

## Optimistic Locking

Use `@Version` on JPA entities for concurrent write protection.

```java
@Entity
public class OrderJpaEntity {
    @Version
    private Long version;
}
```

Handle `OptimisticLockingFailureException` at the application service level:

```java
@Transactional
public void cancelOrder(String orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    order.cancel();
    orderRepository.save(order); // throws OptimisticLockingFailureException on conflict
}
```

---

## Transaction + Event Publishing (Outbox)

Write the event to the outbox table within the same transaction as the business operation.

```java
@Transactional
public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
    Order order = Order.create(command);
    orderRepository.save(order);
    outboxPublisher.publish("Order", order.getOrderId(), "OrderPlaced", toPayload(order));
    return PlaceOrderResult.from(order);
}
```

The outbox scheduler polls and publishes to Kafka outside the transaction.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `@Transactional` on private methods | Must be on public methods — Spring proxies cannot intercept private calls |
| Self-invocation (`this.method()`) | Transaction proxy is bypassed — extract to a separate bean |
| `@Transactional` on controller | Move to the application service layer |
| Long-running transactions | Keep transactions short — do external calls outside the transaction |
| Missing `readOnly = true` on queries | Hurts performance — Hibernate skips dirty checking for read-only |
| Event publish outside transaction | Use outbox table to guarantee atomicity |
