package com.example.admin.application;

import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.port.TenantEntitledDomainsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-631 — the one entitlement predicate of set create / edit / grant: inside the tenant's effective entitled
 * domains → pass; outside → 422 DOMAIN_NOT_SUBSCRIBED; the authority unreachable → the 503 propagates (never «allow»).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DomainSetEntitlementGuardTest {

    @Mock TenantEntitledDomainsPort port;
    @InjectMocks DomainSetEntitlementGuard guard;

    @Test
    @DisplayName("every domain entitled → pass; one outside → 422; 'fan' is entitled through 'fan-platform'")
    void entitledAndNot() {
        when(port.entitledDomains("acme")).thenReturn(Set.of("wms", "fan-platform"));
        assertThatCode(() -> guard.requireEntitled("acme", List.of("wms", "fan"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireEntitled("acme", List.of("wms", "scm")))
                .isInstanceOf(DomainNotSubscribedException.class);
    }

    @Test
    @DisplayName("unknown tenant (empty entitlement) → 422")
    void emptyEntitlement_422() {
        when(port.entitledDomains("ghost")).thenReturn(Set.of());
        assertThatThrownBy(() -> guard.requireEntitled("ghost", List.of("wms")))
                .isInstanceOf(DomainNotSubscribedException.class);
    }

    @Test
    @DisplayName("🔴 entitlement read fails → DownstreamFailureException propagates (503, fail-closed)")
    void readFailure_propagates() {
        when(port.entitledDomains("acme")).thenThrow(new DownstreamFailureException("down", null));
        assertThatThrownBy(() -> guard.requireEntitled("acme", List.of("wms")))
                .isInstanceOf(DownstreamFailureException.class);
    }
}
