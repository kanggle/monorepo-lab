---
name: layered
description: Implement Layered Architecture service
category: backend
---

# Skill: Layered Architecture Implementation

Spring Boot implementation patterns for services using Layered Architecture.

Prerequisite: read `specs/services/<service>/architecture.md` before using this skill.

---

## Package Structure

```
com.example.{service}/
├── presentation/
│   ├── controller/          # REST controllers
│   └── dto/
│       ├── request/         # HTTP request DTOs
│       └── response/        # HTTP response DTOs
├── application/
│   ├── service/             # Application services (use-case coordination)
│   ├── command/             # Input records from presentation
│   └── result/              # Output records to presentation
├── domain/
│   ├── model/               # Entities, enums
│   ├── repository/          # Repository interfaces
│   └── service/             # Domain services (optional, for cross-entity rules)
└── infrastructure/
    ├── persistence/          # JPA implementations, Spring Data repositories
    ├── config/               # Spring configuration classes
    └── adapter/              # External system adapters (Redis, mail, etc.)
```

Package-by-feature is allowed as an alternative, but the layered dependency rule must still hold.

---

## Layer Responsibilities

### Presentation

HTTP mapping only. No business logic.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        UserResult result = userProfileService.getUser(id);
        return ResponseEntity.ok(UserResponse.from(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        UpdateUserCommand command = new UpdateUserCommand(id, request.name(), request.email());
        UserResult result = userProfileService.updateUser(command);
        return ResponseEntity.ok(UserResponse.from(result));
    }
}
```

Rules:
- Controllers receive request DTOs, convert to commands, call application service, convert result to response
- No repository or domain model access
- Validation annotations (`@Valid`) belong here

### Application

Use-case coordination and transaction boundary.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResult getUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
        return UserResult.from(user);
    }

    @Transactional
    public UserResult updateUser(UpdateUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));
        user.updateProfile(command.name(), command.email());
        return UserResult.from(user);
    }
}
```

Rules:
- Owns `@Transactional` boundaries
- Calls domain model methods for business rules
- Uses domain-defined repository interfaces — never infrastructure classes directly
- Input: Command records / Output: Result records

### Domain

Business rules and entities. Framework-independent.

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    public void updateProfile(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        this.name = name;
        this.email = email;
    }
}
```

```java
public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    User save(User user);
}
```

Rules:
- Entity methods enforce business invariants
- Repository interfaces are defined here
- No framework imports except JPA annotations on entities
- No dependency on presentation or infrastructure

### Infrastructure

Persistence implementations, external adapters, configuration.

```java
public interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {
    // Spring Data auto-implements UserRepository methods
}
```

```java
@Component
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void store(String key, String token, Duration ttl) {
        redisTemplate.opsForValue().set(key, token, ttl);
    }
}
```

Rules:
- Implements domain-defined interfaces
- Owns framework and SDK dependencies
- Application layer accesses infrastructure behavior through domain interfaces only

---

## Interface Pattern for Infrastructure Access

When the application layer needs a computed value from infrastructure, the domain layer defines the interface and return type.

```java
// Domain layer: interface + result
public interface PasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}

// Infrastructure layer: implementation
@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}

// Application layer: uses interface only
@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder; // domain interface, not BCrypt class
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Controller calls repository directly | Route through application service |
| Application service imports infrastructure utility | Define interface in domain, implement in infrastructure |
| Business rule in controller (e.g., status check) | Move rule to entity or domain service |
| Domain entity imports Spring `@Service` or `@Component` | Keep domain free of Spring stereotypes (JPA annotations are acceptable) |
| `@Transactional` on controller | Move to application service |
