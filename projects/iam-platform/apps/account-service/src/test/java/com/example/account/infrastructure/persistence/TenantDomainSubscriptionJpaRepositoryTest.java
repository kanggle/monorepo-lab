package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
                    repository.findByStatusAndTenantId(SubscriptionStatus.ACTIVE, "acme-corp");

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
                    repository.findByStatusAndTenantId(SubscriptionStatus.ACTIVE, "wms");

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
                    repository.findByStatusAndTenantId(SubscriptionStatus.ACTIVE, "nonexistent-tenant");

            assertThat(results)
                    .as("unknown tenantId must produce an empty result (no FK violation)")
                    .isEmpty();
        }
    }

    // ── acme-corp must NOT have iam/scm/erp subscriptions ───────────────────

    @Test
    @DisplayName("acme-corp has no iam/scm/erp subscription (deliberate non-entitlement)")
    void acmeCorp_noGapScmErpSubscription() {
        List<TenantDomainSubscriptionJpaEntity> results =
                repository.findByStatusAndTenantId(SubscriptionStatus.ACTIVE, "acme-corp");

        Set<String> domainKeys = results.stream()
                .map(TenantDomainSubscriptionJpaEntity::getDomainKey)
                .collect(Collectors.toSet());

        assertThat(domainKeys)
                .as("acme-corp must not be subscribed to iam (bindsAllTenants), scm, or erp")
                .doesNotContain("iam", "scm", "erp");
    }

    // ── TASK-BE-342 (ADR-MONO-023 step 2): write path + V0021 CHECK ──────────

    @Nested
    @DisplayName("AC: mutation persistence — save / findByTenantIdAndDomainKey + V0021 CHECK accepts all 4 states")
    class MutationPersistence {

        private static TenantDomainSubscriptionJpaEntity entity(String tenantId, String domainKey,
                                                                SubscriptionStatus status) {
            Instant t = Instant.parse("2026-06-10T00:00:00Z");
            return TenantDomainSubscriptionJpaEntity.fromDomain(
                    TenantDomainSubscription.reconstitute(new TenantId(tenantId), domainKey, status, t, t));
        }

        @Test
        @DisplayName("save new (acme-corp, erp, PENDING) → findByTenantIdAndDomainKey returns it")
        void save_andFindSingle() {
            repository.saveAndFlush(entity("acme-corp", "erp", SubscriptionStatus.PENDING));

            Optional<TenantDomainSubscriptionJpaEntity> found =
                    repository.findByTenantIdAndDomainKey("acme-corp", "erp");

            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        }

        @Test
        @DisplayName("V0021 CHECK accepts SUSPENDED and CANCELLED (write round-trip, no constraint violation)")
        void checkAcceptsSuspendedAndCancelled() {
            repository.saveAndFlush(entity("acme-corp", "scm", SubscriptionStatus.SUSPENDED));
            repository.saveAndFlush(entity("acme-corp", "erp", SubscriptionStatus.CANCELLED));

            assertThat(repository.findByTenantIdAndDomainKey("acme-corp", "scm"))
                    .get().extracting(TenantDomainSubscriptionJpaEntity::getStatus)
                    .isEqualTo(SubscriptionStatus.SUSPENDED);
            assertThat(repository.findByTenantIdAndDomainKey("acme-corp", "erp"))
                    .get().extracting(TenantDomainSubscriptionJpaEntity::getStatus)
                    .isEqualTo(SubscriptionStatus.CANCELLED);

            // a SUSPENDED subscription must NOT appear in the ACTIVE reverse-lookup
            // (net-zero read-path invariant: catalog/entitled_domains see ACTIVE only)
            Set<String> activeForAcme = repository
                    .findByStatusAndTenantId(SubscriptionStatus.ACTIVE, "acme-corp").stream()
                    .map(TenantDomainSubscriptionJpaEntity::getDomainKey)
                    .collect(Collectors.toSet());
            assertThat(activeForAcme).doesNotContain("scm", "erp");
        }

        @Test
        @DisplayName("missing pair → findByTenantIdAndDomainKey empty")
        void findMissing_empty() {
            assertThat(repository.findByTenantIdAndDomainKey("acme-corp", "nonexistent-domain"))
                    .isEmpty();
        }
    }
}
