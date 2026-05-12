package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.application.port.out.PartnerPersistencePort;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.model.Partner;
import com.wms.master.domain.model.PartnerType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers-backed persistence adapter test for Partner. Uses Postgres 16
 * alpine to verify constraints that cannot be expressed in JPA or H2 the same
 * way:
 * <ul>
 *   <li>{@link PartnerCodeDuplicateException} translation via
 *       {@code PSQLException.getServerErrorMessage().getConstraint()}
 * </ul>
 *
 * <p>The H2 counterpart {@link PartnerPersistenceAdapterH2Test} covers the
 * remaining ORM cases without requiring Docker.
 *
 * <p>Annotated {@code @Testcontainers(disabledWithoutDocker = true)} — on
 * Windows CI environments without Docker, the class is skipped automatically.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MasterServicePersistenceConfig.class,
        PartnerPersistenceMapper.class,
        PartnerPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class PartnerPersistenceAdapterTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("master_test")
            .withUsername("master_test")
            .withPassword("master_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    private static final String ACTOR = "actor-uuid";

    @Autowired
    PartnerPersistencePort adapter;

    @Autowired
    JpaPartnerRepository jpaRepository;

    @Test
    @DisplayName("insert persists a new Partner and assigns version 0 (Flyway-managed schema)")
    void insertPersistsNew() {
        Partner created = partner("SUP-TC-001", PartnerType.SUPPLIER);

        Partner saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("two Partners with the same partnerCode → PartnerCodeDuplicateException via constraint-name path")
    void duplicatePartnerCode_raisesPartnerCodeDuplicateException() {
        Partner first = partner("SUP-TC-DUP", PartnerType.SUPPLIER);
        Partner second = partner("SUP-TC-DUP", PartnerType.CUSTOMER);

        adapter.insert(first);

        assertThatThrownBy(() -> adapter.insert(second))
                .isInstanceOf(PartnerCodeDuplicateException.class)
                .hasMessageContaining("SUP-TC-DUP");
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        Partner p = partner("SUP-TC-OPT", PartnerType.SUPPLIER);
        adapter.insert(p);

        Partner firstLoad = adapter.findById(p.getId()).orElseThrow();
        Partner secondLoad = adapter.findById(p.getId()).orElseThrow();

        firstLoad.applyUpdate("Changed by first", null, null, null, null, null, null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate("Changed by second (stale)", null, null, null, null, null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("partnerType enum CHECK constraint values are accepted")
    void allPartnerTypesAccepted() {
        adapter.insert(partner("SUP-TC-A", PartnerType.SUPPLIER));
        adapter.insert(partner("CUST-TC-A", PartnerType.CUSTOMER));
        adapter.insert(partner("BOTH-TC-A", PartnerType.BOTH));

        assertThat(jpaRepository.count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("partnerCode lookup matches the persisted row exactly (no case-fold, distinct from SKU)")
    void findByCodeCaseSensitive() {
        adapter.insert(partner("Sup-Mixed", PartnerType.SUPPLIER));

        assertThat(adapter.findByCode("Sup-Mixed")).isPresent();
        // The partner_code column has no UPPER() check; mixed-case is stored as-is.
        // A lowercase lookup must miss because the index is exact-match.
        assertThat(adapter.findByCode("sup-mixed")).isEmpty();
    }

    private static Partner partner(String code, PartnerType type) {
        // randomize id to avoid PK collisions across tests in the same Postgres
        // container reuse window.
        return Partner.create(code, "Partner " + code + "-" + UUID.randomUUID().toString().substring(0, 4),
                type, null, null, null, null, null, ACTOR);
    }
}
