package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.admin.readmodel.master.LocationRefEntity;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefEntity;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefEntity;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.RefSearch;
import com.wms.admin.readmodel.master.SkuRefEntity;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefEntity;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-630 — the § 1.7 master-ref {@code search} queries against real PostgreSQL.
 *
 * <p>Two things only a database can say: (1) an unfiltered call (every param null) does
 * not trip PostgreSQL's untyped-null inference (the 42P18 class this read model already
 * hit in TASK-BE-331/332), and (2) the JPQL {@code LOWER(..) LIKE :q ESCAPE '\'} really
 * matches case-insensitively and treats {@code _} literally. The slice test proves the
 * controller passes the right values; it cannot prove the query does the right thing.
 *
 * <p>The container is shared and carries the dev seed, so every assertion is scoped to a
 * per-test random token — seeded refs can neither satisfy nor break it.
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                // 🔴 Must match every other class sharing AdminServiceIntegrationBase's
                // container (see ReadModelPersistenceIntegrationTest — a list that omits
                // db/seed no longer resolves the applied seed and Flyway refuses to boot).
                "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
@Transactional
class MasterRefSearchIntegrationTest extends AdminServiceIntegrationBase {

    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final PageRequest PAGE = PageRequest.of(0, 50);

    @Autowired WarehouseRefRepository warehouseRepo;
    @Autowired ZoneRefRepository zoneRepo;
    @Autowired LocationRefRepository locationRepo;
    @Autowired SkuRefRepository skuRepo;
    @Autowired LotRefRepository lotRepo;
    @Autowired PartnerRefRepository partnerRepo;

    private String tok;

    @BeforeEach
    void token() {
        tok = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String like(String q) {
        return RefSearch.likePattern(q);
    }

    @Test
    void everySearch_allNullFilters_doesNotFailPgTypeInference() {
        // Unfiltered = the default call the console makes on first render.
        warehouseRepo.search(null, null, PAGE);
        zoneRepo.search(null, null, null, PAGE);
        locationRepo.search(null, null, null, PAGE);
        skuRepo.search(null, null, PAGE);
        lotRepo.search(null, null, null, PAGE);
        partnerRepo.search(null, null, true, RefSearch.types(null), PAGE);
    }

    @Test
    void warehouse_q_matchesCodeOrNameCaseInsensitively() {
        UUID byCode = UUID.randomUUID();
        UUID byName = UUID.randomUUID();
        warehouseRepo.save(new WarehouseRefEntity(byCode, "WH-" + tok, "Busan", "Asia/Seoul", "ACTIVE", NOW));
        // Code must NOT contain tok (so only the name can match) and must fit
        // warehouse_code VARCHAR(40) — a full UUID suffix (45 chars) failed the insert on CI.
        warehouseRepo.save(new WarehouseRefEntity(byName,
                "WHN-" + UUID.randomUUID().toString().substring(0, 8),
                "Incheon " + tok, "Asia/Seoul", "ACTIVE", NOW));

        var ids = warehouseRepo.search(like(tok.toLowerCase(Locale.ROOT)), null, PAGE)
                .map(WarehouseRefEntity::getId).getContent();

        assertThat(ids).containsExactlyInAnyOrder(byCode, byName);
    }

    @Test
    void underscore_isLiteral_notASingleCharWildcard() {
        UUID literal = UUID.randomUUID();
        UUID wildcardOnly = UUID.randomUUID();
        skuRepo.save(new SkuRefEntity(literal, "SKU_" + tok, "x", "EA", "NONE", "ACTIVE", NOW));
        // Would match "SKU_<tok>" if '_' were LIKE's any-single-char wildcard.
        skuRepo.save(new SkuRefEntity(wildcardOnly, "SKUX" + tok, "x", "EA", "NONE", "ACTIVE", NOW));

        var ids = skuRepo.search(like("SKU_" + tok), null, PAGE).map(SkuRefEntity::getId).getContent();

        assertThat(ids).containsExactly(literal);
    }

    @Test
    void status_filtersExactly() {
        UUID active = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        skuRepo.save(new SkuRefEntity(active, "A-" + tok, "x", "EA", "NONE", "ACTIVE", NOW));
        skuRepo.save(new SkuRefEntity(inactive, "I-" + tok, "x", "EA", "NONE", "INACTIVE", NOW));

        var ids = skuRepo.search(like(tok), "ACTIVE", PAGE).map(SkuRefEntity::getId).getContent();

        assertThat(ids).containsExactly(active);
    }

    @Test
    void locationsAndZones_scopeByWarehouse() {
        UUID wh = UUID.randomUUID();
        UUID otherWh = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        locationRepo.save(new LocationRefEntity(mine, "L-" + tok + "-1", wh, null, "STORAGE", "ACTIVE", NOW));
        locationRepo.save(new LocationRefEntity(theirs, "L-" + tok + "-2", otherWh, null, "STORAGE", "ACTIVE", NOW));
        UUID zMine = UUID.randomUUID();
        zoneRepo.save(new ZoneRefEntity(zMine, wh, "Z-" + tok, "zone", "STORAGE", "ACTIVE", NOW));
        zoneRepo.save(new ZoneRefEntity(UUID.randomUUID(), otherWh, "Z2-" + tok, "zone", "STORAGE", "ACTIVE", NOW));

        assertThat(locationRepo.search(like(tok), null, wh, PAGE).map(LocationRefEntity::getId).getContent())
                .containsExactly(mine);
        assertThat(zoneRepo.search(like(tok), null, wh, PAGE).map(ZoneRefEntity::getId).getContent())
                .containsExactly(zMine);
    }

    @Test
    void lots_scopeBySku() {
        UUID sku = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        lotRepo.save(new LotRefEntity(mine, sku, "LOT-" + tok, null, "ACTIVE", NOW));
        lotRepo.save(new LotRefEntity(UUID.randomUUID(), UUID.randomUUID(), "LOT-" + tok, null, "ACTIVE", NOW));

        assertThat(lotRepo.search(like(tok), null, sku, PAGE).map(LotRefEntity::getId).getContent())
                .containsExactly(mine);
    }

    @Test
    void partners_filterByAnyOfTheGivenTypes() {
        UUID supplier = UUID.randomUUID();
        UUID both = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        partnerRepo.save(new PartnerRefEntity(supplier, "S-" + tok, "Supplier " + tok, "SUPPLIER", "ACTIVE", NOW));
        partnerRepo.save(new PartnerRefEntity(both, "B-" + tok, "Both " + tok, "BOTH", "ACTIVE", NOW));
        partnerRepo.save(new PartnerRefEntity(customer, "C-" + tok, "Customer " + tok, "CUSTOMER", "ACTIVE", NOW));

        List<String> supplierTypes = List.of("SUPPLIER", "BOTH");
        assertThat(partnerRepo.search(like(tok), null, RefSearch.anyType(supplierTypes),
                        RefSearch.types(supplierTypes), PAGE).map(PartnerRefEntity::getId).getContent())
                .containsExactlyInAnyOrder(supplier, both);

        // Control: no type filter returns all three — the flag, not the placeholder, decides.
        assertThat(partnerRepo.search(like(tok), null, true, RefSearch.types(null), PAGE)
                        .map(PartnerRefEntity::getId).getContent())
                .containsExactlyInAnyOrder(supplier, both, customer);
    }
}
