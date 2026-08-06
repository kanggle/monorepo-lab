package com.example.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * TASK-BE-578 — the registration hint predicate.
 *
 * <p>This is the whole of the hint's logic, so it is where the OIDC
 * {@code prompt} parsing rules are pinned: whole-token comparison over a
 * space-delimited list, and {@code none} winning over {@code create}.
 */
@DisplayName("registration hint matcher (TASK-BE-578)")
class RegistrationHintRequestMatcherTest {

    private final RegistrationHintRequestMatcher matcher = new RegistrationHintRequestMatcher();

    private boolean matchesPrompt(String... promptValues) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        if (promptValues != null) {
            request.setParameter("prompt", promptValues);
        }
        return matcher.matches(request);
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("prompt=create")
        void plainCreate() {
            assertThat(matchesPrompt("create")).isTrue();
        }

        @Test
        @DisplayName("create alongside other values in the space-delimited list")
        void createInAList() {
            assertThat(matchesPrompt("login create")).isTrue();
            assertThat(matchesPrompt("create consent")).isTrue();
            assertThat(matchesPrompt("consent  create   login")).isTrue();
        }

        @Test
        @DisplayName("surrounding whitespace")
        void padded() {
            assertThat(matchesPrompt("  create  ")).isTrue();
        }
    }

    @Nested
    @DisplayName("declines")
    class Declines {

        @Test
        @DisplayName("no prompt parameter at all — the pre-BE-578 path stays untouched")
        void absent() {
            assertThat(matchesPrompt((String[]) null)).isFalse();
        }

        @Test
        @DisplayName("empty or blank prompt")
        void blank() {
            assertThat(matchesPrompt("")).isFalse();
            assertThat(matchesPrompt("   ")).isFalse();
        }

        @Test
        @DisplayName("another prompt value")
        void otherValue() {
            assertThat(matchesPrompt("login")).isFalse();
            assertThat(matchesPrompt("consent")).isFalse();
        }

        @Test
        @DisplayName("a token that merely CONTAINS create — substring matching would be wrong")
        void substringIsNotAMatch() {
            assertThat(matchesPrompt("created")).isFalse();
            assertThat(matchesPrompt("recreate")).isFalse();
            assertThat(matchesPrompt("create_account")).isFalse();
        }

        @Test
        @DisplayName("case does not fold — OIDC prompt values are case-sensitive")
        void caseSensitive() {
            assertThat(matchesPrompt("Create")).isFalse();
            assertThat(matchesPrompt("CREATE")).isFalse();
        }

        @Test
        @DisplayName("none wins over create: 'show no UI' beats 'show the signup form'")
        void noneBeatsCreate() {
            assertThat(matchesPrompt("none create")).isFalse();
            assertThat(matchesPrompt("create none")).isFalse();
        }

        @Test
        @DisplayName("none wins even when create arrives as a separate repeated parameter")
        void noneBeatsCreateAcrossRepeatedParams() {
            assertThat(matchesPrompt("create", "none")).isFalse();
            assertThat(matchesPrompt("none", "create")).isFalse();
        }
    }

    @Test
    @DisplayName("the hint is read from `prompt` only — no other parameter can trigger it")
    void onlyThePromptParameterIsRead() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        // A `state` or `scope` that happens to contain the word must not route the
        // user to the signup form. The hint has exactly one carrier.
        request.setParameter("state", "create");
        request.setParameter("scope", "openid create");
        request.setParameter("screen_hint", "create");

        assertThat(matcher.matches(request)).isFalse();
    }
}
