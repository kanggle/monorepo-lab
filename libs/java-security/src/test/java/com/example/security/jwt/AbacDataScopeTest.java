package com.example.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AbacDataScope — ADR-MONO-025 canonical data-scope claim reader")
class AbacDataScopeTest {

    @Test
    @DisplayName("data_scope (canonical) and org_scope (legacy alias) are dual-read and unioned")
    void dualReadsBothClaimNames() {
        AbacDataScope s = AbacDataScope.fromClaimValues(List.of("wh-1"), List.of("wh-2"));
        assertThat(s.tokens()).containsExactlyInAnyOrder("wh-1", "wh-2");
    }

    @Test
    @DisplayName("a JSON-array claim is parsed element-wise")
    void parsesArrayClaim() {
        AbacDataScope s = AbacDataScope.fromClaimValues(List.of("dept-a", "dept-b"), null);
        assertThat(s.tokens()).containsExactlyInAnyOrder("dept-a", "dept-b");
        assertThat(s.isUnrestricted()).isFalse();
    }

    @Test
    @DisplayName("a delimited-string claim is split on commas/whitespace; blanks dropped; trimmed")
    void parsesDelimitedStringClaim() {
        AbacDataScope s = AbacDataScope.fromClaimValues("wh-1, wh-2   wh-3", null);
        assertThat(s.tokens()).containsExactlyInAnyOrder("wh-1", "wh-2", "wh-3");
    }

    @Test
    @DisplayName("wildcard '*' ⟺ unrestricted (the producer's net-zero default for unscoped assignments)")
    void wildcardIsUnrestricted() {
        AbacDataScope s = AbacDataScope.fromClaimValues(List.of("*"), null);
        assertThat(s.isUnrestricted()).isTrue();
        assertThat(s.allows("anything-at-all")).isTrue();
    }

    @Test
    @DisplayName("a scoped set without '*' allows only listed tokens (deny-by-default)")
    void scopedSetDeniesOutside() {
        AbacDataScope s = AbacDataScope.fromClaimValues(List.of("wh-1", "wh-2"), null);
        assertThat(s.isUnrestricted()).isFalse();
        assertThat(s.allows("wh-1")).isTrue();
        assertThat(s.allows("wh-9")).isFalse();
    }

    @Test
    @DisplayName("empty / absent scope is NOT unrestricted — fail-closed (denies everything)")
    void emptyIsFailClosed() {
        AbacDataScope absent = AbacDataScope.fromClaimValues((Object) null, null);
        assertThat(absent.isEmpty()).isTrue();
        assertThat(absent.isUnrestricted()).isFalse();
        assertThat(absent.allows("wh-1")).isFalse();
        assertThat(absent.allows("*")).isFalse();

        AbacDataScope none = AbacDataScope.fromClaimValues();
        assertThat(none.isEmpty()).isTrue();
        assertThat(none.allows("wh-1")).isFalse();
    }

    @Test
    @DisplayName("null token argument never matches (defensive)")
    void nullTokenDenied() {
        AbacDataScope scoped = AbacDataScope.fromClaimValues(List.of("wh-1"), null);
        assertThat(scoped.allows(null)).isFalse();
    }
}
