package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.repository.CredentialRepository;
import org.mockito.Mockito;

/**
 * Shared collaborators for {@link TenantClaimTokenCustomizer} unit tests.
 *
 * <p>TASK-MONO-773 S5 removed the console-eligibility port (and the {@code NOT_ASKED} stub that guarded it): the
 * customizer no longer has an admin-service collaborator at all, so «admin-service is not asked» now holds by
 * construction. The class keeps its name so the git history of the 772 cells stays readable.
 */
final class ConsoleEligibilityStubs {

    /**
     * TASK-MONO-772 S6 — a credential store in which no account has moved into the pool: every
     * {@code findByAccountId} answers empty (Mockito's {@code Optional} default), so the customizer's
     * «console session predates a pool move» check never refuses. For cells that are not about that check.
     */
    static CredentialRepository credentialsNotMoved() {
        return Mockito.mock(CredentialRepository.class);
    }

    private ConsoleEligibilityStubs() {
    }
}
