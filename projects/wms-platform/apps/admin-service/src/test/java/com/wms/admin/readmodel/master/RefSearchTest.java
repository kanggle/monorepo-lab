package com.wms.admin.readmodel.master;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TASK-BE-630 — parameter shaping for the § 1.7 master-ref search. */
class RefSearchTest {

    @Test
    @DisplayName("blank or absent q is no filter (null), not a match-everything '%%'")
    void likePattern_blank_isNull() {
        assertThat(RefSearch.likePattern(null)).isNull();
        assertThat(RefSearch.likePattern("")).isNull();
        assertThat(RefSearch.likePattern("   ")).isNull();
    }

    @Test
    @DisplayName("q is trimmed, lower-cased and wrapped as a substring pattern")
    void likePattern_wrapsLowerCase() {
        assertThat(RefSearch.likePattern("  WH01 ")).isEqualTo("%wh01%");
    }

    @Test
    @DisplayName("% _ \\ are escaped so they match literally")
    void likePattern_escapesWildcards() {
        assertThat(RefSearch.likePattern("WH_1")).isEqualTo("%wh\\_1%");
        assertThat(RefSearch.likePattern("50%")).isEqualTo("%50\\%%");
        assertThat(RefSearch.likePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    @DisplayName("status: blank is no filter")
    void status_blank_isNull() {
        assertThat(RefSearch.status(" ")).isNull();
        assertThat(RefSearch.status(" ACTIVE ")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("partner types: the IN list is never empty; anyType carries 'no filter'")
    void types_neverEmpty() {
        assertThat(RefSearch.anyType(null)).isTrue();
        assertThat(RefSearch.types(null)).containsExactly("");
        assertThat(RefSearch.anyType(Arrays.asList(" ", null))).isTrue();
        assertThat(RefSearch.types(Arrays.asList(" ", null))).containsExactly("");
        assertThat(RefSearch.anyType(List.of("SUPPLIER", " BOTH"))).isFalse();
        assertThat(RefSearch.types(List.of("SUPPLIER", " BOTH"))).containsExactly("SUPPLIER", "BOTH");
    }
}
