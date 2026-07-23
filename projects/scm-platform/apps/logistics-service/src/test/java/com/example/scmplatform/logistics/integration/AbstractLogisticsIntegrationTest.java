package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.testsupport.integration.DockerAvailableCondition;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base for logistics-service integration tests. Shared Postgres (Flyway V1 → {@code validate})
 * + a WireMock EasyPost stand-in, started once per JVM. There is no Kafka container — the seam
 * consumer is not wired in Phase 1, so nothing contacts a broker (external-integrations.md § Test
 * Suite; tests seed dispatch rows directly, architecture.md § Edge Cases).
 *
 * <p>Windows local = not authority: these tests SKIP without Docker; CI Linux is the gate.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractLogisticsIntegrationTest {

    protected static final String TENANT_SCM = "scm";

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("scm_logistics")
                    .withUsername("scm")
                    .withPassword("scm")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final WireMockServer EASYPOST =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        POSTGRES.start();
        EASYPOST.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/logistics");

        // EasyPost → WireMock. Fast timeouts + fast retry backoff so the resilience matrix runs
        // quickly under CI (still exercises retry/circuit/bulkhead).
        registry.add("logistics.easypost.base-url", EASYPOST::baseUrl);
        registry.add("logistics.easypost.connect-timeout-seconds", () -> "1");
        registry.add("logistics.easypost.read-timeout-seconds", () -> "2");
        registry.add("resilience4j.retry.instances.easyPostDispatch.wait-duration", () -> "100ms");
        registry.add("resilience4j.retry.instances.easyPostDispatch.exponential-max-wait-duration", () -> "300ms");

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:59092");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/oauth2/jwks");
        registry.add("scmplatform.oauth2.allowed-issuers", () -> "http://test-issuer");
    }

    @Autowired
    protected DispatchPersistencePort persistencePort;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** Wipe all logistics tables between tests (containers are reused across the class). */
    protected void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE dispatch, dispatch_request_dedupe, processed_events");
    }

    /** Seed a PENDING dispatch row directly (the trigger is the BE-044 event; no create endpoint). */
    protected Dispatch seedPending(UUID shipmentId, String shipmentNo) {
        Dispatch dispatch = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                shipmentNo, UUID.randomUUID(), "ORD-" + shipmentNo, TENANT_SCM, Instant.now());
        return persistencePort.save(dispatch);
    }

    /** Seed a DISPATCH_FAILED dispatch row (a prior vendor failure awaiting operator :retry). */
    protected Dispatch seedFailed(UUID shipmentId, String shipmentNo) {
        Dispatch dispatch = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                shipmentNo, UUID.randomUUID(), "ORD-" + shipmentNo, TENANT_SCM, Instant.now());
        dispatch.recordFailure("seeded prior failure", Instant.now());
        return persistencePort.save(dispatch);
    }
}
