package com.example.account.domain.orgnode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-491 (ADR-MONO-047 § D2/D3): the ceiling algebra.
 *
 * <p>The whole point of this class is that {@code UNBOUNDED} and {@code BOUNDED(∅)} are
 * <b>opposites</b>, and that {@code UNBOUNDED} is the intersection <b>identity</b> rather
 * than "the set of all domains known today". Both are easy to get wrong and both fail
 * silently and late, so both are pinned here.
 */
@DisplayName("EntitlementCeiling — deny-only ceiling algebra (ADR-MONO-047 D2)")
class EntitlementCeilingTest {

    @Nested
    @DisplayName("UNBOUNDED is the identity, NOT 'all known domains'")
    class UnboundedIsIdentity {

        @Test
        @DisplayName("unbounded ∩ x = x  and  x ∩ unbounded = x (both directions)")
        void identityElement() {
            EntitlementCeiling bounded = EntitlementCeiling.bounded(List.of("wms", "erp"));

            assertThat(EntitlementCeiling.unbounded().intersect(bounded)).isEqualTo(bounded);
            assertThat(bounded.intersect(EntitlementCeiling.unbounded())).isEqualTo(bounded);
            assertThat(EntitlementCeiling.unbounded().intersect(EntitlementCeiling.unbounded()))
                    .isEqualTo(EntitlementCeiling.unbounded());
        }

        @Test
        @DisplayName("unbounded permits a domain that did not exist when it was created")
        void permitsFutureDomains() {
            // If UNBOUNDED were encoded as "the set of all currently-known domains", a domain
            // added later would be silently EXCLUDED from every legacy node — a bug that would
            // surface months afterwards as "why can't acme-corp subscribe to the new domain".
            assertThat(EntitlementCeiling.unbounded().permits("a-domain-invented-tomorrow")).isTrue();
        }

        @Test
        @DisplayName("unbounded carries no domain set (single canonical encoding of the identity)")
        void unboundedCarriesNoDomains() {
            assertThat(EntitlementCeiling.unbounded().domains()).isEmpty();
            assertThatThrownBy(() ->
                    new EntitlementCeiling(EntitlementCeiling.Mode.UNBOUNDED, Set.of("wms")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("BOUNDED(∅) permits NOTHING — the opposite of UNBOUNDED")
    class EmptyBoundedIsFailClosed {

        @Test
        @DisplayName("bounded(∅) is not equal to unbounded, and permits no domain")
        void emptyIsNotUnbounded() {
            EntitlementCeiling empty = EntitlementCeiling.bounded(List.of());

            assertThat(empty).isNotEqualTo(EntitlementCeiling.unbounded());
            assertThat(empty.isUnbounded()).isFalse();
            assertThat(empty.permits("wms")).isFalse();
            assertThat(EntitlementCeiling.unbounded().permits("wms")).isTrue();
        }

        @Test
        @DisplayName("bounded(∅) annihilates any ceiling it meets (a company locked out of everything)")
        void emptyAnnihilates() {
            EntitlementCeiling empty = EntitlementCeiling.bounded(List.of());
            EntitlementCeiling wms = EntitlementCeiling.bounded(List.of("wms"));

            assertThat(empty.intersect(wms).permits("wms")).isFalse();
            assertThat(wms.intersect(empty).permits("wms")).isFalse();
        }
    }

    @Nested
    @DisplayName("intersect")
    class Intersect {

        @Test
        @DisplayName("bounded ∩ bounded = set intersection")
        void boundedIntersection() {
            EntitlementCeiling a = EntitlementCeiling.bounded(List.of("wms", "erp", "scm"));
            EntitlementCeiling b = EntitlementCeiling.bounded(List.of("erp", "scm", "finance"));

            assertThat(a.intersect(b).domains()).containsExactlyInAnyOrder("erp", "scm");
        }

        @Test
        @DisplayName("commutative and associative, so a chain folds identically in either direction")
        void commutativeAndAssociative() {
            EntitlementCeiling a = EntitlementCeiling.bounded(List.of("wms", "erp", "scm"));
            EntitlementCeiling b = EntitlementCeiling.bounded(List.of("erp", "scm"));
            EntitlementCeiling c = EntitlementCeiling.bounded(List.of("scm", "finance"));

            assertThat(a.intersect(b)).isEqualTo(b.intersect(a));
            assertThat(a.intersect(b).intersect(c)).isEqualTo(a.intersect(b.intersect(c)));
        }

        @Test
        @DisplayName("intersection only ever narrows — never grants")
        void neverGrants() {
            EntitlementCeiling a = EntitlementCeiling.bounded(List.of("wms"));
            EntitlementCeiling b = EntitlementCeiling.bounded(List.of("erp"));

            assertThat(a.intersect(b).permits("wms")).isFalse();
            assertThat(a.intersect(b).permits("erp")).isFalse();
        }
    }

    @Nested
    @DisplayName("isSubsetOf — the child ⊆ parent write invariant (I3)")
    class SubsetOf {

        @Test
        @DisplayName("everything is a subset of unbounded (it is the top element)")
        void everythingIsSubsetOfUnbounded() {
            assertThat(EntitlementCeiling.bounded(List.of("wms")).isSubsetOf(EntitlementCeiling.unbounded())).isTrue();
            assertThat(EntitlementCeiling.bounded(List.of()).isSubsetOf(EntitlementCeiling.unbounded())).isTrue();
            assertThat(EntitlementCeiling.unbounded().isSubsetOf(EntitlementCeiling.unbounded())).isTrue();
        }

        @Test
        @DisplayName("an UNBOUNDED child under a BOUNDED parent is a violation (it would widen past the bound)")
        void unboundedChildUnderBoundedParentIsRejected() {
            assertThat(EntitlementCeiling.unbounded()
                    .isSubsetOf(EntitlementCeiling.bounded(List.of("wms")))).isFalse();
        }

        @Test
        @DisplayName("bounded ⊆ bounded is set containment")
        void boundedContainment() {
            EntitlementCeiling child = EntitlementCeiling.bounded(List.of("wms"));
            EntitlementCeiling parent = EntitlementCeiling.bounded(List.of("wms", "erp"));

            assertThat(child.isSubsetOf(parent)).isTrue();
            assertThat(parent.isSubsetOf(child)).isFalse();
            assertThat(EntitlementCeiling.bounded(List.of()).isSubsetOf(child)).isTrue();
        }
    }

    @Nested
    @DisplayName("storage encoding round-trip (ceiling_mode + ceiling_domains)")
    class StorageEncoding {

        @Test
        @DisplayName("UNBOUNDED round-trips with an empty CSV")
        void unboundedRoundTrip() {
            EntitlementCeiling c = EntitlementCeiling.unbounded();
            assertThat(c.domainsCsv()).isEmpty();
            assertThat(EntitlementCeiling.fromStorage("UNBOUNDED", "")).isEqualTo(c);
            // A stale/garbage CSV under UNBOUNDED must not resurrect a domain set.
            assertThat(EntitlementCeiling.fromStorage("UNBOUNDED", "wms,erp")).isEqualTo(c);
        }

        @Test
        @DisplayName("BOUNDED with an EMPTY csv is the empty set, NOT unbounded")
        void emptyCsvIsEmptySetNotUnbounded() {
            EntitlementCeiling parsed = EntitlementCeiling.fromStorage("BOUNDED", "");

            assertThat(parsed.isUnbounded()).isFalse();
            assertThat(parsed.permits("wms")).isFalse();
            assertThat(parsed).isEqualTo(EntitlementCeiling.bounded(List.of()));
        }

        @Test
        @DisplayName("BOUNDED round-trips through a canonical, sorted CSV")
        void boundedRoundTrip() {
            EntitlementCeiling c = EntitlementCeiling.bounded(List.of("wms", "erp"));

            assertThat(c.domainsCsv()).isEqualTo("erp,wms"); // canonical order
            assertThat(EntitlementCeiling.fromStorage("BOUNDED", c.domainsCsv())).isEqualTo(c);
        }
    }
}
