package com.wms.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.admin.domain.error.SettingImmutableFieldException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettingInvariantTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void global_withWarehouseId_rejected() {
        assertThatThrownBy(() -> new Setting("k", SettingScope.GLOBAL, UUID.randomUUID(),
                "24", "{\"type\":\"integer\"}", null, 0L, NOW, "sys", NOW, "sys"))
                .isInstanceOf(SettingImmutableFieldException.class);
    }

    @Test
    void warehouse_withoutWarehouseId_rejected() {
        assertThatThrownBy(() -> new Setting("k", SettingScope.WAREHOUSE, null,
                "24", "{\"type\":\"integer\"}", null, 0L, NOW, "sys", NOW, "sys"))
                .isInstanceOf(SettingImmutableFieldException.class);
    }

    @Test
    void global_withNullWarehouse_succeeds() {
        Setting s = new Setting("k", SettingScope.GLOBAL, null,
                "24", "{\"type\":\"integer\"}", null, 0L, NOW, "sys", NOW, "sys");
        assertThat(s.scope()).isEqualTo(SettingScope.GLOBAL);
        assertThat(s.warehouseId()).isNull();
    }

    @Test
    void warehouse_withWarehouseId_succeeds() {
        UUID wh = UUID.randomUUID();
        Setting s = new Setting("k", SettingScope.WAREHOUSE, wh,
                "24", "{\"type\":\"integer\"}", null, 0L, NOW, "sys", NOW, "sys");
        assertThat(s.warehouseId()).isEqualTo(wh);
    }

    @Test
    void withValue_preservesKeyScopeWarehouseAndSchema() {
        Setting s = new Setting("k", SettingScope.GLOBAL, null,
                "24", "{\"type\":\"integer\"}", "desc", 0L, NOW, "sys", NOW, "sys");
        Setting updated = s.withValue("36", NOW.plusSeconds(60), "admin");
        assertThat(updated.key()).isEqualTo("k");
        assertThat(updated.scope()).isEqualTo(SettingScope.GLOBAL);
        assertThat(updated.warehouseId()).isNull();
        assertThat(updated.schemaJson()).isEqualTo("{\"type\":\"integer\"}");
        assertThat(updated.valueJson()).isEqualTo("36");
        assertThat(updated.updatedBy()).isEqualTo("admin");
    }

    @Test
    void create_requiresNonNullKeyScopeValueAndSchema() {
        assertThatThrownBy(() -> new Setting(null, SettingScope.GLOBAL, null,
                "24", "{\"type\":\"integer\"}", null, 0L, NOW, "sys", NOW, "sys"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Setting("k", SettingScope.GLOBAL, null,
                null, "{\"type\":\"integer\"}", null, 0L, NOW, "sys", NOW, "sys"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Setting("k", SettingScope.GLOBAL, null,
                "24", null, null, 0L, NOW, "sys", NOW, "sys"))
                .isInstanceOf(NullPointerException.class);
    }
}
