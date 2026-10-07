package com.wms.inventory.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-MONO-768 — the ecommerce stock path only works if four services' dev seeds and the demo
 * seed script agree on the same literal UUIDs. Nothing at runtime checks that; a mismatch does
 * not error, it silently BACKORDERS every store order.
 *
 * <p>Why each pair must be equal:
 * <ul>
 *   <li><b>SKU ids.</b> outbound's {@code picking.requested} carries outbound's
 *       {@code sku_snapshot.id} as {@code skuId}; the stock row was created from the inbound ASN
 *       line, which carries inbound's {@code sku_snapshot.id}; inventory resolves the reservation
 *       by {@code (warehouseId, skuId)} equality. inventory's own snapshot gates the receive
 *       ({@code MasterRefValidator}). seed-wms.sh generates the ASN line ids from a format string.
 *       All five must be the same 86 {@code (code → id)} pairs.</li>
 *   <li><b>WH-MAIN id.</b> ecommerce resolves WH-MAIN by code in outbound; the ASN is created
 *       against inbound's warehouse id; inventory rows carry that id; the reservation looks it up
 *       with outbound's id.</li>
 *   <li><b>Location / zone.</b> inbound validates the putaway location by id and requires it to be
 *       in the ASN's warehouse; seed-wms.sh names that id.</li>
 * </ul>
 *
 * <p>Reads the files from the repository (walking up from the module directory), not from the
 * classpath: only inventory's own seed is on this module's classpath. 🔴 Every extraction asserts
 * it found something — a regex that matches nothing must fail, not compare two empty maps.
 */
@DisplayName("TASK-MONO-768 — ecommerce seed UUIDs agree across master / inbound / inventory / outbound / admin / seed-wms.sh")
class EcommerceSeedParityTest {

    private static final String APPS = "projects/wms-platform/apps/";
    private static final Path OUTBOUND = Paths.get(APPS + "outbound-service/src/main/resources/db/seed/R__seed_dev_masterref.sql");
    private static final Path INBOUND = Paths.get(APPS + "inbound-service/src/main/resources/db/seed/R__seed_dev_masterref.sql");
    private static final Path INVENTORY = Paths.get(APPS + "inventory-service/src/main/resources/db/seed/R__seed_dev_masterref.sql");
    /** admin-service's ref tables — the console renders the codes its projections copy from here. */
    private static final Path ADMIN = Paths.get(APPS + "admin-service/src/main/resources/db/seed/R__seed_dev_masterref.sql");
    private static final Path MASTER_PARTNERS = Paths.get(APPS + "master-service/src/main/resources/db/seed/R__05_seed_dev_partners.sql");
    private static final Path MASTER_WAREHOUSE = Paths.get(APPS + "master-service/src/main/resources/db/seed/R__01_seed_dev_warehouse.sql");
    private static final Path MASTER_ZONES = Paths.get(APPS + "master-service/src/main/resources/db/seed/R__02_seed_dev_zones.sql");
    private static final Path MASTER_LOCATIONS = Paths.get(APPS + "master-service/src/main/resources/db/seed/R__03_seed_dev_locations.sql");
    private static final Path SEED_WMS = Paths.get("infra/demo/seed/seed-wms.sh");

    private static final int ECOMMERCE_SKU_COUNT = 86;
    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** {@code ('<id>', '<ecommerce variant uuid as code>', ...} — id and sku_code lead every tuple. */
    private static final Pattern ECOMMERCE_SKU_PAIR = Pattern.compile(
            "\\(\\s*'(" + UUID_RE + ")',\\s*'(c0000000-0000-0000-0000-[0-9]{12})'");
    /** {@code '<id>', 'WH-MAIN'} — warehouse id then code, in master's table and every snapshot. */
    private static final Pattern WH_MAIN = Pattern.compile("'(" + UUID_RE + ")',\\s*'WH-MAIN'");
    private static final String LOCATION_CODE = "WH-MAIN-A-01-01-01";
    /** Snapshot column order: id, location_code, warehouse_id, zone_id. */
    private static final Pattern SNAPSHOT_LOCATION = Pattern.compile(
            "'(" + UUID_RE + ")',\\s*'" + LOCATION_CODE + "',\\s*'(" + UUID_RE + ")',\\s*'(" + UUID_RE + ")'");
    /** master {@code locations} column order: id, warehouse_id, zone_id, location_code. */
    private static final Pattern MASTER_LOCATION = Pattern.compile(
            "'(" + UUID_RE + ")',\\s*'(" + UUID_RE + ")',\\s*'(" + UUID_RE + ")',\\s*'" + LOCATION_CODE + "'");

    @Test
    @DisplayName("the 86 ecommerce SKU (code → id) pairs are identical in outbound, inbound, inventory and seed-wms.sh")
    void ecommerceSkuIdsAgree() throws IOException {
        Map<String, String> outbound = skuPairs(OUTBOUND);
        Map<String, String> inbound = skuPairs(INBOUND);
        Map<String, String> inventory = skuPairs(INVENTORY);

        // outbound is the reference: its ids are what picking.requested sends (TASK-MONO-765).
        assertThat(outbound).as("outbound sku_snapshot ecommerce rows").hasSize(ECOMMERCE_SKU_COUNT);
        assertThat(inbound).as("inbound sku_snapshot must equal outbound's (the ASN line skuId)")
                .containsExactlyInAnyOrderEntriesOf(outbound);
        assertThat(inventory).as("inventory sku_snapshot must equal outbound's (the reservation lookup)")
                .containsExactlyInAnyOrderEntriesOf(outbound);
        assertThat(skuPairs(ADMIN)).as("admin_sku_ref must equal outbound's (the console's sku code)")
                .containsExactlyInAnyOrderEntriesOf(outbound);

        // seed-wms.sh does not list ids — it formats them. Re-run its formula and compare the set.
        String script = read(SEED_WMS);
        String format = shellLiteral(script, "EC_SKU_ID_FORMAT");
        int base = Integer.parseInt(shellLiteral(script, "EC_SKU_ID_BASE"));
        int count = Integer.parseInt(shellLiteral(script, "EC_SKU_COUNT"));
        List<String> generated = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            generated.add(String.format(format, base + i));
        }
        assertThat(generated).as("ids seed-wms.sh puts on the ASN lines")
                .containsExactlyInAnyOrderElementsOf(outbound.values());
    }

    @Test
    @DisplayName("WH-MAIN has one id in master, outbound, inbound, inventory and seed-wms.sh")
    void whMainIdAgrees() throws IOException {
        String master = single(WH_MAIN, MASTER_WAREHOUSE, 1);
        assertThat(single(WH_MAIN, OUTBOUND, 1)).as("outbound warehouse_snapshot").isEqualTo(master);
        assertThat(single(WH_MAIN, INBOUND, 1)).as("inbound warehouse_snapshot").isEqualTo(master);
        assertThat(single(WH_MAIN, INVENTORY, 1)).as("inventory warehouse_snapshot").isEqualTo(master);
        assertThat(single(WH_MAIN, ADMIN, 1)).as("admin_warehouse_ref").isEqualTo(master);
        assertThat(shellLiteral(read(SEED_WMS), "WH_MAIN_ID")).as("seed-wms.sh WH_MAIN_ID").isEqualTo(master);
    }

    @Test
    @DisplayName("ECOMMERCE-STORE has one id in master, outbound and admin")
    void ecommerceStorePartnerIdAgrees() throws IOException {
        Pattern partner = Pattern.compile("'(" + UUID_RE + ")',\\s*'ECOMMERCE-STORE'");
        String master = single(partner, MASTER_PARTNERS, 1);
        assertThat(single(partner, OUTBOUND, 1)).as("outbound partner_snapshot").isEqualTo(master);
        assertThat(single(partner, ADMIN, 1)).as("admin_partner_ref").isEqualTo(master);
    }

    @Test
    @DisplayName("WH-MAIN's location (id, warehouse, zone) agrees in master, inbound, inventory and seed-wms.sh")
    void whMainLocationAgrees() throws IOException {
        String warehouse = single(WH_MAIN, MASTER_WAREHOUSE, 1);
        Matcher master = only(MASTER_LOCATION, MASTER_LOCATIONS);
        String locationId = master.group(1);
        assertThat(master.group(2)).as("master location's warehouse").isEqualTo(warehouse);
        String zoneId = master.group(3);

        // The zone master hangs the location off must itself be WH-MAIN's, and inbound mirrors it.
        Pattern whMainZone = Pattern.compile("'(" + UUID_RE + ")',\\s*'" + warehouse + "',\\s*'Z-A'");
        assertThat(single(whMainZone, MASTER_ZONES, 1)).as("master zone under WH-MAIN").isEqualTo(zoneId);
        assertThat(single(whMainZone, INBOUND, 1)).as("inbound zone_snapshot").isEqualTo(zoneId);
        assertThat(single(whMainZone, ADMIN, 1)).as("admin_zone_ref").isEqualTo(zoneId);

        // admin_location_ref shares the snapshot column order (id, code, warehouse, zone).
        for (Path snapshot : List.of(INBOUND, INVENTORY, ADMIN)) {
            Matcher m = only(SNAPSHOT_LOCATION, snapshot);
            assertThat(m.group(1)).as("%s location id", snapshot).isEqualTo(locationId);
            assertThat(m.group(2)).as("%s location warehouse", snapshot).isEqualTo(warehouse);
            assertThat(m.group(3)).as("%s location zone", snapshot).isEqualTo(zoneId);
        }
        assertThat(shellLiteral(read(SEED_WMS), "WH_MAIN_LOCATION_ID"))
                .as("seed-wms.sh WH_MAIN_LOCATION_ID").isEqualTo(locationId);
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, String> skuPairs(Path file) throws IOException {
        Map<String, String> pairs = new LinkedHashMap<>();
        Matcher m = ECOMMERCE_SKU_PAIR.matcher(read(file));
        while (m.find()) {
            String previous = pairs.put(m.group(2), m.group(1));
            assertThat(previous).as("%s seeds code %s twice", file, m.group(2)).isNull();
        }
        assertThat(pairs).as("%s: ecommerce sku_snapshot rows found", file).isNotEmpty();
        return pairs;
    }

    private static String single(Pattern p, Path file, int group) throws IOException {
        return only(p, file).group(group);
    }

    private static Matcher only(Pattern p, Path file) throws IOException {
        Matcher m = p.matcher(read(file));
        assertThat(m.find()).as("%s: no match for %s", file, p.pattern()).isTrue();
        Matcher first = p.matcher(read(file));
        first.find();
        assertThat(m.find()).as("%s: more than one match for %s", file, p.pattern()).isFalse();
        return first;
    }

    /** {@code NAME=value} or {@code NAME='value'} at line start in the shell script. */
    private static String shellLiteral(String script, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + "='?([^'\\s]+)'?\\s*$").matcher(script);
        assertThat(m.find()).as("seed-wms.sh: literal assignment of %s", name).isTrue();
        return m.group(1);
    }

    private static String read(Path relative) throws IOException {
        return Files.readString(locate(relative), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Walks up from the Gradle test working directory (the module dir) to the repo root. */
    private static Path locate(Path relative) {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError(relative + " not found above " + Paths.get("").toAbsolutePath()
                + " — this check must not pass without reading it");
    }
}
