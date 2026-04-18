---
name: testcontainers
description: Testcontainers setup and usage
category: testing
---

# Skill: Testcontainers

Patterns for using Testcontainers in Spring Boot integration tests.

Prerequisite: read `platform/testing-strategy.md` before using this skill.

---

## Basic Setup

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("auth_db")
        .withUsername("auth_user")
        .withPassword("auth_pass");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

---

## Container Types Used

| Container | Image | Typical Use |
|---|---|---|
| PostgreSQL | `postgres:16-alpine` | Backend services persisting relational state |
| Redis | `redis:7-alpine` | Services using Redis for sessions, cache, idempotency keys, rate limits |
| Kafka | `apache/kafka:3.7.0` | Event producer/consumer tests |
| Elasticsearch | `elasticsearch:8.15.0` | Search-index services |

---

## Kafka Container

```java
@SuppressWarnings("resource")
@Container
static KafkaContainer kafka = new KafkaContainer(
    DockerImageName.parse("apache/kafka:3.7.0")
);

@DynamicPropertySource
static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
}
```

---

## Elasticsearch Container

```java
@SuppressWarnings("resource")
@Container
static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
    DockerImageName.parse("elasticsearch:8.15.0")
).withEnv("xpack.security.enabled", "false");

@DynamicPropertySource
static void elasticProperties(DynamicPropertyRegistry registry) {
    registry.add("elasticsearch.uris", elasticsearch::getHttpHostAddress);
}
```

---

## Property Override with @DynamicPropertySource

Always override connection properties to point to containers.

```java
@DynamicPropertySource
static void overrideProperties(DynamicPropertyRegistry registry) {
    // Database
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    // Redis
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

    // Application-specific
    registry.add("jwt.secret", () -> "test-secret-key-minimum-32-characters!!");
}
```

---

## Data Isolation

- Use `UUID.randomUUID()` for unique test data.
- Clean Redis keys in `@BeforeEach`.
- Do not rely on `@Transactional` rollback — Flyway migrations run real DDL.

---

## Rules

- Never use H2 as a substitute for PostgreSQL.
- Use `@SuppressWarnings("resource")` on container fields (they are managed by JUnit).
- Containers are `static` — shared across all tests in the class for performance.
- Use `@DynamicPropertySource` to wire container addresses into Spring context.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Container not starting — Docker not running | Ensure Docker Desktop is running |
| Port conflicts | Testcontainers uses random mapped ports — never hardcode ports |
| Slow test startup | Share containers across test class (`static @Container`) |
| H2 used instead of Testcontainers | Always use real PostgreSQL for integration tests |
| Missing `@DynamicPropertySource` | Spring context will connect to wrong DB/Redis |
