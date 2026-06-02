package com.wms.outbound.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.adapter.out.persistence.entity.OrderEntity;
import com.wms.outbound.adapter.out.persistence.repository.OrderJpaRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression (TASK-BE-332): {@code OrderJpaRepository.findFiltered} /
 * {@code countFiltered} guard their nullable temporal bounds
 * ({@code requiredShipAfter/Before} LocalDate, {@code createdAfter/Before}
 * Instant) with a bare {@code :param IS NULL}. On an unfiltered call those bind
 * as untyped nulls, and PostgreSQL aborts the prepared statement with
 * {@code 42P18 could not determine data type of parameter} — a 500 on any
 * unfiltered order search. The fix CASTs the temporal IS-NULL guards (same class
 * as BE-331 AlertLog). The error fires at statement-prepare, so an empty table
 * still reproduces it.
 *
 * <p>A {@code @DataJpaTest} repository slice on a dedicated Postgres container
 * (mirrors master-service {@code LotRepositoryImplTest}) — NOT the full
 * {@code @SpringBootTest} {@code OutboundServiceIntegrationBase} (whose app
 * context is the wrong level for a query-text regression). Auto-skips without
 * Docker. outbound-service {@code integrationTest} is not wired into CI; this is
 * verified locally.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("OrderJpaRepository nullable-temporal filter — PostgreSQL 42P18 regression")
class OrderJpaRepositoryFilterIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("outbound_test")
            .withUsername("outbound_test")
            .withPassword("outbound_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Generate the OrderEntity schema from the mapping (NOT Flyway) — this
        // query-text regression only needs the orders table, and the outbound
        // Flyway set has a pre-existing test-time issue (V13 tms_request_dedupe)
        // orthogonal to this fix. Real Postgres still exercises the 42P18 path.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderJpaRepository orderRepository;

    @Test
    @DisplayName("findFiltered — all filters null runs on Postgres without 42P18")
    void findFiltered_allNull_doesNotFailPgTypeInference() {
        List<OrderEntity> result = orderRepository.findFiltered(
                null, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("countFiltered — all filters null runs on Postgres without 42P18")
    void countFiltered_allNull_doesNotFailPgTypeInference() {
        long count = orderRepository.countFiltered(
                null, null, null, null, null, null, null, null, null);

        assertThat(count).isGreaterThanOrEqualTo(0L);
    }
}
