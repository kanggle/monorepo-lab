---
name: standalone-profile
description: @Profile("standalone") in-memory fallbacks for local dev
category: backend
---

# Skill: Standalone Profile

Patterns for `@Profile("standalone")` in-memory implementations for local development without Docker.

Prerequisite: read `platform/deployment-policy.md` before using this skill.

---

## Purpose

The `standalone` profile enables running any service without Kafka, Redis, PostgreSQL, or Elasticsearch. Useful for:
- Frontend development against a single backend
- Quick debugging without Docker Compose
- Local testing of business logic

---

## StandaloneConfig Pattern

Each service provides a config class that registers in-memory fallbacks.

```java
@Configuration
@Profile("standalone")
public class StandaloneConfig {

    // In-memory rate limiter — always allows
    @Bean
    public RateLimiter rateLimiter() {
        return (clientKey, maxRequests, windowSeconds) -> false;
    }

    // In-memory event publisher — logs and skips
    @Bean
    public AuthEventPublisher authEventPublisher() {
        return event -> log.debug("Standalone: skipping event {}", event.eventType());
    }

    // In-memory refresh token store
    @Bean
    public RefreshTokenStore refreshTokenStore() {
        return new InMemoryRefreshTokenStore();
    }

    // In-memory session registry
    @Bean
    public UserSessionRegistry userSessionRegistry() {
        return new InMemoryUserSessionRegistry();
    }
}
```

---

## Implementation Categories

| Category | Standalone Behavior |
|---|---|
| Event publishers | No-op (log and skip) |
| Rate limiters | Always allow (return `false`) |
| Token stores (Redis) | `ConcurrentHashMap` with manual TTL |
| Session registries (Redis) | `LinkedList`-based FIFO |
| OAuth state stores (Redis) | `ConcurrentHashMap` |
| Access token blocklists (Redis) | `ConcurrentHashMap` with scheduled cleanup |
| External API clients | Stub responses or test data |

---

## Application Configuration

> **Note:** H2 is used here for standalone local development only. This does NOT apply to integration tests — see `platform/testing-strategy.md` which requires Testcontainers with real PostgreSQL.

```yaml
# application-standalone.yml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: update
  datasource:
    url: jdbc:h2:file:./data/{service}-db
    driver-class-name: org.h2.Driver
  kafka:
    # Not configured — Kafka beans disabled by @Profile("!standalone")
```

---

## Activation

```bash
# Via environment variable
SPRING_PROFILES_ACTIVE=standalone ./gradlew :apps:auth-service:bootRun

# Via Gradle property
./gradlew :apps:auth-service:bootRun --args='--spring.profiles.active=standalone'
```

---

## Bean Registration Strategy

Use `@Profile` annotations consistently:
- `@Profile("!standalone")` on Redis/Kafka implementations
- `@Profile("standalone")` on in-memory fallbacks (in `StandaloneConfig`)

```java
// Real implementation
@Component
@Profile("!standalone")
public class RedisRefreshTokenStore implements RefreshTokenStore { ... }

// Standalone fallback (registered in StandaloneConfig)
@Bean
@Profile("standalone")  // implicit via @Configuration class
public RefreshTokenStore refreshTokenStore() {
    return new InMemoryRefreshTokenStore();
}
```

---

## Services with StandaloneConfig

| Service | Standalone Beans |
|---|---|
| auth-service | RateLimiter, RefreshTokenStore, UserSessionRegistry, OAuthStateStore, AccessTokenBlocklist, AuthEventPublisher |
| order-service | OrderEventPublisher (+ RestClient for payment) |
| product-service | ProductEventPublisher |
| payment-service | PaymentEventPublisher, PaymentGatewayPort |
| user-service | ProductInfoProvider, test user initialization |

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Missing `@Profile("!standalone")` on Redis bean | Both beans registered → conflict |
| H2 SQL incompatible with PostgreSQL | Use `ddl-auto: update` — skip Flyway in standalone |
| Standalone config missing for new Redis dependency | Add in-memory fallback when introducing new Redis usage |
| Test accidentally activating standalone | Use `@ActiveProfiles("test")` not `standalone` in tests |
