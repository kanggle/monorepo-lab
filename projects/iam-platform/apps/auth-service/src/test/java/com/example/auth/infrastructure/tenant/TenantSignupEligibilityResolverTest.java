package com.example.auth.infrastructure.tenant;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.AccountServicePort.TenantLookupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-581 — the signup-eligibility predicate.
 *
 * <p>The predicate mirrors account-service's {@code ActiveTenantGuard} (row exists AND status
 * is ACTIVE). These cases pin both halves, because a predicate that only checks existence is
 * green on today's defect and still wrong for a suspended tenant — and the suspended case
 * arrives as a 403, which never looks like the 404 the console produces.
 */
class TenantSignupEligibilityResolverTest {

    private AccountServicePort accountServicePort;
    private TenantSignupEligibilityResolver resolver;

    @BeforeEach
    void setUp() {
        accountServicePort = mock(AccountServicePort.class);
        resolver = new TenantSignupEligibilityResolver(accountServicePort);
    }

    @Test
    @DisplayName("BITE — a tenant with no row (the console's reserved `iam` slug) is not offered")
    void tenantWithNoRowIsNotOffered() {
        when(accountServicePort.getTenant("iam")).thenReturn(Optional.empty());

        assertThat(resolver.isSignupOffered("iam")).isFalse();
    }

    @Test
    @DisplayName("A tenant that exists but is SUSPENDED is not offered (403, not 404)")
    void suspendedTenantIsNotOffered() {
        when(accountServicePort.getTenant("acme-corp"))
                .thenReturn(Optional.of(new TenantLookupResult("B2B_ENTERPRISE", "SUSPENDED")));

        assertThat(resolver.isSignupOffered("acme-corp")).isFalse();
    }

    @Test
    @DisplayName("CONTROL — an existing ACTIVE tenant is offered")
    void activeTenantIsOffered() {
        when(accountServicePort.getTenant("ecommerce"))
                .thenReturn(Optional.of(new TenantLookupResult("B2C", "ACTIVE")));

        assertThat(resolver.isSignupOffered("ecommerce")).isTrue();
    }

    @Test
    @DisplayName("The ADR-002 platform-scope sentinel `*` is not a tenant and is never offered")
    void platformScopeSentinelIsNotOffered() {
        assertThat(resolver.isSignupOffered("*")).isFalse();
        verify(accountServicePort, times(0)).getTenant(any());
    }

    @Test
    @DisplayName("An account-service outage fails OPEN — a consumer surface must not silently "
            + "lose its signup link during an outage")
    void outageFailsOpen() {
        when(accountServicePort.getTenant("ecommerce"))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertThat(resolver.isSignupOffered("ecommerce")).isTrue();
    }

    @Test
    @DisplayName("Only eligible answers are memoised — an ineligible tenant is re-asked, so a "
            + "later provisioning is picked up without a restart")
    void ineligibleAnswersAreNotCached() {
        when(accountServicePort.getTenant("later-tenant"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new TenantLookupResult("B2C", "ACTIVE")));

        assertThat(resolver.isSignupOffered("later-tenant")).isFalse();
        assertThat(resolver.isSignupOffered("later-tenant")).isTrue();
        verify(accountServicePort, times(2)).getTenant("later-tenant");
    }

    @Test
    @DisplayName("An eligible answer is memoised — the second call makes no network request")
    void eligibleAnswersAreCached() {
        when(accountServicePort.getTenant("fan-platform"))
                .thenReturn(Optional.of(new TenantLookupResult("B2C", "ACTIVE")));

        assertThat(resolver.isSignupOffered("fan-platform")).isTrue();
        assertThat(resolver.isSignupOffered("fan-platform")).isTrue();

        verify(accountServicePort, times(1)).getTenant("fan-platform");
        assertThat(resolver.cachedEligibleTenants()).containsExactly("fan-platform");
    }
}
