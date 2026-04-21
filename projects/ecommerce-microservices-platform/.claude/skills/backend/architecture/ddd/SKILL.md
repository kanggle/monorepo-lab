---
name: ddd
description: Implement DDD Architecture service
category: backend
---

# Skill: DDD Architecture Implementation

Spring Boot implementation patterns for services using DDD-style Architecture.

Prerequisite: read `specs/services/<service>/architecture.md` before using this skill.

---

## Package Structure

```
com.example.{service}/
├── interfaces/
│   ├── rest/
│   │   ├── controller/       # REST controllers
│   │   └── dto/
│   │       ├── request/
│   │       └── response/
│   └── event/                # Inbound event handlers (if any)
├── application/
│   ├── service/              # Application services (use-case orchestration)
│   ├── command/              # Input records
│   ├── result/               # Output records
│   └── port/                 # Outbound ports (optional, if infra abstraction needed)
├── domain/
│   ├── {aggregate}/          # One package per aggregate root
│   │   ├── {AggregateRoot}.java
│   │   ├── {Entity}.java
│   │   ├── {ValueObject}.java
│   │   └── {AggregateRoot}Repository.java
│   ├── event/                # Domain events
│   └── service/              # Domain services (cross-aggregate rules)
└── infrastructure/
    ├── persistence/
    │   ├── entity/            # JPA entities (if separate from domain model)
    │   ├── repository/        # Spring Data implementations
    │   └── mapper/            # Domain <-> JPA entity mappers (if separate)
    ├── event/                 # Event publishing adapters
    └── config/
```

Packages are organized by aggregate, not by technical role. Each aggregate package is self-contained.

---

## Aggregate Root

Aggregate root is the only entry point for modifying the aggregate. All invariants are enforced here.

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AbstractAggregateRoot<Order> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderLine> lines = new ArrayList<>();

    @Embedded
    private Money totalAmount;

    public static Order create(UUID customerId, List<OrderLineCommand> lineCommands) {
        if (lineCommands.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line");
        }
        Order order = new Order();
        order.customerId = customerId;
        order.status = OrderStatus.CREATED;
        lineCommands.forEach(cmd -> order.addLine(cmd.productId(), cmd.quantity(), cmd.unitPrice()));
        order.recalculateTotal();
        order.registerEvent(new OrderCreatedEvent(order.id, order.customerId, order.totalAmount));
        return order;
    }

    public void confirm() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("Only CREATED orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
        registerEvent(new OrderConfirmedEvent(this.id));
    }

    private void addLine(UUID productId, int quantity, Money unitPrice) {
        this.lines.add(new OrderLine(productId, quantity, unitPrice));
    }

    private void recalculateTotal() {
        this.totalAmount = lines.stream()
            .map(OrderLine::subtotal)
            .reduce(Money.ZERO, Money::add);
    }
}
```

Rules:
- Use `AbstractAggregateRoot<T>` from Spring Data for domain event registration
- Static factory method for creation — constructor is `protected`
- State transitions validate preconditions and register domain events
- No public setters — state changes only through behavior methods
- Child entities are modified only through the aggregate root

---

## Entity (Non-Root)

Entities inside an aggregate are accessed only through the aggregate root.

```java
@Entity
@Table(name = "order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price"))
    private Money unitPrice;

    OrderLine(UUID productId, int quantity, Money unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
```

Rules:
- Package-private or protected constructor — only aggregate root creates them
- Validates own invariants in constructor

---

## Value Object

Immutable, identity-less, equality by value.

```java
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    @Column(nullable = false)
    private BigDecimal amount;

    public Money(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
        this.amount = amount;
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }
}
```

Rules:
- `@Embeddable` for JPA persistence
- All operations return new instances (immutable)
- Override `equals`/`hashCode` based on value

---

## Domain Event

```java
public record OrderCreatedEvent(
    UUID orderId,
    UUID customerId,
    Money totalAmount
) {}
```

Domain events are registered via `registerEvent()` in the aggregate root. Spring Data publishes them automatically when the aggregate is saved through the repository.

For outbound event publishing (to Kafka/messaging), infrastructure layer listens to domain events and converts them to contract-compliant messages:

```java
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreatedEvent event) {
        OrderCreatedMessage message = new OrderCreatedMessage(
            event.orderId(), event.customerId(), event.totalAmount().getAmount());
        kafkaTemplate.send("order.created", event.orderId().toString(), message);
    }
}
```

---

## Repository

Domain layer defines the interface. Infrastructure layer implements it.

```java
// Domain layer
public interface OrderRepository {
    Optional<Order> findById(UUID id);
    Order save(Order order);
}

// Infrastructure layer
public interface JpaOrderRepository extends JpaRepository<Order, UUID>, OrderRepository {
}
```

Rules:
- Repository interface is per aggregate root, not per entity
- Domain layer defines only the operations the domain needs
- No `findAll()` or bulk operations unless the domain requires them

---

## Domain Service

For business rules that span multiple aggregates or don't belong to a single aggregate.

```java
@RequiredArgsConstructor
public class OrderPricingService {

    private final DiscountPolicy discountPolicy;

    public Money calculateFinalPrice(Order order, Customer customer) {
        Money baseTotal = order.getTotalAmount();
        BigDecimal discountRate = discountPolicy.resolveDiscount(customer.tier(), baseTotal);
        return baseTotal.multiply(BigDecimal.ONE.subtract(discountRate));
    }
}
```

Rules:
- No Spring annotations — this is a pure domain class (registered as `@Bean` in infrastructure config)
- No framework or persistence dependencies
- Use only when logic genuinely doesn't belong to a single aggregate

---

## Application Service

Orchestrates use-cases. Thin layer — delegates business decisions to domain.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCommandService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderResult createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.customerId(), command.lines());
        Order saved = orderRepository.save(order);
        return OrderResult.from(saved);
    }

    @Transactional
    public OrderResult confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.confirm();
        return OrderResult.from(order);
    }
}
```

Rules:
- `@Transactional` boundary is here
- Does NOT contain business rules — calls aggregate methods instead
- Loads aggregate, calls behavior, saves — that's it

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Business rule in application service | Move to aggregate root or domain service |
| Public setter on aggregate root | Replace with behavior method that validates invariants |
| Repository per entity (not aggregate) | One repository per aggregate root only |
| Domain event published directly from application service | Use `registerEvent()` in aggregate, let Spring Data publish |
| Aggregate root modified by another aggregate directly | Use application service to coordinate, or domain events for eventual consistency |
| Value object with mutable state | Return new instance from every operation |
| Domain model depends on DTO classes | Domain returns itself; application layer maps to Result records |
