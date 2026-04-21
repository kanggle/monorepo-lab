---
name: transaction-boundary
description: Transaction boundary design
category: database
---

# Skill: Transaction Boundary

Patterns for defining transaction boundaries in the persistence layer.

Prerequisite: read `specs/platform/coding-rules.md` before using this skill. See also `backend/transaction-handling/SKILL.md` for code-level `@Transactional` patterns.

---

## Boundary Rules

| Layer | Transaction Responsibility |
|---|---|
| Controller | Never starts transactions |
| Application Service | Owns `@Transactional` — defines the boundary |
| Domain | No transaction awareness |
| Repository | Participates in the caller's transaction |

---

## Single Service Call = Single Transaction

Each application service method is one transaction. Do not split a business operation across multiple transactions.

```java
// Correct: one transaction for the entire operation
@Transactional
public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
    Order order = Order.create(command);
    orderRepository.save(order);
    outboxPublisher.publish("Order", order.getOrderId(), "OrderPlaced", payload);
    return PlaceOrderResult.from(order);
}
```

```java
// Wrong: multiple transactions for one business operation
public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
    saveOrder(command);       // @Transactional — commits
    publishEvent(command);    // @Transactional — separate commit, may fail
}
```

---

## Read vs Write Boundaries

Separate query services from command services. Each gets its own transaction type.

```java
// Write service
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    @Transactional
    public PlaceOrderResult placeOrder(PlaceOrderCommand command) { ... }

    @Transactional
    public void cancelOrder(String orderId) { ... }
}

// Read service
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    @Transactional(readOnly = true)
    public OrderDetail getOrder(String orderId) { ... }

    @Transactional(readOnly = true)
    public PageResult<OrderSummary> getOrders(String userId, PageQuery page) { ... }
}
```

---

## Cross-Service Boundaries

Services do not share transactions. Use eventual consistency via events.

```
Order Service                    Payment Service
┌─────────────────┐             ┌─────────────────┐
│ @Transactional   │             │ @Transactional   │
│ save order       │             │ save payment     │
│ save outbox      │  ──Kafka──▶ │                  │
│ COMMIT           │             │ COMMIT           │
└─────────────────┘             └─────────────────┘
```

---

## Lazy Loading Considerations

All lazy-loaded associations must be accessed within the transaction boundary.

```java
// Correct: access lazy collection inside @Transactional
@Transactional(readOnly = true)
public OrderDetail getOrder(String orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    return OrderDetail.from(order); // accesses order.getItems() inside tx
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| LazyInitializationException | Access lazy associations inside `@Transactional` |
| Two transactions for one business op | Single `@Transactional` on the service method |
| Repository with `@Transactional` | Remove — let the service define the boundary |
| Missing `readOnly = true` on queries | Add it — enables Hibernate optimizations |
