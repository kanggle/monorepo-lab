package com.wms.admin.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.repository.SettingRepository;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** TASK-BE-644 — the threshold the inventory read model derives low-stock from. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class LowStockThresholdReaderTest {

    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    @Mock SettingRepository settingRepository;

    private LowStockThresholdReader reader;

    @BeforeEach
    void setUp() {
        reader = new LowStockThresholdReader(settingRepository, new ObjectMapper());
    }

    private void stored(String valueJson) {
        when(settingRepository.find(LowStockThresholdReader.KEY, null)).thenReturn(Optional.of(
                new Setting(LowStockThresholdReader.KEY, SettingScope.GLOBAL, null, valueJson,
                        "{\"type\":\"integer\"}", null, 1, NOW, "seed", NOW, "ops")));
    }

    @Test
    void returnsTheCurrentSettingValue() {
        stored("25");
        assertThat(reader.currentThreshold()).isEqualTo(25);
    }

    @Test
    void zeroIsAValidThreshold() {
        stored("0");
        assertThat(reader.currentThreshold()).isZero();
    }

    @Test
    void missingSetting_fallsBackToSeedDefault10() {
        when(settingRepository.find(LowStockThresholdReader.KEY, null)).thenReturn(Optional.empty());
        assertThat(reader.currentThreshold()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"25\"", "2.5", "-1", "true", "null", "{\"v\":25}", "{not json", "99999999999"})
    void invalidValue_fallsBackToSeedDefault10(String valueJson) {
        stored(valueJson);
        assertThat(reader.currentThreshold()).isEqualTo(LowStockThresholdReader.DEFAULT_THRESHOLD);
    }

    @Test
    void boundaryIsStrict_equalIsNotLowStock() {
        assertThat(LowStockThresholdReader.isLowStock(9, 10)).isTrue();
        assertThat(LowStockThresholdReader.isLowStock(10, 10)).isFalse();
        assertThat(LowStockThresholdReader.isLowStock(11, 10)).isFalse();
        assertThat(LowStockThresholdReader.isLowStock(0, 0)).isFalse();
    }
}
