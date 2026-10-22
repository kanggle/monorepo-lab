package com.wms.admin.application.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.repository.SettingRepository;
import com.wms.admin.domain.Setting;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the current low-stock threshold for the inventory read model —
 * TASK-BE-644 ({@code admin-service-api.md} § inventory, {@code domain-model.md}
 * § InventorySnapshot).
 *
 * <p>Low-stock is derived <em>at query time</em> as {@code availableQty < threshold}.
 * admin-service owns the setting table, so this is a same-DB PK lookup on every
 * dashboard request — a setting change applies to every snapshot row at once,
 * with no inventory event and no bulk recompute. (The stored flag this replaces
 * was computed at projection time against a hard-coded {@code <= 10}, so a
 * setting change never reached the console's badge or filter.)
 *
 * <p>Fallback is {@link #DEFAULT_THRESHOLD} — the seed default — when the
 * GLOBAL row is missing or its value is not a non-negative integer. Writes are
 * already schema-validated in {@link SettingsService}; the fallback is for a
 * fresh database without the seed and for a hand-edited row, and it must never
 * fail the dashboard request.
 */
@Component
public class LowStockThresholdReader {

    private static final Logger log = LoggerFactory.getLogger(LowStockThresholdReader.class);

    /** GLOBAL setting key (seed {@code db/seed/R__seed_dev_data.sql}). */
    public static final String KEY = "inventory.low_stock.default_threshold_qty";

    /** Seed default; used when the setting is missing or unreadable. */
    public static final int DEFAULT_THRESHOLD = 10;

    private final SettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    public LowStockThresholdReader(SettingRepository settingRepository, ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
    }

    /** The threshold to compare {@code availableQty} against with strict {@code <}. */
    public int currentThreshold() {
        Optional<Setting> setting = settingRepository.find(KEY, null);
        if (setting.isEmpty()) {
            return DEFAULT_THRESHOLD;
        }
        String raw = setting.get().valueJson();
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 0) {
                return node.intValue();
            }
        } catch (Exception e) {
            // fall through to the default below
        }
        log.warn("setting {} has a non-integer value {} — using default low-stock threshold {}",
                KEY, raw, DEFAULT_THRESHOLD);
        return DEFAULT_THRESHOLD;
    }

    /** Strict boundary: a row exactly at the threshold is not low-stock (matches inventory alerts). */
    public static boolean isLowStock(int availableQty, int threshold) {
        return availableQty < threshold;
    }
}
