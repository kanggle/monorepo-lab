---
name: clean
description: Implement Clean Architecture service
category: backend
---

# Skill: Clean Architecture Implementation

Spring Boot implementation patterns for services using Clean Architecture (Uncle Bob).

Prerequisite: read `specs/services/<service>/architecture.md` before using this skill.

---

## Package Structure

```
com.example.{service}/
├── domain/
│   ├── model/                 # Entities, value objects
│   ├── event/                 # Domain events
│   └── exception/             # Domain-specific exceptions
├── usecase/
│   ├── port/
│   │   ├── in/                # Input ports (use-case interfaces)
│   │   └── out/               # Output ports (driven interfaces)
│   ├── interactor/            # Use-case implementations
│   ├── command/               # Input records
│   └── result/                # Output records
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/    # REST controllers
│   │   │   └── dto/
│   │   │       ├── request/
│   │   │       └── response/
│   │   └── event/             # Message consumer adapters
│   └── out/
│       ├── persistence/       # DB adapter
│       │   ├── entity/        # JPA entities
│       │   ├── repository/    # Spring Data repositories
│       │   └── mapper/        # Domain <-> JPA entity mappers
│       ├── external/          # External API adapters
│       └── event/             # Event publishing adapters
└── config/                    # Spring configuration, bean wiring
```

Key difference from Hexagonal: the structure is similar, but **Use Case (Interactor) is the central organizational unit**. Business logic concentrates in Use Case Interactors, while Domain handles only enterprise rules.

---

## Dependency Rule

All dependencies point **inward only**.

```
[Frameworks & Drivers] → [Adapters] → [Use Cases] → [Domain]
         outermost                                    innermost
```

- `domain/` — depends on nothing. Pure Java.
- `usecase/` — depends on `domain/` only. No framework dependency.
- `adapter/` — depends on `usecase/` ports. May use frameworks.
- `config/` — may reference all layers (for DI assembly purpose).

---

## Domain Layer (Enterprise Business Rules)

Framework-agnostic core business entities. The most stable layer.

```java
@Getter
public class Notification {

    private UUID id;
    private UUID recipientId;
    private NotificationType type;
    private String title;
    private String body;
    private NotificationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public static Notification create(UUID recipientId, NotificationType type,
                                       String title, String body) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.recipientId = recipientId;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.status = NotificationStatus.PENDING;
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public void markSent() {
        if (this.status != NotificationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING notifications can be marked as sent");
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public boolean isRetryable() {
        return this.status == NotificationStatus.FAILED
            && this.type != NotificationType.TRANSACTIONAL;
    }

    public static Notification reconstitute(UUID id, UUID recipientId, NotificationType type,
                                             String title, String body, NotificationStatus status,
                                             LocalDateTime createdAt, LocalDateTime sentAt) {
        Notification n = new Notification();
        n.id = id;
        n.recipientId = recipientId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.status = status;
        n.createdAt = createdAt;
        n.sentAt = sentAt;
        return n;
    }
}
```

Rules:
- No `@Entity`, no `@Component`, no Spring annotations
- Static factory for creation, `reconstitute` for persistence restoration
- Protects only its own invariants

---

## Use Case Layer (Application Business Rules)

### Input Port (Use-Case Interface)

**One use case = one interface**. This is the core principle of Clean Architecture.

```java
// usecase/port/in/SendNotificationUseCase.java
public interface SendNotificationUseCase {
    SendNotificationResult execute(SendNotificationCommand command);
}

// usecase/port/in/RetryFailedNotificationsUseCase.java
public interface RetryFailedNotificationsUseCase {
    RetryResult execute();
}

// usecase/port/in/GetNotificationStatusUseCase.java
public interface GetNotificationStatusUseCase {
    NotificationStatusResult execute(UUID notificationId);
}
```

Rules:
- Interface name IS the use-case name
- Single `execute()` method — one entry point per use case
- Command record for input, Result record for output

### Output Port (Driven Interface)

```java
// usecase/port/out/NotificationPersistencePort.java
public interface NotificationPersistencePort {
    Notification save(Notification notification);
    Optional<Notification> findById(UUID id);
    List<Notification> findFailedRetryable();
}

// usecase/port/out/NotificationSenderPort.java
public interface NotificationSenderPort {
    SendResult send(Notification notification);
}

// usecase/port/out/NotificationEventPort.java
public interface NotificationEventPort {
    void publishSent(NotificationSentEvent event);
    void publishFailed(NotificationFailedEvent event);
}
```

### Command / Result Records

```java
// usecase/command/SendNotificationCommand.java
public record SendNotificationCommand(
    UUID recipientId,
    NotificationType type,
    String title,
    String body
) {}

// usecase/result/SendNotificationResult.java
public record SendNotificationResult(
    UUID notificationId,
    NotificationStatus status
) {
    public static SendNotificationResult from(Notification notification) {
        return new SendNotificationResult(notification.getId(), notification.getStatus());
    }
}
```

### Interactor (Use-Case Implementation)

Interactors orchestrate use-case logic. They invoke Domain entities and communicate with the outside world through Output Ports.

```java
// usecase/interactor/SendNotificationInteractor.java
@RequiredArgsConstructor
public class SendNotificationInteractor implements SendNotificationUseCase {

    private final NotificationPersistencePort persistencePort;
    private final NotificationSenderPort senderPort;
    private final NotificationEventPort eventPort;

    @Override
    public SendNotificationResult execute(SendNotificationCommand command) {
        Notification notification = Notification.create(
            command.recipientId(), command.type(),
            command.title(), command.body());

        Notification saved = persistencePort.save(notification);

        SendResult sendResult = senderPort.send(saved);

        if (sendResult.isSuccess()) {
            saved.markSent();
            eventPort.publishSent(NotificationSentEvent.from(saved));
        } else {
            saved.markFailed();
            eventPort.publishFailed(NotificationFailedEvent.from(saved));
        }

        persistencePort.save(saved);
        return SendNotificationResult.from(saved);
    }
}
```

```java
// usecase/interactor/RetryFailedNotificationsInteractor.java
@RequiredArgsConstructor
public class RetryFailedNotificationsInteractor implements RetryFailedNotificationsUseCase {

    private final NotificationPersistencePort persistencePort;
    private final NotificationSenderPort senderPort;
    private final NotificationEventPort eventPort;

    @Override
    public RetryResult execute() {
        List<Notification> failedList = persistencePort.findFailedRetryable();
        int successCount = 0;

        for (Notification notification : failedList) {
            SendResult result = senderPort.send(notification);
            if (result.isSuccess()) {
                notification.markSent();
                eventPort.publishSent(NotificationSentEvent.from(notification));
                successCount++;
            }
            persistencePort.save(notification);
        }

        return new RetryResult(failedList.size(), successCount);
    }
}
```

```java
// usecase/interactor/GetNotificationStatusInteractor.java
@RequiredArgsConstructor
public class GetNotificationStatusInteractor implements GetNotificationStatusUseCase {

    private final NotificationPersistencePort persistencePort;

    @Override
    public NotificationStatusResult execute(UUID notificationId) {
        Notification notification = persistencePort.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        return NotificationStatusResult.from(notification);
    }
}
```

Rules:
- **No Spring annotations** — Interactor is a pure Java class (no `@Service`)
- Spring bean registration via `@Bean` in `config/`
- One Interactor implements one Input Port only
- Access external systems only through Output Ports
- No `@Transactional` on Interactor directly — handle in config via AOP or in adapter

---

## Adapter Layer (Interface Adapters)

### Inbound Adapter (REST Controller)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final SendNotificationUseCase sendNotificationUseCase;
    private final GetNotificationStatusUseCase getNotificationStatusUseCase;

    @PostMapping
    public ResponseEntity<SendNotificationResponse> send(
            @Valid @RequestBody SendNotificationRequest request) {
        SendNotificationCommand command = new SendNotificationCommand(
            request.recipientId(), request.type(),
            request.title(), request.body());
        SendNotificationResult result = sendNotificationUseCase.execute(command);
        return ResponseEntity.ok(SendNotificationResponse.from(result));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<NotificationStatusResponse> getStatus(@PathVariable UUID id) {
        NotificationStatusResult result = getNotificationStatusUseCase.execute(id);
        return ResponseEntity.ok(NotificationStatusResponse.from(result));
    }
}
```

Rules:
- Depends on Input Port interface, NOT on Interactor class directly
- Responsible only for HTTP <-> Command/Result conversion

### Outbound Adapter (Persistence)

```java
// JPA entity — internal to adapter
@Entity
@Table(name = "notifications")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class NotificationJpaEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID recipientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
```

```java
@Component
@RequiredArgsConstructor
class NotificationPersistenceMapper {

    Notification toDomain(NotificationJpaEntity entity) {
        return Notification.reconstitute(
            entity.getId(), entity.getRecipientId(), entity.getType(),
            entity.getTitle(), entity.getBody(), entity.getStatus(),
            entity.getCreatedAt(), entity.getSentAt());
    }

    NotificationJpaEntity toEntity(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.id = notification.getId();
        entity.recipientId = notification.getRecipientId();
        entity.type = notification.getType();
        entity.title = notification.getTitle();
        entity.body = notification.getBody();
        entity.status = notification.getStatus();
        entity.createdAt = notification.getCreatedAt();
        entity.sentAt = notification.getSentAt();
        return entity;
    }
}
```

```java
@Component
@RequiredArgsConstructor
class NotificationPersistenceAdapter implements NotificationPersistencePort {

    private final JpaNotificationRepository jpaRepository;
    private final NotificationPersistenceMapper mapper;

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = mapper.toEntity(notification);
        NotificationJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Notification> findFailedRetryable() {
        return jpaRepository.findByStatus(NotificationStatus.FAILED)
            .stream()
            .map(mapper::toDomain)
            .filter(Notification::isRetryable)
            .toList();
    }
}

interface JpaNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {
    List<NotificationJpaEntity> findByStatus(NotificationStatus status);
}
```

### Outbound Adapter (External System)

```java
@Component
@RequiredArgsConstructor
class EmailNotificationSenderAdapter implements NotificationSenderPort {

    private final JavaMailSender mailSender;

    @Override
    public SendResult send(Notification notification) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(resolveEmail(notification.getRecipientId()));
            message.setSubject(notification.getTitle());
            message.setText(notification.getBody());
            mailSender.send(message);
            return SendResult.success();
        } catch (MailException e) {
            return SendResult.failure(e.getMessage());
        }
    }
}
```

---

## Config Layer (Frameworks & Drivers)

Interactors have no Spring annotations, so bean registration and transaction handling are done in config.

```java
@Configuration
public class UseCaseConfig {

    @Bean
    public SendNotificationUseCase sendNotificationUseCase(
            NotificationPersistencePort persistencePort,
            NotificationSenderPort senderPort,
            NotificationEventPort eventPort) {
        return new SendNotificationInteractor(persistencePort, senderPort, eventPort);
    }

    @Bean
    public RetryFailedNotificationsUseCase retryFailedNotificationsUseCase(
            NotificationPersistencePort persistencePort,
            NotificationSenderPort senderPort,
            NotificationEventPort eventPort) {
        return new RetryFailedNotificationsInteractor(persistencePort, senderPort, eventPort);
    }

    @Bean
    public GetNotificationStatusUseCase getNotificationStatusUseCase(
            NotificationPersistencePort persistencePort) {
        return new GetNotificationStatusInteractor(persistencePort);
    }
}
```

### Transaction Handling

Since Interactors do not have `@Transactional` directly, choose one of two approaches:

**Approach 1: TransactionProxyFactoryBean in Config** (strict Clean Architecture)

```java
@Bean
public SendNotificationUseCase sendNotificationUseCase(
        NotificationPersistencePort persistencePort,
        NotificationSenderPort senderPort,
        NotificationEventPort eventPort,
        PlatformTransactionManager txManager) {
    SendNotificationInteractor target =
        new SendNotificationInteractor(persistencePort, senderPort, eventPort);

    TransactionProxyFactoryBean proxy = new TransactionProxyFactoryBean();
    proxy.setTarget(target);
    proxy.setTransactionManager(txManager);
    Properties txAttributes = new Properties();
    txAttributes.setProperty("execute", "PROPAGATION_REQUIRED");
    proxy.setTransactionAttributes(txAttributes);
    proxy.afterPropertiesSet();

    return (SendNotificationUseCase) proxy.getObject();
}
```

**Approach 2: Delegate transaction to Adapter** (pragmatic approach)

```java
@Component
@RequiredArgsConstructor
class TransactionalNotificationPersistenceAdapter implements NotificationPersistencePort {

    private final JpaNotificationRepository jpaRepository;
    private final NotificationPersistenceMapper mapper;

    @Override
    @Transactional
    public Notification save(Notification notification) {
        // ...
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID id) {
        // ...
    }
}
```

---

## Clean Architecture vs Hexagonal: Key Differences

| Aspect | Hexagonal | Clean Architecture |
|---|---|---|
| Central concept | Port & Adapter (boundary isolation) | Use Case (business use case) |
| Application Service | Multiple use cases in one service | **One Interactor = one use case** |
| Framework dependency | `@Service` allowed on Application Service | **No Spring annotations** on Interactor |
| Bean registration | Auto-registration via `@Service` | Manual registration via `@Bean` in `@Configuration` |
| Method naming | Use-case-specific methods (`processPayment`, `getStatus`) | Single `execute()` method |
| Best fit | External system isolation focus | Use-case independence and test isolation focus |

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Adding `@Service` to Interactor | Pure Java without Spring annotations, register via `@Bean` |
| Multiple use cases in one Interactor | One Interactor per use case, one Input Port |
| JPA annotations on Domain model | Domain is pure POJO, JPA entity stays inside adapter |
| Output Port returning JPA entity types | Ports use Domain model types only |
| Controller depending on Interactor class directly | Depend on Input Port interface |
| Declaring `@Transactional` on Interactor directly | Handle via proxy in config or in Persistence Adapter |
| Grouping all use cases in one service class | Clean Architecture requires per-use-case separation |
| Merging Domain and Use Case layers | Domain = enterprise rules, Use Case = application rules — keep in separate packages |
