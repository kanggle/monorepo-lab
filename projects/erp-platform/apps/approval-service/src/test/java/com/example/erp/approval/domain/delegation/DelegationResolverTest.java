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
 * TASK-ERP-BE-017 adds the per-request scope: a REQUEST grant authorizes only its
 * matching requestId (the resolver's {@code coversRequest} re-check is the
 * authoritative in-domain filter over the SQL predicate). {@code STRICT_STUBS}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DelegationResolverTest {

    private static final String TENANT = "erp";
    private static final String R1 = "appr-1";
    private static final String R2 = "appr-2";
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");

    @Mock DelegationGrantRepository repo;
    @InjectMocks DelegationResolver resolver;

    private DelegationGrant globalGrant() {
        return DelegationGrant.create("dgr-1", TENANT, "emp-a", "emp-d",
                FROM, TO, null, DelegationScope.GLOBAL, null, "emp-a", FROM);
    }

    private DelegationGrant requestGrant(String requestId) {
        return DelegationGrant.create("dgr-rq", TENANT, "emp-a", "emp-d",
                FROM, TO, null, DelegationScope.REQUEST, requestId, "emp-a", FROM);
    }

    @Test
    @DisplayName("actor IS the stage approver → direct (no repo lookup)")
    void directApprover() {
        DelegationResolution r = resolver.resolve("emp-a", "emp-a", TENANT, R1, NOW);
        assertThat(r.authorized()).isTrue();
        assertThat(r.onBehalfOf()).isNull();
        assertThat(r.isDelegated()).isFalse();
        verify(repo, never()).findActiveGrant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("active GLOBAL grant A→D → delegated (onBehalfOf = A) for any request")
    void validGlobalDelegate() {
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, R1, NOW))
                .thenReturn(Optional.of(globalGrant()));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, R1, NOW);
        assertThat(r.authorized()).isTrue();
        assertThat(r.onBehalfOf()).isEqualTo("emp-a");
        assertThat(r.isDelegated()).isTrue();
    }

    @Test
    @DisplayName("REQUEST grant for R1 → delegated when acting on R1")
    void requestGrantAuthorizesMatching() {
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, R1, NOW))
                .thenReturn(Optional.of(requestGrant(R1)));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, R1, NOW);
        assertThat(r.authorized()).isTrue();
        assertThat(r.onBehalfOf()).isEqualTo("emp-a");
    }

    @Test
    @DisplayName("REQUEST grant for R1, acting on R2 → not authorized "
            + "(coversRequest re-check, defense-in-depth)")
    void requestGrantMismatchRefused() {
        // The query mistakenly returns the R1 grant; the resolver's coversRequest
        // re-check must still fail-close for R2.
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, R2, NOW))
                .thenReturn(Optional.of(requestGrant(R1)));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, R2, NOW);
        assertThat(r.authorized()).isFalse();
    }

    @Test
    @DisplayName("no grant → not authorized")
    void noGrant() {
        when(repo.findActiveGrant("emp-a", "emp-x", TENANT, R1, NOW))
                .thenReturn(Optional.empty());
        DelegationResolution r = resolver.resolve("emp-a", "emp-x", TENANT, R1, NOW);
        assertThat(r.authorized()).isFalse();
    }

    @Test
    @DisplayName("grant returned but expired at now (defense-in-depth isActiveAt re-check) → not authorized")
    void expiredGrant() {
        DelegationGrant expired = DelegationGrant.create("dgr-2", TENANT, "emp-a", "emp-d",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"), null, DelegationScope.GLOBAL, null,
                "emp-a", Instant.parse("2026-05-01T00:00:00Z"));
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, R1, NOW))
                .thenReturn(Optional.of(expired));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, R1, NOW);
        assertThat(r.authorized()).isFalse();
    }

    @Test
    @DisplayName("grant returned but REVOKED (isActiveAt re-check) → not authorized")
    void revokedGrant() {
        DelegationGrant revoked = globalGrant();
        revoked.revoke("emp-a", Instant.parse("2026-06-10T00:00:00Z"));
        when(repo.findActiveGrant("emp-a", "emp-d", TENANT, R1, NOW))
                .thenReturn(Optional.of(revoked));
        DelegationResolution r = resolver.resolve("emp-a", "emp-d", TENANT, R1, NOW);
        assertThat(r.authorized()).isFalse();
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
