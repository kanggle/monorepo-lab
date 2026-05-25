package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.PartnerPersistencePort;
import com.wms.master.application.query.ListPartnersCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.model.Partner;
import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

/**
 * H2-backed persistence adapter test — exercises the JPA / exception-translation
 * path on every CI run regardless of Docker availability. The Postgres-specific
 * constraint-name path is covered by {@link PartnerRepositoryImplTest}.
 */
@DataJpaTest
@Import({MasterServicePersistenceConfig.class,
        PartnerPersistenceMapper.class,
        PartnerRepositoryImpl.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partner_adapter_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PartnerRepositoryImplH2Test {

    private static final String ACTOR = "actor-uuid";

    @Autowired
    PartnerPersistencePort adapter;

    @Autowired
    JpaPartnerRepository jpaRepository;

    @Test
    @DisplayName("insert persists a new Partner and assigns version 0")
    void insertPersistsNew() {
        Partner created = partner("SUP-001", PartnerType.SUPPLIER);

        Partner saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert translates duplicate partnerCode to PartnerCodeDuplicateException")
    void insertDuplicateCode() {
        adapter.insert(partner("SUP-DUP", PartnerType.SUPPLIER));

        Partner duplicate = partner("SUP-DUP", PartnerType.CUSTOMER);

        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(PartnerCodeDuplicateException.class)
                .hasMessageContaining("SUP-DUP");
    }

    @Test
    @DisplayName("findByCode returns the persisted Partner")
    void findByCodeHit() {
        Partner created = adapter.insert(partner("SUP-LOOKUP", PartnerType.SUPPLIER));

        Optional<Partner> found = adapter.findByCode("SUP-LOOKUP");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("findByCode returns empty for absent code")
    void findByCodeMiss() {
        adapter.insert(partner("SUP-1", PartnerType.SUPPLIER));
        assertThat(adapter.findByCode("doesnotexist")).isEmpty();
    }

    @Test
    @DisplayName("existsByCode reports correctly")
    void existsByCode() {
        adapter.insert(partner("SUP-EX", PartnerType.SUPPLIER));
        assertThat(adapter.existsByCode("SUP-EX")).isTrue();
        assertThat(adapter.existsByCode("NOPE")).isFalse();
    }

    @Test
    @DisplayName("findById returns empty for unknown id")
    void findByIdMiss() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("update mutable fields; version bumps; immutable partnerCode preserved")
    void updateMutableFields() {
        Partner inserted = adapter.insert(partner("SUP-UPD", PartnerType.SUPPLIER));
        assertThat(inserted.getVersion()).isZero();

        Partner loaded = adapter.findById(inserted.getId()).orElseThrow();
        loaded.applyUpdate("Renamed", PartnerType.BOTH, "B-2", "C2",
                "c2@example.com", "+82-2", "Busan", "actor-2");
        adapter.update(loaded);

        Partner reloaded = adapter.findById(inserted.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getPartnerType()).isEqualTo(PartnerType.BOTH);
        assertThat(reloaded.getBusinessNumber()).isEqualTo("B-2");
        assertThat(reloaded.getContactName()).isEqualTo("C2");
        assertThat(reloaded.getContactEmail()).isEqualTo("c2@example.com");
        assertThat(reloaded.getContactPhone()).isEqualTo("+82-2");
        assertThat(reloaded.getAddress()).isEqualTo("Busan");
        assertThat(reloaded.getPartnerCode()).isEqualTo("SUP-UPD");
        assertThat(reloaded.getUpdatedBy()).isEqualTo("actor-2");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("deactivate state persists")
    void deactivatePersists() {
        Partner p = adapter.insert(partner("SUP-DEACT", PartnerType.SUPPLIER));

        Partner loaded = adapter.findById(p.getId()).orElseThrow();
        loaded.deactivate("actor-2");
        adapter.update(loaded);

        Partner reloaded = adapter.findById(p.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        Partner p = adapter.insert(partner("SUP-OPT", PartnerType.SUPPLIER));

        Partner first = adapter.findById(p.getId()).orElseThrow();
        Partner second = adapter.findById(p.getId()).orElseThrow();

        first.applyUpdate("First edit", null, null, null, null, null, null, "actor-first");
        adapter.update(first);

        second.applyUpdate("Second edit (stale)", null, null, null, null, null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage paginates, filters by status and partnerType, sorts")
    void findPagePaginatesAndFilters() {
        adapter.insert(partner("SUP-P01", PartnerType.SUPPLIER));
        adapter.insert(partner("CUST-P02", PartnerType.CUSTOMER));
        adapter.insert(partner("BOTH-P03", PartnerType.BOTH));

        Partner toDeactivate = adapter.findByCode("CUST-P02").orElseThrow();
        toDeactivate.deactivate(ACTOR);
        adapter.update(toDeactivate);

        PageResult<Partner> activeOnly = adapter.findPage(
                new ListPartnersCriteria(WarehouseStatus.ACTIVE, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(activeOnly.totalElements()).isEqualTo(2);

        PageResult<Partner> supplierOnly = adapter.findPage(
                new ListPartnersCriteria(null, null, PartnerType.SUPPLIER),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(supplierOnly.totalElements()).isEqualTo(1);
        assertThat(supplierOnly.content().get(0).getPartnerCode()).isEqualTo("SUP-P01");

        PageResult<Partner> all = adapter.findPage(
                ListPartnersCriteria.any(),
                new PageQuery(0, 10, "partnerCode", "asc"));
        assertThat(all.totalElements()).isEqualTo(3);
        assertThat(all.content().get(0).getPartnerCode()).isEqualTo("BOTH-P03");
    }

    private static Partner partner(String code, PartnerType type) {
        return Partner.create(code, "Sample Partner " + code, type,
                null, null, null, null, null, ACTOR);
    }
}
