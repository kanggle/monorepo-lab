package com.example.fanplatform.artist.integration;

import com.example.fanplatform.artist.testsupport.JwksMockServer;
import com.example.fanplatform.artist.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/**
 * Shared base class for artist-service integration tests. Spins up Postgres
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
public abstract class ArtistServiceIntegrationBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fanplatform_artist")
            .withUsername("test")
            .withPassword("test");

    protected static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    protected static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    protected static JwtTestHelper jwt;
    protected static JwksMockServer jwks;

    @BeforeAll
    static void startSharedInfra() throws IOException {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
        jwt = new JwtTestHelper();
        jwks = new JwksMockServer(jwt);
    }

    @AfterAll
    static void stopSharedInfra() throws IOException {
        if (jwks != null) jwks.close();
        if (REDIS != null) REDIS.stop();
        if (KAFKA != null) KAFKA.stop();
        if (POSTGRES != null) POSTGRES.stop();
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
