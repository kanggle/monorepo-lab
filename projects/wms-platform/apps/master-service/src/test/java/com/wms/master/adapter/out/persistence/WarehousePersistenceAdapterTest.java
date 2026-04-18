package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Optional;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MasterServicePersistenceConfig.class,
        WarehousePersistenceMapper.class,
        WarehousePersistenceAdapter.class})
@Testcontainers
class WarehousePersistenceAdapterTest {

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
    WarehousePersistenceAdapter adapter;

    @Autowired
    JpaWarehouseRepository jpaRepository;

    @Test
    @DisplayName("insert persists a new warehouse and assigns version 0")
    void insertPersistsNew() {
        Warehouse created = Warehouse.create("WH01", "Seoul Main", "Seoul, Korea", "Asia/Seoul", ACTOR);

        Warehouse saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert translates duplicate warehouseCode to WarehouseCodeDuplicateException")
    void insertDuplicateCode() {
        Warehouse first = Warehouse.create("WH02", "First", null, "UTC", ACTOR);
        adapter.insert(first);

        Warehouse duplicate = Warehouse.create("WH02", "Second", null, "UTC", ACTOR);

        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(WarehouseCodeDuplicateException.class)
                .hasMessageContaining("WH02");
    }

    @Test
    @DisplayName("findById returns the persisted warehouse")
    void findByIdHit() {
        Warehouse w = Warehouse.create("WH03", "Name", "Addr", "UTC", ACTOR);
        adapter.insert(w);

        Optional<Warehouse> found = adapter.findById(w.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getWarehouseCode()).isEqualTo("WH03");
        assertThat(found.get().getName()).isEqualTo("Name");
        assertThat(found.get().getAddress()).isEqualTo("Addr");
        assertThat(found.get().getTimezone()).isEqualTo("UTC");
        assertThat(found.get().getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
        assertThat(found.get().getCreatedBy()).isEqualTo(ACTOR);
    }

    @Test
    @DisplayName("findById returns empty when id unknown")
    void findByIdMiss() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByCode returns the persisted warehouse")
    void findByCodeHit() {
        Warehouse w = Warehouse.create("WH04", "ByCode", null, "UTC", ACTOR);
        adapter.insert(w);

        Optional<Warehouse> found = adapter.findByCode("WH04");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(w.getId());
    }

    @Test
    @DisplayName("update mutable fields; version bumps; immutable fields preserved")
    void updateMutableFields() {
        Warehouse w = Warehouse.create("WH05", "Original", "Original Addr", "UTC", ACTOR);
        Warehouse inserted = adapter.insert(w);
        assertThat(inserted.getVersion()).isZero();

        Warehouse loaded = adapter.findById(w.getId()).orElseThrow();
        loaded.applyUpdate("Renamed", "New Addr", "Asia/Seoul", "actor-2");
        adapter.update(loaded);

        Warehouse reloaded = adapter.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getAddress()).isEqualTo("New Addr");
        assertThat(reloaded.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(reloaded.getWarehouseCode()).isEqualTo("WH05");
        assertThat(reloaded.getCreatedBy()).isEqualTo(ACTOR);
        assertThat(reloaded.getUpdatedBy()).isEqualTo("actor-2");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("deactivate state persists")
    void deactivatePersists() {
        Warehouse w = Warehouse.create("WH06", "For deactivation", null, "UTC", ACTOR);
        adapter.insert(w);

        Warehouse loaded = adapter.findById(w.getId()).orElseThrow();
        loaded.deactivate("actor-2");
        adapter.update(loaded);

        Warehouse reloaded = adapter.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        Warehouse w = Warehouse.create("WH07", "Concurrent", null, "UTC", ACTOR);
        adapter.insert(w);

        // Two separate loads represent two actors reading the same version
        Warehouse firstLoad = adapter.findById(w.getId()).orElseThrow();
        Warehouse secondLoad = adapter.findById(w.getId()).orElseThrow();

        firstLoad.applyUpdate("Changed by first", null, null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate("Changed by second (stale)", null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage returns paginated, filtered, sorted results")
    void findPagePaginatesAndFilters() {
        adapter.insert(Warehouse.create("WH10", "Alpha",   null, "UTC", ACTOR));
        adapter.insert(Warehouse.create("WH11", "Bravo",   null, "UTC", ACTOR));
        adapter.insert(Warehouse.create("WH12", "Charlie", null, "UTC", ACTOR));

        Warehouse toDeactivate = adapter.findByCode("WH11").orElseThrow();
        toDeactivate.deactivate(ACTOR);
        adapter.update(toDeactivate);

        PageResult<Warehouse> activeOnly = adapter.findPage(
                new WarehouseListCriteria(WarehouseStatus.ACTIVE, null),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(activeOnly.totalElements()).isEqualTo(2);
        assertThat(activeOnly.content())
                .extracting(Warehouse::getWarehouseCode)
                .containsExactlyInAnyOrder("WH10", "WH12");

        PageResult<Warehouse> filteredByQuery = adapter.findPage(
                new WarehouseListCriteria(null, "Bravo"),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(filteredByQuery.totalElements()).isEqualTo(1);
        assertThat(filteredByQuery.content().get(0).getWarehouseCode()).isEqualTo("WH11");

        PageResult<Warehouse> firstPage = adapter.findPage(
                WarehouseListCriteria.any(),
                new PageQuery(0, 2, "warehouseCode", "asc"));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content().get(0).getWarehouseCode()).isEqualTo("WH10");
    }
}
