package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.port.OrgNodePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-492 / ADR-MONO-047 D5 — the fail-closed boundary.
 *
 * <p>The invariant under test is a security one, not a caching one: <b>a failure narrows
 * reach, it never widens it, and it is never cached.</b>
 */
class OrgNodeSubtreeResolverTest {

    private OrgNodePort port;
    private OrgNodeSubtreeResolver resolver;

    @BeforeEach
    void setUp() {
        port = mock(OrgNodePort.class);
        resolver = new OrgNodeSubtreeResolver(port, 60_000L);
    }

    @Test
    @DisplayName("subtree resolves → the node's tenants")
    void subtreeResolves() {
        when(port.subtreeTenantIds("n1")).thenReturn(List.of("acme-wms", "acme-erp"));
        assertThat(resolver.subtreeTenantIdsFailClosed("n1"))
                .containsExactlyInAnyOrder("acme-wms", "acme-erp");
    }

    @Test
    @DisplayName("authority down → EMPTY set; never '*', never all-tenants")
    void subtreeFailure_isEmptyNeverStar() {
        when(port.subtreeTenantIds("n1")).thenThrow(new DownstreamFailureException("account down", null));

        Set<String> resolved = resolver.subtreeTenantIdsFailClosed("n1");

        assertThat(resolved).isEmpty();
        assertThat(resolved).doesNotContain("*");
    }

    @Test
    @DisplayName("a failure is NOT cached — the next call retries the authority")
    void failureIsNeverCached() {
        when(port.subtreeTenantIds("n1"))
                .thenThrow(new DownstreamFailureException("transient", null))
                .thenReturn(List.of("acme-wms"));

        assertThat(resolver.subtreeTenantIdsFailClosed("n1")).isEmpty();
        // The outage ended; the very next call must see reach restored, not a memoised deny.
        assertThat(resolver.subtreeTenantIdsFailClosed("n1")).containsExactly("acme-wms");
        verify(port, times(2)).subtreeTenantIds("n1");
    }

    @Test
    @DisplayName("a success is cached within the TTL (one round-trip per burst)")
    void successIsCachedWithinTtl() {
        when(port.subtreeTenantIds("n1")).thenReturn(List.of("acme-wms"));

        resolver.subtreeTenantIdsFailClosed("n1");
        resolver.subtreeTenantIdsFailClosed("n1");

        verify(port, times(1)).subtreeTenantIds("n1");
    }

    @Test
    @DisplayName("an expired entry is not served — a stale success is as wrong as a cached failure")
    void expiredEntryIsNotServed() {
        OrgNodeSubtreeResolver instantExpiry = new OrgNodeSubtreeResolver(port, 0L);
        when(port.subtreeTenantIds("n1")).thenReturn(List.of("acme-wms"));

        instantExpiry.subtreeTenantIdsFailClosed("n1");
        instantExpiry.subtreeTenantIdsFailClosed("n1");

        verify(port, times(2)).subtreeTenantIds("n1");
    }

    @Test
    @DisplayName("a previously-cached success is dropped once the authority starts failing")
    void cachedSuccessIsEvictedOnFailure() {
        OrgNodeSubtreeResolver instantExpiry = new OrgNodeSubtreeResolver(port, 0L);
        when(port.subtreeTenantIds("n1"))
                .thenReturn(List.of("acme-wms"))
                .thenThrow(new DownstreamFailureException("account down", null));

        assertThat(instantExpiry.subtreeTenantIdsFailClosed("n1")).containsExactly("acme-wms");
        assertThat(instantExpiry.subtreeTenantIdsFailClosed("n1")).isEmpty();
    }

    @Test
    @DisplayName("null / blank node id → EMPTY, no round-trip")
    void nullNodeId_isEmpty() {
        assertThat(resolver.subtreeTenantIdsFailClosed(null)).isEmpty();
        assertThat(resolver.subtreeTenantIdsFailClosed("  ")).isEmpty();
        verify(port, times(0)).subtreeTenantIds(anyString());
    }

    @Test
    @DisplayName("effective ceiling resolves → carried through unchanged")
    void ceilingResolves() {
        when(port.effectiveCeiling("n1")).thenReturn(CeilingView.bounded(List.of("wms")));
        assertThat(resolver.effectiveCeilingFailClosed("n1").domains()).containsExactly("wms");
    }

    @Test
    @DisplayName("ceiling resolution fails → BOUNDED([]) (permits nothing), NEVER UNBOUNDED")
    void ceilingFailure_isBoundedEmptyNeverUnbounded() {
        when(port.effectiveCeiling("n1")).thenThrow(new DownstreamFailureException("account down", null));

        CeilingView ceiling = resolver.effectiveCeilingFailClosed("n1");

        assertThat(ceiling.isUnbounded()).isFalse();
        assertThat(ceiling.permitsNothing()).isTrue();
        assertThat(ceiling.permits("wms")).isFalse();
    }

    @Test
    @DisplayName("a null ceiling from the authority is treated as a failure, not as 'no ceiling'")
    void nullCeiling_failsClosed() {
        when(port.effectiveCeiling("n1")).thenReturn(null);
        assertThat(resolver.effectiveCeilingFailClosed("n1").permitsNothing()).isTrue();
    }

    @Test
    @DisplayName("UNBOUNDED is the intersection identity, not 'every known domain'")
    void unboundedPermitsADomainInventedTomorrow() {
        when(port.effectiveCeiling("n1")).thenReturn(CeilingView.unbounded());
        assertThat(resolver.effectiveCeilingFailClosed("n1").permits("a-domain-invented-tomorrow")).isTrue();
    }
}
