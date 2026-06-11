package com.example.security.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-MONO-029 — {@code RESOURCE_TAG} access condition (closed-enum, fail-safe, opt-in).
 */
@DisplayName("ResourceTagCondition — ADR-MONO-029 RESOURCE_TAG access condition")
class ResourceTagConditionTest {

    @Test
    @DisplayName("net-zero: no tag declared ⟹ not configured, any resource satisfies (no gate)")
    void unconfiguredIsNetZero() {
        ResourceTagCondition none = ResourceTagCondition.forbidden(null);
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.isSatisfiedBy(Set.of("protected"))).isTrue();
        assertThat(none.isSatisfiedBy(null)).isTrue(); // unconfigured short-circuits before fail-safe

        ResourceTagCondition blanks = ResourceTagCondition.forbidden(Arrays.asList("  ", null, ""));
        assertThat(blanks.isConfigured()).isFalse();
        assertThat(blanks.isSatisfiedBy(Set.of("anything"))).isTrue();

        assertThat(ResourceTagCondition.required(List.of()).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("deny-if-present: resource carrying a forbidden tag ⟹ denied; otherwise allowed")
    void denyIfPresent() {
        ResourceTagCondition c = ResourceTagCondition.forbidden(List.of("protected"));
        assertThat(c.isConfigured()).isTrue();
        assertThat(c.isSatisfiedBy(Set.of("protected"))).isFalse();             // carries forbidden → deny
        assertThat(c.isSatisfiedBy(Set.of("protected", "vip"))).isFalse();      // among others → deny
        assertThat(c.isSatisfiedBy(Set.of("vip"))).isTrue();                    // other tag → allow
        assertThat(c.isSatisfiedBy(Set.of())).isTrue();                         // no tags → allow (not protected)
    }

    @Test
    @DisplayName("deny-if-present: matching is case-insensitive + trimmed")
    void denyIfPresentCaseInsensitive() {
        ResourceTagCondition c = ResourceTagCondition.forbidden(List.of("  Protected  "));
        assertThat(c.isSatisfiedBy(Set.of("PROTECTED"))).isFalse();
        assertThat(c.isSatisfiedBy(Set.of(" protected "))).isFalse();
        assertThat(c.isSatisfiedBy(Set.of("protective"))).isTrue(); // not an exact tag
    }

    @Test
    @DisplayName("deny-if-present: ANY forbidden tag present denies (union within the type, not a combinator)")
    void denyIfPresentMultiple() {
        ResourceTagCondition c = ResourceTagCondition.forbidden(List.of("protected", "frozen"));
        assertThat(c.isSatisfiedBy(Set.of("frozen"))).isFalse();
        assertThat(c.isSatisfiedBy(Set.of("protected"))).isFalse();
        assertThat(c.isSatisfiedBy(Set.of("normal"))).isTrue();
    }

    @Test
    @DisplayName("require: resource must carry ALL required tags")
    void requireTag() {
        ResourceTagCondition one = ResourceTagCondition.required(List.of("approved"));
        assertThat(one.isSatisfiedBy(Set.of("approved"))).isTrue();
        assertThat(one.isSatisfiedBy(Set.of("approved", "vip"))).isTrue();
        assertThat(one.isSatisfiedBy(Set.of("pending"))).isFalse();
        assertThat(one.isSatisfiedBy(Set.of())).isFalse(); // lacks required → deny

        ResourceTagCondition both = ResourceTagCondition.required(List.of("approved", "reviewed"));
        assertThat(both.isSatisfiedBy(Set.of("approved", "reviewed"))).isTrue();
        assertThat(both.isSatisfiedBy(Set.of("approved"))).isFalse(); // missing one → deny
    }

    @Test
    @DisplayName("fail-safe: configured + null resource tags (unresolved) ⟹ denied (both modes)")
    void failSafeOnNullTags() {
        assertThat(ResourceTagCondition.forbidden(List.of("protected")).isSatisfiedBy(null)).isFalse();
        assertThat(ResourceTagCondition.required(List.of("approved")).isSatisfiedBy(null)).isFalse();
    }

    @Test
    @DisplayName("empty (known-untagged) vs null (unresolved): empty allows under deny-if-present, null denies")
    void emptyVsNull() {
        ResourceTagCondition c = ResourceTagCondition.forbidden(List.of("protected"));
        assertThat(c.isSatisfiedBy(Set.of())).isTrue();  // known to have no tags → allow
        assertThat(c.isSatisfiedBy(null)).isFalse();     // could not resolve → fail-safe deny
    }
}
