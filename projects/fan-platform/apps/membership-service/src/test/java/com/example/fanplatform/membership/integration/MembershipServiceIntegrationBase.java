package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.testsupport.JwksMockServer;
import com.example.fanplatform.membership.testsupport.JwtTestHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/**
 * Shared base for membership-service integration tests. Spins up Postgres +
 * Kafka Testcontainers + a WireMock JWKS server. NO Redis (membership-service
 * has no cache case).
 *
 * <p>The same JWKS stand-in serves both the end-user decoder
 * ({@code /.well-known/jwks.json}) and the workload-identity decoder
 * ({@code /oauth2/jwks}); both token kinds are signed by the one
 * {@link JwtTestHelper} keypair. The internal issuer is pinned to
 * {@link JwtTestHelper#SAS_ISSUER}.
 *
 * <p>Tagged {@code @Tag("integration")} so the default {@code test} task skips
 * it; only {@code integrationTest} includes it. {@code disabledWithoutDocker}
 * so it skips cleanly on a Docker-less host.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class MembershipServiceIntegrationBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fanplatform_membership")
            .withUsername("test")
            .withPassword("test");

    protected static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    protected static final JwtTestHelper jwt;
    protected static final JwksMockServer jwks;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Truncate all tables via JDBC (NOT Spring Data {@code deleteAll()}). The
     * {@code outbox} / {@code processed_events} repositories are configured with
     * {@code enableDefaultTransactions = false} (java-messaging
     * {@code OutboxJpaConfig}), so their {@code deleteAll()} runs without a
     * transaction and fails ("No EntityManager with actual transaction available
     * ... merge"). A JDBC TRUNCATE auto-commits on its own connection — no JPA
     * transaction required — and also clears {@code idempotency_keys} so the
     * shared singleton containers do not leak state across test classes.
     */
    protected void truncateAll() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE memberships, idempotency_keys, outbox, processed_events "
                        + "RESTART IDENTITY CASCADE");
    }

    // Containers + JWKS are started in a static initializer (NOT @BeforeAll) so
    // they are running BEFORE the Spring context loads — @DynamicPropertySource
    // is evaluated during context refresh, which happens before @BeforeAll, so a
    // @BeforeAll start is too late ("Mapped port can only be obtained after the
    // container is started"). Mirrors the proven CI-green erp read-model base.
    // @Testcontainers(disabledWithoutDocker = true) still skips cleanly on a
    // Docker-less host: that ExecutionCondition is evaluated before JUnit
    // actively uses the class, so this static block never runs when skipped.
    static {
        POSTGRES.start();
        KAFKA.start();
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
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        registry.add("fanplatform.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("fanplatform.oauth2.required-tenant-id", () -> "fan-platform");
        // Workload-identity decoder for /internal/** — same JWKS host, pinned issuer.
        registry.add("fanplatform.internal.jwt.jwk-set-uri", () -> jwks.hostInternalJwksUrl());
        registry.add("fanplatform.internal.jwt.issuer", () -> JwtTestHelper.SAS_ISSUER);
        // Disable the outbox poller by default; relay tests enable it explicitly.
        registry.add("outbox.polling.enabled", () -> "false");
    }
}
