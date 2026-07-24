package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.adapter.outbound.dispatch.StandaloneDispatchAdapter;
import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code standalone} profile boots with <b>no vendor credentials</b> and serves dispatch via
 * the deterministic {@link StandaloneDispatchAdapter} — the EasyPost adapter + its dedicated pool
 * are not created (external-integrations.md § Test Suite; ADR-053 standalone parity). Only
 * Postgres is required (Flyway V1 → {@code validate}).
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("standalone")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StandaloneProfileBootIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("scm_logistics")
                    .withUsername("scm")
                    .withPassword("scm")
                    .withStartupTimeout(Duration.ofMinutes(3));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/logistics");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:59092");
        // No EASYPOST_API_KEY provided — standalone must boot without it.
    }

    @Autowired
    private ShipmentDispatchPort shipmentDispatchPort;

    @Autowired
    private DispatchPersistencePort persistencePort;

    @Test
    void standaloneAdapterIsActive_andServesDeterministicAck() {
        assertThat(shipmentDispatchPort).isInstanceOf(StandaloneDispatchAdapter.class);

        UUID shipmentId = UUID.randomUUID();
        Dispatch seeded = persistencePort.save(Dispatch.create(
                UUID.randomUUID(), ShipmentId.of(shipmentId), "SHP-STANDALONE",
                UUID.randomUUID(), "ORD-STANDALONE", "scm", Instant.now()));

        DispatchAck ack = shipmentDispatchPort.dispatch(seeded);

        assertThat(ack.vendor()).isEqualTo(Carrier.STANDALONE);
        assertThat(ack.trackingNo()).isEqualTo("STANDALONE-" + shipmentId);
        assertThat(ack.carrierCode()).isEqualTo("STANDALONE-CARRIER");
    }
}
