package com.example.fanplatform.artist.domain.agency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-MONO-748 — the normalisation rule and the agency's own invariants. */
class AgencyTest {

    @ParameterizedTest(name = "[{index}] ''{0}'' → ''{1}''")
    @CsvSource(delimiter = '|', value = {
            "Aurora Entertainment|Aurora Entertainment",
            // quoted: @CsvSource trims unquoted values, which would test nothing here
            "'  Aurora Entertainment  '|Aurora Entertainment",
            "'Aurora    Entertainment'|Aurora Entertainment",
            "Aurora\tEntertainment|Aurora Entertainment",
            // case is PRESERVED — the rule does not fold it
            "AURORA ENTERTAINMENT|AURORA ENTERTAINMENT",
            "SM|SM",
    })
    void normalizeName_collapsesWhitespaceAndKeepsCase(String raw, String expected) {
        assertThat(Agency.normalizeName(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("newlines / CR / form feed / vertical tab collapse like the V4 SQL class [ \\t\\n\\r\\f\\v]")
    void normalizeName_allAsciiWhitespaceKinds() {
        assertThat(Agency.normalizeName("\nNova\r\n\fSound\u000B")).isEqualTo("Nova Sound");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void normalizeName_blankIsNoAgency(String raw) {
        assertThat(Agency.normalizeName(raw)).isNull();
    }

    @Test
    @DisplayName("Edge Case: «SM» and «SM Entertainment» are NOT the same name — no auto-merge")
    void normalizeName_doesNotMergeDifferentNames() {
        assertThat(Agency.normalizeName("SM")).isNotEqualTo(Agency.normalizeName("SM Entertainment"));
        assertThat(Agency.normalizeName("Aurora")).isNotEqualTo(Agency.normalizeName("AURORA"));
    }

    @Test
    void create_storesNormalisedNameActiveNoSeller() {
        Agency a = Agency.create(AgencyId.of("ag-1"), "fan-platform", "  Nova   Sound ");
        assertThat(a.getName()).isEqualTo("Nova Sound");
        assertThat(a.getStatus()).isEqualTo(AgencyStatus.ACTIVE);
        assertThat(a.getStoreSellerId()).isNull();
    }

    @Test
    void create_blankNameRejected() {
        assertThatThrownBy(() -> Agency.create(AgencyId.of("ag-1"), "fan-platform", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_tooLongNameRejected() {
        assertThatThrownBy(() -> Agency.create(AgencyId.of("ag-1"), "fan-platform", "x".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archived_isFrozen() {
        Agency a = Agency.create(AgencyId.of("ag-1"), "fan-platform", "Nova Sound");
        a.archive();
        assertThat(a.isActive()).isFalse();
        assertThatThrownBy(() -> a.rename("Other")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> a.linkStoreSeller("seller-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(a::archive).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linkStoreSeller_setsAndClears() {
        Agency a = Agency.create(AgencyId.of("ag-1"), "fan-platform", "Nova Sound");
        a.linkStoreSeller("seller-1");
        assertThat(a.getStoreSellerId()).isEqualTo("seller-1");
        a.linkStoreSeller(null);
        assertThat(a.getStoreSellerId()).isNull();
        assertThatThrownBy(() -> a.linkStoreSeller("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
