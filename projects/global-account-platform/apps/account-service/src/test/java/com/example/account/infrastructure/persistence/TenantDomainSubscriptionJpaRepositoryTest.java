package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.TenantStatus;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-325 (ADR-MONO-019 § 3.3 step 2): real-DB proof that the V0020
 * Flyway seed feeds {@link TenantDomainSubscriptionJpaRepository} correctly —
 * specifically the {@code findByStatusAndTenantId} (reverse-lookup) path that
 * backs the keystone (BE-324) {@code findActiveByTenantId} port method.
 *
 * <p>Uses the same {@code @DataJpaTest} + {@code MySQLContainer} + Flyway
 * pattern as {@link TenantJpaRepositoryTest} — no new Testcontainers
 * bootstrap introduced.
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@link DockerAvailableCondition}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("TenantDomainSubscriptionJpaRepository — V0020 real-seed reverse-lookup proof (TASK-BE-325)")
class TenantDomainSubscriptionJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("account_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TenantDomainSubscriptionJpaRepository repository;

    // ── V0020 real-customer tenant reverse-lookup ────────────────────────────

    @Nested
    @DisplayName("AC-2: findByStatusAndTenantId (reverse-lookup) — V0020 seed")
    class ReverseLookup {

        @Test
        @DisplayName("acme-corp → {finance, wms} exactly (set equality, order-agnostic)")
        void acmeCorp_returnsFinanceAndWms() {
            List<TenantDomainSubscriptionJpaEntity> results =
                    repository.findByStatusAndTenantId(TenantStatus.ACTIVE, "acme-corp");

            Set<String> domainKeys = results.stream()
                    .map(TenantDomainSubscriptionJpaEntity::getDomainKey)
                    .collect(Collectors.toSet());

            assertThat(domainKeys)
                    .as("acme-corp must be subscribed to exactly finance and wms (V0020 seed)")
                    .containsExactlyInAnyOrder("finance", "wms");
        }

        @Test
        @DisplayName("wms → {wms} (V0019 self-subscription still present — no regression)")
        void wms_returnsSelf() {
            List<TenantDomainSubscriptionJpaEntity> results =
                    repository.findByStatusAndTenantId(TenantStatus.ACTIVE, "wms");

            Set<String> domainKeys = results.stream()
                    .map(TenantDomainSubscriptionJpaEntity::getDomainKey)
                    .collect(Collectors.toSet());

            assertThat(domainKeys)
                    .as("wms self-subscription (V0019 backward-compat seed) must still exist")
                    .containsExactly("wms");
        }

        @Test
        @DisplayName("nonexistent-tenant → empty list")
        void nonexistentTenant_returnsEmpty() {
            List<TenantDomainSubscriptionJpaEntity> results =
                    repository.findByStatusAndTenantId(TenantStatus.ACTIVE, "nonexistent-tenant");

            assertThat(results)
                    .as("unknown tenantId must produce an empty result (no FK violation)")
                    .isEmpty();
        }
    }

    // ── acme-corp must NOT have gap/scm/erp subscriptions ───────────────────

    @Test
    @DisplayName("acme-corp has no gap/scm/erp subscription (deliberate non-entitlement)")
    void acmeCorp_noGapScmErpSubscription() {
        List<TenantDomainSubscriptionJpaEntity> results =
                repository.findByStatusAndTenantId(TenantStatus.ACTIVE, "acme-corp");

        Set<String> domainKeys = results.stream()
                .map(TenantDomainSubscriptionJpaEntity::getDomainKey)
                .collect(Collectors.toSet());

        assertThat(domainKeys)
                .as("acme-corp must not be subscribed to gap (bindsAllTenants), scm, or erp")
                .doesNotContain("gap", "scm", "erp");
    }
}
