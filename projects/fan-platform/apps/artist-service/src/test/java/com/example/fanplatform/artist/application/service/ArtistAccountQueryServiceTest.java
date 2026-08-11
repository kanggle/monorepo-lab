package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArtistAccountQueryService} — the artist-side half of community-service's
 * follow-target validation (TASK-FAN-BE-045 AC-6, ADR-004 A). Pins the
 * fail-closed contract {@link com.example.fanplatform.artist.application.port.in
 * .CheckArtistAccountUseCase} documents: an infrastructure error must answer
 * {@code false}, never propagate, never {@code true}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ArtistAccountQueryServiceTest {

    @Mock ArtistRepository artistRepository;
    @InjectMocks ArtistAccountQueryService service;

    @Test
    @DisplayName("repository says the account exists in that tenant -> true")
    void isArtistAccount_existsInTenant_returnsTrue() {
        when(artistRepository.existsByTenantIdAndAccountId(eq("fan-platform"), eq("acc-1")))
                .thenReturn(true);

        boolean result = service.isArtistAccount("acc-1", "fan-platform");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("account exists but in a DIFFERENT tenant -> false")
    void isArtistAccount_differentTenant_returnsFalse() {
        // The repository call is scoped to the tenant the caller asked about; a
        // cross-tenant match must never leak as true.
        when(artistRepository.existsByTenantIdAndAccountId(eq("other-tenant"), eq("acc-1")))
                .thenReturn(false);

        boolean result = service.isArtistAccount("acc-1", "other-tenant");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("null accountId -> false, short-circuits before the repository")
    void isArtistAccount_nullAccountId_returnsFalseWithoutRepositoryCall() {
        assertBlankAccountIdShortCircuits(null);
    }

    @Test
    @DisplayName("empty accountId -> false, short-circuits before the repository")
    void isArtistAccount_emptyAccountId_returnsFalseWithoutRepositoryCall() {
        assertBlankAccountIdShortCircuits("");
    }

    @Test
    @DisplayName("whitespace-only accountId -> false, short-circuits before the repository")
    void isArtistAccount_blankAccountId_returnsFalseWithoutRepositoryCall() {
        assertBlankAccountIdShortCircuits("   ");
    }

    private void assertBlankAccountIdShortCircuits(String accountId) {
        boolean result = service.isArtistAccount(accountId, "fan-platform");

        assertThat(result).isFalse();
        verify(artistRepository, never()).existsByTenantIdAndAccountId(anyString(), anyString());
    }

    @Test
    @DisplayName("null tenantId -> false, short-circuits before the repository")
    void isArtistAccount_nullTenantId_returnsFalseWithoutRepositoryCall() {
        assertBlankTenantIdShortCircuits(null);
    }

    @Test
    @DisplayName("empty tenantId -> false, short-circuits before the repository")
    void isArtistAccount_emptyTenantId_returnsFalseWithoutRepositoryCall() {
        assertBlankTenantIdShortCircuits("");
    }

    @Test
    @DisplayName("whitespace-only tenantId -> false, short-circuits before the repository")
    void isArtistAccount_blankTenantId_returnsFalseWithoutRepositoryCall() {
        assertBlankTenantIdShortCircuits("   ");
    }

    private void assertBlankTenantIdShortCircuits(String tenantId) {
        boolean result = service.isArtistAccount("acc-1", tenantId);

        assertThat(result).isFalse();
        verify(artistRepository, never()).existsByTenantIdAndAccountId(anyString(), anyString());
    }

    @Test
    @DisplayName("repository throws (infrastructure failure) -> false, NOT propagated — fail-closed contract")
    void isArtistAccount_repositoryThrows_failsClosedToFalse() {
        when(artistRepository.existsByTenantIdAndAccountId(eq("fan-platform"), eq("acc-1")))
                .thenThrow(new RuntimeException("DB unavailable"));

        boolean result = service.isArtistAccount("acc-1", "fan-platform");

        // The point of the contract: an error must not read as permission. A thrown
        // RuntimeException must be swallowed and answered as false, not rethrown and
        // not converted into true.
        assertThat(result).isFalse();
    }
}
