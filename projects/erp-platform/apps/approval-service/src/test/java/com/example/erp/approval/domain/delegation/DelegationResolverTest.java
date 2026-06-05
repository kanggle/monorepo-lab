package com.example.erp.approval.domain.delegation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DelegationResolver} — the transition-time resolution of
 * an acting principal against the current stage's approver (TASK-ERP-BE-013):
 * direct approver / valid delegate / expired-window / REVOKED / no-grant.
 * {@code STRICT_STUBS}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DelegationResolverTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    @Mock DelegationGrantRepository repo;
    @InjectMocks DelegationResolver resolver;

    private DelegationGrant activeGrant() {
        return DelegationGrant.create("dgr-1", TENANT, "emp-a", "emp-d",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"), null, "emp-a",
                Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    @DisplayName("actor IS the stage approver → direct (no repo lookup)")
    void directApprover() {
        DelegationResolution r = resolver.resolve("emp-a", "emp-a", TENANT, NOW);
        assertThat(r.authorized()).isTrue();
        assertThat(r.onBehalfOf()).isNull();
        assertThat(r.isDelegated()).isFalse();
        verify(repo, never()).findActiveGrant(any(), any(), any(), any());
    }

    @Test
    @DisplayName("active grant A→D → delegated (onBehalfOf = A)")
    void validDelegate() {
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, NOW))
                .thenReturn(Optional.of(activeGrant()));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, NOW);
        assertThat(r.authorized()).isTrue();
        assertThat(r.onBehalfOf()).isEqualTo("emp-a");
        assertThat(r.isDelegated()).isTrue();
    }

    @Test
    @DisplayName("no grant → not authorized")
    void noGrant() {
        when(repo.findActiveGrant("emp-a", "emp-x", TENANT, NOW))
                .thenReturn(Optional.empty());
        DelegationResolution r = resolver.resolve("emp-a", "emp-x", TENANT, NOW);
        assertThat(r.authorized()).isFalse();
    }

    @Test
    @DisplayName("grant returned but expired at now (defense-in-depth isActiveAt re-check) → not authorized")
    void expiredGrant() {
        DelegationGrant expired = DelegationGrant.create("dgr-2", TENANT, "emp-a", "emp-d",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"), null, "emp-a",
                Instant.parse("2026-05-01T00:00:00Z"));
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, NOW))
                .thenReturn(Optional.of(expired));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, NOW);
        assertThat(r.authorized()).isFalse();
    }

    @Test
    @DisplayName("grant returned but REVOKED (isActiveAt re-check) → not authorized")
    void revokedGrant() {
        DelegationGrant revoked = activeGrant();
        revoked.revoke("emp-a", Instant.parse("2026-06-10T00:00:00Z"));
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, NOW))
                .thenReturn(Optional.of(revoked));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, NOW);
        assertThat(r.authorized()).isFalse();
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
