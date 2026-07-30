package com.example.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The promoted mechanism, tested in isolation from any project's path data (ADR-MONO-058 § D5).
 *
 * <p>{@code PublicPathSet} holds no path string of its own — every set here is a synthetic fixture, not
 * a copy of any service's real {@code PublicPaths} data (those stay pinned in each service's own tests).
 */
@DisplayName("PublicPathSet — the EXACT/PREFIXES matching mechanism, mechanism only")
class PublicPathSetTest {

    private static final Set<String> EXACT = Set.of("/actuator/health", "/actuator/info");
    private static final Set<String> PREFIXES = Set.of("/actuator/health/");

    private final PublicPathSet paths = PublicPathSet.of(EXACT, PREFIXES);

    @Nested
    @DisplayName("isPublic(String)")
    class IsPublicString {

        @Test
        @DisplayName("an exact-match path is public")
        void exactMatch() {
            assertThat(paths.isPublic("/actuator/health")).isTrue();
            assertThat(paths.isPublic("/actuator/info")).isTrue();
        }

        @Test
        @DisplayName("a path under a prefix is public — probed with a prefix that has no EXACT twin")
        void prefixMatch() {
            // /actuator/health/ has no bare EXACT entry in this fixture (unlike the real service copies,
            // where /actuator/health is ALSO in EXACT) — so this genuinely exercises the PREFIXES branch,
            // not a pass-through of the EXACT check.
            assertThat(paths.isPublic("/actuator/health/liveness")).isTrue();
            assertThat(paths.isPublic("/actuator/health/readiness")).isTrue();
        }

        @Test
        @DisplayName("the bare prefix WITH its trailing slash is public")
        void prefixItself() {
            assertThat(paths.isPublic("/actuator/health/")).isTrue();
        }

        @Test
        @DisplayName("a null path is not public")
        void nullPath() {
            assertThat(paths.isPublic((String) null)).isFalse();
        }

        @Test
        @DisplayName("a non-matching path is not public")
        void nonMatchingPath() {
            assertThat(paths.isPublic("/actuator/env")).isFalse();
            assertThat(paths.isPublic("/actuator/heapdump")).isFalse();
            assertThat(paths.isPublic("/api/community/posts")).isFalse();
        }

        @Test
        @DisplayName("an empty EXACT/PREFIXES set matches nothing")
        void emptySets() {
            PublicPathSet empty = PublicPathSet.of(Set.of(), Set.of());
            assertThat(empty.isPublic("/actuator/health")).isFalse();
            assertThat(empty.isPublic("/anything")).isFalse();
        }
    }

    @Nested
    @DisplayName("isPublic(HttpServletRequest)")
    class IsPublicRequest {

        @Test
        @DisplayName("delegates to isPublic(String) using the request URI")
        void delegatesToRequestUri() {
            MockHttpServletRequest publicRequest = new MockHttpServletRequest("GET", "/actuator/health");
            publicRequest.setRequestURI("/actuator/health");
            assertThat(paths.isPublic(publicRequest)).isTrue();

            MockHttpServletRequest privateRequest = new MockHttpServletRequest("GET", "/api/community/posts");
            privateRequest.setRequestURI("/api/community/posts");
            assertThat(paths.isPublic(privateRequest)).isFalse();
        }
    }

    @Nested
    @DisplayName("of(Set, Set) — construction contract")
    class Construction {

        @Test
        @DisplayName("a prefix not ending in '/' throws at construction — fail fast, not a silent no-match")
        void malformedPrefixThrows() {
            assertThatThrownBy(() -> PublicPathSet.of(Set.of(), Set.of("/actuator/health")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("/actuator/health");
        }

        @Test
        @DisplayName("exact() and prefixes() return what was supplied")
        void accessorsReturnSuppliedData() {
            assertThat(paths.exact()).isEqualTo(EXACT);
            assertThat(paths.prefixes()).isEqualTo(PREFIXES);
        }

        @Test
        @DisplayName("mutating the caller's own set after construction does not affect the instance")
        void defensiveCopyIsolatesFromCallerMutation() {
            var mutableExact = new java.util.HashSet<>(Set.of("/actuator/health"));
            var mutablePrefixes = new java.util.HashSet<>(Set.of("/actuator/health/"));
            PublicPathSet fromMutable = PublicPathSet.of(mutableExact, mutablePrefixes);

            mutableExact.add("/actuator/env");
            mutablePrefixes.add("/api/");

            assertThat(fromMutable.isPublic("/actuator/env"))
                    .as("PublicPathSet must hold its own copy, not alias the caller's set")
                    .isFalse();
            assertThat(fromMutable.isPublic("/api/community/posts"))
                    .as("same for the prefixes set")
                    .isFalse();
        }

        @Test
        @DisplayName("null exact or prefixes throws NullPointerException")
        void nullArgumentsRejected() {
            assertThatThrownBy(() -> PublicPathSet.of(null, Set.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> PublicPathSet.of(Set.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
