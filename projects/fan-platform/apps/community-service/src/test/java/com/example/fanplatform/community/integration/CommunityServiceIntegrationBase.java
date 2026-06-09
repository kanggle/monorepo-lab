package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.testsupport.JwksMockServer;
import com.example.fanplatform.community.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/**
 * Shared base class for community-service integration tests. Spins up Postgres
 * + Kafka + Redis Testcontainers + a WireMock JWKS server. Subclasses inherit
 * the wiring and use {@link org.springframework.boot.test.web.client.TestRestTemplate}
 * (or @AutoConfigureMockMvc) to exercise endpoints.
 *
 * <p>Tagged {@code @Tag("integration")} so the default `test` Gradle task
 * skips this; only `integrationTest` includes it. Annotated
 * {@code disabledWithoutDocker = true} so the suite skips cleanly on Windows
 * native JVM (per memory project_testcontainers_docker_desktop_blocker).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class CommunityServiceIntegrationBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fanplatform_community")
            .withUsername("test")
            .withPassword("test");

    protected static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    protected static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    protected static final JwtTestHelper jwt;
    protected static final JwksMockServer jwks;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Truncate all tables via JDBC (NOT Spring Data {@code deleteAll()}). The
     * {@code outbox} / {@code processed_events} repositories come from
     * java-messaging {@code OutboxJpaConfig} which declares
     * {@code @EnableJpaRepositories(enableDefaultTransactions = false)}, so their
     * {@code deleteAll()} runs without a transaction and fails
     * ("No EntityManager with actual transaction available ... merge"). A JDBC
     * TRUNCATE auto-commits on its own connection — no JPA transaction required —
     * and clears every table so the shared singleton containers do not leak state
     * across IT classes (memory §19c). Mirrors the CI-green membership-service /
     * erp masterdata bases.
     */
    protected void truncateAll() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE posts, post_status_history, comments, reactions, "
                        + "follows, outbox, processed_events RESTART IDENTITY CASCADE");
    }

    // Containers + JWKS are started in a static initializer (NOT @BeforeAll) so
    // they are running BEFORE the Spring context loads — @DynamicPropertySource
    // is evaluated during context refresh, which happens before @BeforeAll, so a
    // @BeforeAll start is too late ("Mapped port can only be obtained after the
    // container is started"). Mirrors the proven CI-green membership-service /
    // erp read-model bases (memory §19). @Testcontainers(disabledWithoutDocker =
    // true) still skips cleanly on a Docker-less host: that ExecutionCondition is
    // evaluated before JUnit uses the class, so this static block never runs when
    // skipped. Singleton containers stop at JVM shutdown (Testcontainers Ryuk) —
    // no @AfterAll teardown needed (and none that could race the next class).
    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
        try {
            jwt = new JwtTestHelper();
            jwks = new JwksMockServer(jwt);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        registry.add("fanplatform.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("fanplatform.oauth2.required-tenant-id", () -> "fan-platform");
        // Disable the outbox poller in integration tests by default; specific
        // tests that exercise the relay path enable it via @TestPropertySource.
        registry.add("outbox.polling.enabled", () -> "false");
    }
}
