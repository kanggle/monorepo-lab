package com.example.order.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Fast-lane unit coverage for {@link SystemClientSubjectValidator} (TASK-BE-505) — the
 * subject allow-list that pins {@code /api/internal/**} to the reserved system client so an
 * ordinary CUSTOMER token (same issuer, valid signature) can no longer run the internal sweep.
 * Runs in the default {@code test} task (no Docker), independent of the flaky Testcontainers
 * {@code ConfirmPaidStaleIT} lane which covers the end-to-end wiring.
 */
class SystemClientSubjectValidatorTest {

    private static final String SYSTEM_CLIENT = "ecommerce-internal-services-client";

    private static Jwt jwtWithSubject(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        return jwt;
    }

    @Test
    @DisplayName("allow-listed system client-id subject → success")
    void allowListedSubject_succeeds() {
        var validator = new SystemClientSubjectValidator(Set.of(SYSTEM_CLIENT));
        assertThat(validator.validate(jwtWithSubject(SYSTEM_CLIENT)).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("ordinary CUSTOMER token (account-UUID subject) → rejected")
    void customerUuidSubject_rejected() {
        var validator = new SystemClientSubjectValidator(Set.of(SYSTEM_CLIENT));
        var result = validator.validate(jwtWithSubject("2f1c9a3e-0000-4444-8888-abcabcabcabc"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_token".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("a different platform's system client-id → rejected (allow-list is specific)")
    void otherClientSubject_rejected() {
        var validator = new SystemClientSubjectValidator(Set.of(SYSTEM_CLIENT));
        assertThat(validator.validate(jwtWithSubject("wms-internal-services-client")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("null subject → rejected (never NPEs, fail-closed)")
    void nullSubject_rejected() {
        var validator = new SystemClientSubjectValidator(Set.of(SYSTEM_CLIENT));
        assertThat(validator.validate(jwtWithSubject(null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("empty allow-list → rejects everyone (mis-config fails closed, does not reopen the gap)")
    void emptyAllowList_rejectsAll() {
        var validator = new SystemClientSubjectValidator(Set.of());
        assertThat(validator.validate(jwtWithSubject(SYSTEM_CLIENT)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("multiple allow-listed clients → each admitted")
    void multipleAllowListed_eachAdmitted() {
        var validator = new SystemClientSubjectValidator(Set.of(SYSTEM_CLIENT, "ecommerce-batch-client"));
        assertThat(validator.validate(jwtWithSubject(SYSTEM_CLIENT)).hasErrors()).isFalse();
        assertThat(validator.validate(jwtWithSubject("ecommerce-batch-client")).hasErrors()).isFalse();
        assertThat(validator.validate(jwtWithSubject("someone-else")).hasErrors()).isTrue();
    }
}
