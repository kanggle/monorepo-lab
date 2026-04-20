package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.query.ListSkusCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.BarcodeDuplicateException;
import com.wms.master.domain.exception.SkuCodeDuplicateException;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

/**
 * H2-backed persistence adapter test — identical ORM coverage to
 * {@link SkuPersistenceAdapterTest} but without Testcontainers, so the
 * JPA / exception-translation path is exercised on every CI run regardless
 * of Docker availability.
 *
 * <p>What this test does NOT cover (Testcontainers variant does):
 * <ul>
 *   <li>Partial unique index on {@code barcode} (lives in Flyway V5 only —
 *       JPA {@code @UniqueConstraint} cannot express a filter)
 *   <li>{@code CHECK (sku_code = UPPER(sku_code))} guard (same reason)
 * </ul>
 * The domain factory uppercases {@code skuCode} on create, so the CHECK is
 * a defense-in-depth rather than a behavior gate — safe to omit here.
 */
@DataJpaTest
@Import({MasterServicePersistenceConfig.class,
        SkuPersistenceMapper.class,
        SkuPersistenceAdapter.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sku_adapter_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class SkuPersistenceAdapterH2Test {

    private static final String ACTOR = "actor-uuid";

    @Autowired
    SkuPersistencePort adapter;

    @Autowired
    JpaSkuRepository jpaRepository;

    @Test
    @DisplayName("insert persists a new SKU and assigns version 0")
    void insertPersistsNew() {
        Sku created = sku("SKU-001", "Gala Apple 1kg", null,
                BaseUom.EA, TrackingType.LOT, 30);

        Sku saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert translates duplicate skuCode to SkuCodeDuplicateException")
    void insertDuplicateCode() {
        adapter.insert(sku("SKU-DUP", "First", null, BaseUom.EA, TrackingType.NONE, null));

        Sku duplicate = sku("SKU-DUP", "Second", null, BaseUom.EA, TrackingType.NONE, null);

        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(SkuCodeDuplicateException.class)
                .hasMessageContaining("SKU-DUP");
    }

    @Test
    @DisplayName("case-insensitive duplicate via UPPERCASE normalization at factory")
    void insertDuplicateCodeCaseInsensitive() {
        adapter.insert(sku("sku-case", "First", null, BaseUom.EA, TrackingType.NONE, null));

        // Domain factory uppercases both — inserts conflict even though raw inputs differ in case
        Sku duplicate = sku("SKU-CASE", "Second", null, BaseUom.EA, TrackingType.NONE, null);

        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(SkuCodeDuplicateException.class);
    }

    @Test
    @DisplayName("findBySkuCode accepts the normalized UPPERCASE form")
    void findBySkuCodeNormalized() {
        Sku created = adapter.insert(
                sku("sku-lookup", "For lookup", null, BaseUom.EA, TrackingType.NONE, null));

        Optional<Sku> byExact = adapter.findBySkuCode("SKU-LOOKUP");

        assertThat(byExact).isPresent();
        assertThat(byExact.get().getId()).isEqualTo(created.getId());
        assertThat(byExact.get().getSkuCode()).isEqualTo("SKU-LOOKUP");
    }

    @Test
    @DisplayName("findByBarcode returns the persisted SKU")
    void findByBarcode() {
        Sku created = adapter.insert(
                sku("SKU-BAR", "With barcode", "8801234567890", BaseUom.EA, TrackingType.NONE, null));

        Optional<Sku> found = adapter.findByBarcode("8801234567890");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("findByBarcode returns empty for absent code")
    void findByBarcodeMiss() {
        adapter.insert(sku("SKU-NOBAR", "No barcode", null, BaseUom.EA, TrackingType.NONE, null));

        assertThat(adapter.findByBarcode("doesnotexist")).isEmpty();
    }

    @Test
    @DisplayName("findById returns empty for unknown id")
    void findByIdMiss() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("update mutable fields; version bumps; immutable fields preserved")
    void updateMutableFields() {
        Sku inserted = adapter.insert(
                sku("SKU-UPD", "Original", null, BaseUom.EA, TrackingType.NONE, null));
        assertThat(inserted.getVersion()).isZero();

        Sku loaded = adapter.findById(inserted.getId()).orElseThrow();
        loaded.applyUpdate("Renamed", "Now with desc", "8801111111111",
                500, 250, "HAZ-3", 14, "actor-2");
        adapter.update(loaded);

        Sku reloaded = adapter.findById(inserted.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getDescription()).isEqualTo("Now with desc");
        assertThat(reloaded.getBarcode()).isEqualTo("8801111111111");
        assertThat(reloaded.getWeightGrams()).isEqualTo(500);
        assertThat(reloaded.getVolumeMl()).isEqualTo(250);
        assertThat(reloaded.getHazardClass()).isEqualTo("HAZ-3");
        assertThat(reloaded.getShelfLifeDays()).isEqualTo(14);
        // immutable fields
        assertThat(reloaded.getSkuCode()).isEqualTo("SKU-UPD");
        assertThat(reloaded.getBaseUom()).isEqualTo(BaseUom.EA);
        assertThat(reloaded.getTrackingType()).isEqualTo(TrackingType.NONE);
        assertThat(reloaded.getUpdatedBy()).isEqualTo("actor-2");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("deactivate state persists")
    void deactivatePersists() {
        Sku s = adapter.insert(
                sku("SKU-DEACT", "For deactivation", null, BaseUom.EA, TrackingType.NONE, null));

        Sku loaded = adapter.findById(s.getId()).orElseThrow();
        loaded.deactivate("actor-2");
        adapter.update(loaded);

        Sku reloaded = adapter.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        Sku s = adapter.insert(
                sku("SKU-OPT", "Concurrent", null, BaseUom.EA, TrackingType.NONE, null));

        Sku first = adapter.findById(s.getId()).orElseThrow();
        Sku second = adapter.findById(s.getId()).orElseThrow();

        first.applyUpdate("First edit", null, null, null, null, null, null, "actor-first");
        adapter.update(first);

        second.applyUpdate("Second edit (stale)", null, null, null, null, null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage paginates, filters by status and trackingType, sorts")
    void findPagePaginatesAndFilters() {
        adapter.insert(sku("SKU-P01", "Alpha",   null, BaseUom.EA,  TrackingType.NONE, null));
        adapter.insert(sku("SKU-P02", "Bravo",   null, BaseUom.BOX, TrackingType.LOT,  15));
        adapter.insert(sku("SKU-P03", "Charlie", null, BaseUom.EA,  TrackingType.LOT,  30));

        Sku toDeactivate = adapter.findBySkuCode("SKU-P02").orElseThrow();
        toDeactivate.deactivate(ACTOR);
        adapter.update(toDeactivate);

        PageResult<Sku> activeOnly = adapter.findPage(
                new ListSkusCriteria(WarehouseStatus.ACTIVE, null, null, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(activeOnly.totalElements()).isEqualTo(2);
        assertThat(activeOnly.content())
                .extracting(Sku::getSkuCode)
                .containsExactlyInAnyOrder("SKU-P01", "SKU-P03");

        PageResult<Sku> lotOnly = adapter.findPage(
                new ListSkusCriteria(null, null, TrackingType.LOT, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(lotOnly.totalElements()).isEqualTo(2);
        assertThat(lotOnly.content())
                .extracting(Sku::getSkuCode)
                .containsExactlyInAnyOrder("SKU-P02", "SKU-P03");

        PageResult<Sku> byBarcode = adapter.findPage(
                ListSkusCriteria.any(),
                new PageQuery(0, 10, "skuCode", "asc"));

        assertThat(byBarcode.totalElements()).isEqualTo(3);
        assertThat(byBarcode.content().get(0).getSkuCode()).isEqualTo("SKU-P01");
    }

    /**
     * TASK-BE-009: the constraint-name extraction path must translate a
     * barcode-unique violation to {@link BarcodeDuplicateException} even
     * when the exception's root-cause message is opaque (e.g. because the
     * DB emitted a localized message that does not contain the constraint
     * string). This simulates a Postgres {@link PSQLException} whose
     * {@link ServerErrorMessage#getConstraint()} returns only the constraint
     * name — the substring fallback would not fire in that case.
     */
    @Test
    @DisplayName("translateIntegrityViolation uses Postgres constraint-name to detect barcode duplicate")
    void translateBarcodeDuplicateViaConstraintName() {
        JpaSkuRepository mockRepo = mock(JpaSkuRepository.class);
        SkuPersistenceMapper realMapper = new SkuPersistenceMapper();
        SkuPersistenceAdapter directAdapter = new SkuPersistenceAdapter(mockRepo, realMapper);

        // Server-error message wire format: each field is a single-char type
        // code followed by a null-terminated value. PostgreSQL uses 'n'
        // (U+006E) for the constraint field (see Postgres protocol: ErrorResponse).
        // The message body ('M') is intentionally a non-English, non-matching
        // string so the substring fallback cannot match the constraint name —
        // forcing the test to rely on the structured getConstraint() path.
        ServerErrorMessage sem = new ServerErrorMessage(
                "Sérror\0Mopaque localized message\0nuq_skus_barcode\0");
        PSQLException pg = new PSQLException(sem);
        DataIntegrityViolationException wrapped =
                new DataIntegrityViolationException("could not execute statement", pg);
        when(mockRepo.saveAndFlush(any(SkuJpaEntity.class))).thenThrow(wrapped);

        Sku s = sku("SKU-BARC", "Dup bar", "8801234567890",
                BaseUom.EA, TrackingType.NONE, null);

        assertThatThrownBy(() -> directAdapter.insert(s))
                .isInstanceOf(BarcodeDuplicateException.class)
                .hasMessageContaining("8801234567890");
    }

    private static Sku sku(String code, String name, String barcode,
                           BaseUom uom, TrackingType tracking, Integer shelfLifeDays) {
        return Sku.create(code, name, null, barcode, uom, tracking,
                null, null, null, shelfLifeDays, ACTOR);
    }
}
