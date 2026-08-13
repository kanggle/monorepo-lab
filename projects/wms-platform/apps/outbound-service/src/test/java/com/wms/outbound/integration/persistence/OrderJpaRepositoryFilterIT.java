package com.wms.outbound.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.adapter.out.persistence.entity.OrderEntity;
import com.wms.outbound.adapter.out.persistence.repository.OrderJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
        // query-text regression only needs the orders table, and the full outbound
        // Flyway set is orthogonal to this fix. Real Postgres still exercises the
        // 42P18 path.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderJpaRepository orderRepository;

    @Test
    @DisplayName("findFiltered — all filters null runs on Postgres without 42P18")
    void findFiltered_allNull_doesNotFailPgTypeInference() {
        List<OrderEntity> result = orderRepository.findFiltered(
                null, null, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("countFiltered — all filters null runs on Postgres without 42P18")
    void countFiltered_allNull_doesNotFailPgTypeInference() {
        long count = orderRepository.countFiltered(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(count).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("findFiltered — :tenantId pins results to a single tenant (TASK-MONO-304)")
    void findFiltered_byTenantId_isolatesTenants() {
        OrderEntity ecommerce = persistedOrder("ORD-EC-1", "FULFILLMENT_ECOMMERCE", "ecommerce");
        OrderEntity b2b = persistedOrder("ORD-B2B-1", "MANUAL", null);
        orderRepository.saveAll(List.of(ecommerce, b2b));

        List<OrderEntity> scoped = orderRepository.findFiltered(
                null, null, null, null, null, "ecommerce", null, null, null, null,
                PageRequest.of(0, 20));
        assertThat(scoped).extracting(OrderEntity::getOrderNo).containsExactly("ORD-EC-1");

        long count = orderRepository.countFiltered(
                null, null, null, null, null, "ecommerce", null, null, null, null);
        assertThat(count).isEqualTo(1L);

        // No tenant filter → both rows visible (native-wms / unrestricted path).
        assertThat(orderRepository.countFiltered(
                null, null, null, null, null, null, null, null, null, null))
                .isEqualTo(2L);
    }

    /**
     * TASK-BE-581 AC-4 / ADR-MONO-064 § D2 — the cell this suite did not have.
     *
     * <p>Until § D2 the list path pinned {@code source = FULFILLMENT_ECOMMERCE} on top of
     * the tenant filter, so a {@code MANUAL} row could not reach a tenant-scoped caller
     * even if the tenant filter had leaked: the source pin was a second line of defence
     * that no test ever had to distinguish from the first. § D1 now stamps tenants onto
     * {@code MANUAL} orders and § D2 removed that second line, so <b>the tenant filter
     * alone</b> has to hold for exactly the row shape this decision introduced.
     *
     * <p>The two {@code MANUAL} rows below differ only in tenant. If the filter were
     * ever weakened to "tenant matches OR the row is MANUAL" — the shape of mistake §
     * D2 makes reachable — the sibling row would appear here and nowhere else in the
     * suite.
     */
    @Test
    @DisplayName("findFiltered — :tenantId isolates MANUAL rows too, now that they carry tenants")
    void findFiltered_byTenantId_isolatesManualOrdersAcrossTenants() {
        OrderEntity mine = persistedOrder("ORD-MAN-DEMO", "MANUAL", "demo-corp");
        OrderEntity theirs = persistedOrder("ORD-MAN-ACME", "MANUAL", "acme-corp");
        OrderEntity theirEcommerce = persistedOrder("ORD-EC-ACME", "FULFILLMENT_ECOMMERCE", "acme-corp");
        orderRepository.saveAll(List.of(mine, theirs, theirEcommerce));

        List<OrderEntity> scoped = orderRepository.findFiltered(
                null, null, null, null, null, "demo-corp", null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(scoped)
                .as("a demo-corp caller sees its own MANUAL order and nobody else's")
                .extracting(OrderEntity::getOrderNo)
                .containsExactly("ORD-MAN-DEMO");
        assertThat(orderRepository.countFiltered(
                null, null, null, null, null, "demo-corp", null, null, null, null))
                .isEqualTo(1L);
        // Control: the sibling rows really are present, so the single result above is
        // isolation and not an empty fixture.
        assertThat(orderRepository.countFiltered(
                null, null, null, null, null, "acme-corp", null, null, null, null))
                .isEqualTo(2L);
    }

    private static OrderEntity persistedOrder(String orderNo, String source, String tenantId) {
        Instant now = Instant.now();
        return new OrderEntity(
                UUID.randomUUID(), orderNo, source,
                UUID.randomUUID(), UUID.randomUUID(), "RECEIVED",
                null, null, null, null, null, tenantId,
                now, "test", now, "test");
    }
}
