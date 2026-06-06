package com.example.security.infrastructure.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaxMindGeoLookup 단위 테스트")
class MaxMindGeoLookupUnitTest {

    @Test
    @DisplayName("DB 없이 init() 호출 → isAvailable() false")
    void init_noDb_isNotAvailable() {
        MaxMindGeoLookup lookup = new MaxMindGeoLookup("");
        lookup.init();

        assertThat(lookup.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("DB 없음 상태에서 resolve() → empty")
    void resolve_whenNotAvailable_returnsEmpty() {
        MaxMindGeoLookup lookup = new MaxMindGeoLookup("");
        lookup.init();

        assertThat(lookup.resolve("1.2.3.4")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("null IP → empty (DB 상태 무관)")
    void resolve_nullIp_returnsEmpty() {
        MaxMindGeoLookup lookup = new MaxMindGeoLookup("");
        lookup.init();

        assertThat(lookup.resolve(null)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("공백 IP → empty")
    void resolve_blankIp_returnsEmpty() {
        MaxMindGeoLookup lookup = new MaxMindGeoLookup("");
        lookup.init();

        assertThat(lookup.resolve("   ")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("마스킹된 IP ('1.2.3.*') → empty")
    void resolve_maskedIp_returnsEmpty() {
        MaxMindGeoLookup lookup = new MaxMindGeoLookup("");
        lookup.init();

        assertThat(lookup.resolve("1.2.3.*")).isEqualTo(Optional.empty());
    }
}
