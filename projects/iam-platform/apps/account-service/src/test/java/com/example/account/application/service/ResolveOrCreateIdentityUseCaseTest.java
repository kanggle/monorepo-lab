package com.example.account.application.service;

import com.example.account.application.service.ResolveOrCreateIdentityUseCase.Outcome;
import com.example.account.application.service.ResolveOrCreateIdentityUseCase.ResolveOrCreateIdentityResult;
import com.example.account.domain.identity.Identity;
import com.example.account.domain.repository.IdentityRepository;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d): Docker-free unit tests for the
 * resolve-or-create identity provisioning primitive. Covers the four FIXED
 * behaviors (CREATED / REUSED / EXISTS_NOT_REUSED) + the concurrent-insert
 * race re-read path. Asserts the no-silent-merge invariant (no mutation of an
 * existing identity; null id when opt-in is absent).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResolveOrCreateIdentityUseCase (ADR-MONO-034 step 3d — resolve-or-create, no silent merge)")
class ResolveOrCreateIdentityUseCaseTest {

    private static final TenantId TENANT = new TenantId("fan-platform");
    private static final String EMAIL = "Person@Example.com";        // mixed-case input
    private static final String NORMALIZED = "person@example.com";   // Email VO normalizes

    @Mock
    private IdentityRepository identityRepository;

    @InjectMocks
    private ResolveOrCreateIdentityUseCase useCase;

    @Test
    @DisplayName("identity 미존재 → fresh 생성 + CREATED")
    void notExisting_createsFresh() {
        when(identityRepository.findByTenantAndEmail(TENANT, NORMALIZED))
                .thenReturn(Optional.empty());
        when(identityRepository.save(any(Identity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResolveOrCreateIdentityResult result = useCase.execute("fan-platform", EMAIL, false);

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(result.identityId()).isNotBlank();

        // The saved identity carries the NORMALIZED email (lowercased by Email VO).
        ArgumentCaptor<Identity> captor = ArgumentCaptor.forClass(Identity.class);
        verify(identityRepository).save(captor.capture());
        assertThat(captor.getValue().getPrimaryEmail()).isEqualTo(NORMALIZED);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("identity 존재 + reuseExisting=true → 기존 id 반환 + REUSED (mutation 없음)")
    void existing_reuseTrue_returnsExisting() {
        Identity existing = Identity.create(TENANT, NORMALIZED);
        when(identityRepository.findByTenantAndEmail(TENANT, NORMALIZED))
                .thenReturn(Optional.of(existing));

        ResolveOrCreateIdentityResult result = useCase.execute("fan-platform", EMAIL, true);

        assertThat(result.outcome()).isEqualTo(Outcome.REUSED);
        assertThat(result.identityId()).isEqualTo(existing.getIdentityId());
        verify(identityRepository, never()).save(any());
    }

    @Test
    @DisplayName("identity 존재 + reuseExisting=false → identityId=null + EXISTS_NOT_REUSED (no merge)")
    void existing_reuseFalse_returnsNull() {
        Identity existing = Identity.create(TENANT, NORMALIZED);
        when(identityRepository.findByTenantAndEmail(TENANT, NORMALIZED))
                .thenReturn(Optional.of(existing));

        ResolveOrCreateIdentityResult result = useCase.execute("fan-platform", EMAIL, false);

        assertThat(result.outcome()).isEqualTo(Outcome.EXISTS_NOT_REUSED);
        assertThat(result.identityId()).isNull();
        verify(identityRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 insert 경합 → DataIntegrityViolation 후 re-read → reuse=true 면 REUSED")
    void raceOnInsert_reReads_andReusesWhenOptIn() {
        Identity raced = Identity.create(TENANT, NORMALIZED);
        // First lookup: empty (decide to create). Re-read after the race: present.
        when(identityRepository.findByTenantAndEmail(TENANT, NORMALIZED))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(identityRepository.save(any(Identity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_identities_tenant_email"));

        ResolveOrCreateIdentityResult result = useCase.execute("fan-platform", EMAIL, true);

        assertThat(result.outcome()).isEqualTo(Outcome.REUSED);
        assertThat(result.identityId()).isEqualTo(raced.getIdentityId());
        verify(identityRepository, times(2)).findByTenantAndEmail(TENANT, NORMALIZED);
    }

    @Test
    @DisplayName("동시 insert 경합 + reuse=false → re-read 후 EXISTS_NOT_REUSED (no merge, no error escape)")
    void raceOnInsert_reReads_butNoMergeWhenNoOptIn() {
        Identity raced = Identity.create(TENANT, NORMALIZED);
        when(identityRepository.findByTenantAndEmail(TENANT, NORMALIZED))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(identityRepository.save(any(Identity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_identities_tenant_email"));

        ResolveOrCreateIdentityResult result = useCase.execute("fan-platform", EMAIL, false);

        assertThat(result.outcome()).isEqualTo(Outcome.EXISTS_NOT_REUSED);
        assertThat(result.identityId()).isNull();
    }
}
