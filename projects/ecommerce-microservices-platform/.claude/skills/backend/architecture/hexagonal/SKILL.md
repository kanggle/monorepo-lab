---
name: hexagonal
description: Implement Hexagonal Architecture service
category: backend
---

# Skill: Hexagonal Architecture Implementation

Spring Boot implementation patterns for services using Hexagonal (Ports and Adapters) Architecture.

Prerequisite: read `specs/services/<service>/architecture.md` before using this skill.

---

## Package Structure

```
com.example.{service}/
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/    # REST controllers (inbound adapter)
│   │   │   └── dto/
│   │   │       ├── request/
│   │   │       └── response/
│   │   └── event/             # Message consumer adapters (inbound adapter)
│   └── out/
│       ├── persistence/       # DB adapter (outbound adapter)
│       │   ├── entity/        # JPA entities
│       │   ├── repository/    # Spring Data repositories
│       │   └── mapper/        # Domain <-> JPA entity mappers
│       ├── external/          # External API adapters (outbound adapter)
│       └── event/             # Event publishing adapters (outbound adapter)
├── application/
│   ├── port/
│   │   ├── in/                # Inbound ports (use-case interfaces)
│   │   └── out/               # Outbound ports (driven interfaces)
│   ├── service/               # Use-case implementations
│   ├── command/               # Input records
│   └── result/                # Output records
├── domain/
│   ├── model/                 # Domain models (POJO, not JPA entities)
│   ├── event/                 # Domain events
│   └── service/               # Domain services
└── config/                    # Spring configuration, bean wiring
```

The key distinction: domain models are **pure POJOs** — JPA entities live in the persistence adapter. A mapper converts between them.

---

## Inbound Port (Use-Case Interface)

Defines what the application can do. Inbound adapters depend on this.

```java
public interface ProcessPaymentUseCase {
    PaymentResult processPayment(ProcessPaymentCommand command);
}

public interface GetPaymentStatusUseCase {
    PaymentStatusResult getStatus(UUID paymentId);
}
```

Rules:
- One interface per use-case (or group of closely related operations)
- Input: Command records / Output: Result records
- No framework annotations

---

## Outbound Port (Driven Interface)

Defines what the application needs from the outside. Outbound adapters implement this.

```java
public interface PaymentGatewayPort {
    PaymentGatewayResponse charge(PaymentGatewayRequest request);
    PaymentGatewayResponse refund(String transactionId, BigDecimal amount);
}

public interface PaymentPersistencePort {
    Optional<Payment> findById(UUID id);
    Payment save(Payment payment);
}

public interface PaymentEventPort {
    void publishPaymentCompleted(PaymentCompletedEvent event);
    void publishPaymentFailed(PaymentFailedEvent event);
}
```

Rules:
- Defined in `application/port/out/`
- Uses domain model types, not infrastructure types
- No SDK or framework types in the signature (no `JsonNode`, no `RestClientResponse`)

---

## Application Service (Use-Case Implementation)

Implements inbound ports. Depends on outbound ports.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService implements ProcessPaymentUseCase, GetPaymentStatusUseCase {

    private final PaymentPersistencePort persistencePort;
    private final PaymentGatewayPort gatewayPort;
    private final PaymentEventPort eventPort;

    @Override
    @Transactional
    public PaymentResult processPayment(ProcessPaymentCommand command) {
        Payment payment = Payment.initiate(command.orderId(), command.amount(), command.method());

        PaymentGatewayRequest gatewayRequest = PaymentGatewayRequest.from(payment);
        PaymentGatewayResponse gatewayResponse = gatewayPort.charge(gatewayRequest);

        payment.applyGatewayResult(gatewayResponse.transactionId(), gatewayResponse.status());
        Payment saved = persistencePort.save(payment);

        if (saved.isCompleted()) {
            eventPort.publishPaymentCompleted(PaymentCompletedEvent.from(saved));
        } else {
            eventPort.publishPaymentFailed(PaymentFailedEvent.from(saved));
        }

        return PaymentResult.from(saved);
    }

    @Override
    public PaymentStatusResult getStatus(UUID paymentId) {
        Payment payment = persistencePort.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return PaymentStatusResult.from(payment);
    }
}
```

Rules:
- Implements inbound port interfaces
- Depends only on outbound port interfaces — never on adapter classes
- Coordinates flow: domain logic stays in domain model
- `@Transactional` boundary is here

---

## Domain Model (Pure POJO)

No JPA annotations. No framework dependency. Pure business logic.

```java
@Getter
public class Payment {

    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String transactionId;
    private LocalDateTime createdAt;

    public static Payment initiate(UUID orderId, BigDecimal amount, PaymentMethod method) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.amount = amount;
        payment.method = method;
        payment.status = PaymentStatus.INITIATED;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }

    public void applyGatewayResult(String transactionId, GatewayResultStatus gatewayStatus) {
        if (this.status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment already processed");
        }
        this.transactionId = transactionId;
        this.status = gatewayStatus == GatewayResultStatus.SUCCESS
            ? PaymentStatus.COMPLETED
            : PaymentStatus.FAILED;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    // reconstruct from persistence (used by mapper)
    public static Payment reconstitute(UUID id, UUID orderId, BigDecimal amount,
                                        PaymentMethod method, PaymentStatus status,
                                        String transactionId, LocalDateTime createdAt) {
        Payment payment = new Payment();
        payment.id = id;
        payment.orderId = orderId;
        payment.amount = amount;
        payment.method = method;
        payment.status = status;
        payment.transactionId = transactionId;
        payment.createdAt = createdAt;
        return payment;
    }
}
```

Rules:
- No `@Entity`, no `@Table`, no `@Column`
- Static factory for creation, `reconstitute` for loading from persistence
- All state transitions enforce invariants

---

## Inbound Adapter (REST Controller)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final GetPaymentStatusUseCase getPaymentStatusUseCase;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request) {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
            request.orderId(), request.amount(), request.method());
        PaymentResult result = processPaymentUseCase.processPayment(command);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<PaymentStatusResponse> getStatus(@PathVariable UUID id) {
        PaymentStatusResult result = getPaymentStatusUseCase.getStatus(id);
        return ResponseEntity.ok(PaymentStatusResponse.from(result));
    }
}
```

Rules:
- Depends on inbound port interface, not on service class directly
- Translates HTTP request to command, result to HTTP response
- No business logic

---

## Outbound Adapter (Persistence)

JPA entity and mapper are adapter-internal. Domain model stays clean.

```java
// JPA entity — adapter internal
@Entity
@Table(name = "payments")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String transactionId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

```java
// Mapper — adapter internal
@Component
class PaymentPersistenceMapper {

    Payment toDomain(PaymentJpaEntity entity) {
        return Payment.reconstitute(
            entity.getId(), entity.getOrderId(), entity.getAmount(),
            entity.getMethod(), entity.getStatus(),
            entity.getTransactionId(), entity.getCreatedAt());
    }

    PaymentJpaEntity toEntity(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.id = payment.getId();
        entity.orderId = payment.getOrderId();
        entity.amount = payment.getAmount();
        entity.method = payment.getMethod();
        entity.status = payment.getStatus();
        entity.transactionId = payment.getTransactionId();
        entity.createdAt = payment.getCreatedAt();
        return entity;
    }
}
```

```java
// Port implementation
@Component
@RequiredArgsConstructor
class PaymentPersistenceAdapter implements PaymentPersistencePort {

    private final JpaPaymentRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = mapper.toEntity(payment);
        PaymentJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}

interface JpaPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {
}
```

Rules:
- JPA entity is package-private to the adapter — not used outside
- Mapper converts between domain model and JPA entity
- Adapter implements outbound port

---

## Outbound Adapter (External API)

```java
@Component
@RequiredArgsConstructor
class ExternalPaymentGatewayAdapter implements PaymentGatewayPort {

    private final PaymentVendorClient vendorClient;

    @Override
    public PaymentGatewayResponse charge(PaymentGatewayRequest request) {
        VendorChargeResponse vendorResponse = vendorClient.charge(
            request.amount(), request.method().toVendorCode());
        return new PaymentGatewayResponse(
            vendorResponse.getTransactionId(),
            mapStatus(vendorResponse.getResultCode()));
    }

    @Override
    public PaymentGatewayResponse refund(String transactionId, BigDecimal amount) {
        VendorRefundResponse vendorResponse = vendorClient.refund(transactionId, amount);
        return new PaymentGatewayResponse(
            vendorResponse.getTransactionId(),
            mapStatus(vendorResponse.getResultCode()));
    }

    private GatewayResultStatus mapStatus(String vendorCode) {
        return "SUCCESS".equals(vendorCode)
            ? GatewayResultStatus.SUCCESS
            : GatewayResultStatus.FAILURE;
    }
}
```

Rules:
- Vendor SDK types do not leak into port signatures
- Adapter translates between port types and vendor types
- Error handling (timeout, retry) is adapter responsibility

---

## Bean Wiring

Domain services (non-Spring classes) are registered via configuration.

```java
@Configuration
public class DomainConfig {

    @Bean
    public PaymentValidationService paymentValidationService() {
        return new PaymentValidationService();
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Domain model has `@Entity` annotation | Domain model is pure POJO; JPA entity lives in persistence adapter |
| Outbound port returns vendor SDK type | Port returns domain or application types only |
| Application service depends on adapter class | Depend on port interface; adapter is injected by Spring |
| Business rule in adapter | Move to domain model or domain service |
| Controller depends on service class instead of inbound port | Depend on use-case interface |
| No mapper — domain model and JPA entity are same class | Separate them; mapper converts between the two |
| Port interface defined in adapter package | Port interface lives in `application/port/` |
