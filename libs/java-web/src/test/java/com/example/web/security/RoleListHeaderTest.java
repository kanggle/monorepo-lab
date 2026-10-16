package com.example.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleListHeader — the one comparison rule for a comma-joined role header")
class RoleListHeaderTest {

    @Test
    @DisplayName("parse: split on comma, trim, drop empties, upper-case (ROOT), de-duplicate in header order")
    void parse() {
        assertThat(RoleListHeader.parse(" role_a , ROLE_B,,role_a ,  "))
                .containsExactly("ROLE_A", "ROLE_B");
        assertThat(RoleListHeader.parse("ROLE_A")).containsExactly("ROLE_A");
    }

    @Test
    @DisplayName("parse: null / empty / blank header → no role")
    void parse_empty() {
        assertThat(RoleListHeader.parse(null)).isEmpty();
        assertThat(RoleListHeader.parse("")).isEmpty();
        assertThat(RoleListHeader.parse("  ,  ")).isEmpty();
    }

    @Test
    @DisplayName("holdsAny: whole-element membership — a multi-role header holds each of its roles")
    void holdsAny_membership() {
        assertThat(RoleListHeader.holdsAny("ROLE_X,ROLE_A,ROLE_Y", "ROLE_A")).isTrue();
        assertThat(RoleListHeader.holdsAny("ROLE_X,ROLE_Y", "ROLE_A", "ROLE_Y")).isTrue();
        assertThat(RoleListHeader.holdsAny("ROLE_X,ROLE_Y", List.of("ROLE_A", "ROLE_B"))).isFalse();
    }

    @Test
    @DisplayName("holdsAny: case is ignored on both sides (the rule one sibling copy used to break)")
    void holdsAny_caseInsensitive() {
        assertThat(RoleListHeader.holdsAny("role_a", "ROLE_A")).isTrue();
        assertThat(RoleListHeader.holdsAny("ROLE_A", "role_a")).isTrue();
    }

    @Test
    @DisplayName("holdsAny: a substring is not membership, in either direction")
    void holdsAny_noSubstring() {
        assertThat(RoleListHeader.holdsAny("ROLE_AX", "ROLE_A")).isFalse();
        assertThat(RoleListHeader.holdsAny("ROLE_A", "ROLE_AX")).isFalse();
        assertThat(RoleListHeader.holdsAny("XROLE_A", "ROLE_A")).isFalse();
    }

    @Test
    @DisplayName("holdsAny: fails closed — null/blank header, null/empty/blank wanted roles")
    void holdsAny_failsClosed() {
        assertThat(RoleListHeader.holdsAny(null, "ROLE_A")).isFalse();
        assertThat(RoleListHeader.holdsAny("", "ROLE_A")).isFalse();
        assertThat(RoleListHeader.holdsAny("ROLE_A", (String[]) null)).isFalse();
        assertThat(RoleListHeader.holdsAny("ROLE_A", Set.of())).isFalse();
        assertThat(RoleListHeader.holdsAny("ROLE_A", (java.util.Collection<String>) null)).isFalse();
        assertThat(RoleListHeader.holdsAny("ROLE_A", "  ")).isFalse();
    }

    @Test
    @DisplayName("parse result is immutable")
    void parse_immutable() {
        Set<String> parsed = RoleListHeader.parse("ROLE_A");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parsed.add("ROLE_B"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
