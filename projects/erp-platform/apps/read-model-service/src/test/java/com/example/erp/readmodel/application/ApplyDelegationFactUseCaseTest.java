package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApplyDelegationFactUseCase}: the 2-event latest-state
 * upsert, sticky-terminal REVOKED, out-of-order (revoke-before-grant), and dedupe
 * (T8). STRICT_STUBS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyDelegationFactUseCaseTest {

    @Mock DelegationFactProjectionRepository delegationRepository;
    @Mock EventDedupeService dedupeService;

    @InjectMocks ApplyDelegationFactUseCase useCase;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");
    private static final Instant T_GRANT = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant T_REVOKE = Instant.parse("2026-06-10T00:00:00Z");

    private DelegationFactCommand granted(String eventId) {
        return new DelegationFactCommand(eventId, "erp.approval.delegated.v1", "dgr-1",
                DelegationFactStatus.ACTIVE, "emp-a", "emp-d", FROM, TO, "vacation",
                T_GRANT, null, "REQUEST", "appr-1");
    }

    private DelegationFactCommand revoked(String eventId) {
        return new DelegationFactCommand(eventId, "erp.approval.delegation.revoked.v1", "dgr-1",
                DelegationFactStatus.REVOKED, "emp-a", "emp-d", null, null, "back",
                T_REVOKE, T_REVOKE, null, null);
    }

    @Test
    void grantedInsertsNewActiveFactAndMarksProcessed() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(false);
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.empty());

        useCase.apply(granted("evt-1"));

        ArgumentCaptor<DelegationFactProjection> captor =
                ArgumentCaptor.forClass(DelegationFactProjection.class);
        verify(delegationRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(DelegationFactStatus.ACTIVE);
        assertThat(captor.getValue().validFrom()).isEqualTo(FROM);
        assertThat(captor.getValue().validTo()).isEqualTo(TO);
        assertThat(captor.getValue().revokedAt()).isNull();
        // AC-1: the REQUEST scope + scopeRequestId are projected.
        assertThat(captor.getValue().scope()).isEqualTo("REQUEST");
        assertThat(captor.getValue().scopeRequestId()).isEqualTo("appr-1");
        verify(dedupeService).markProcessed("evt-1", "erp.approval.delegated.v1", "dgr-1");
    }

    @Test
    void revokeOnExistingActiveTransitionsToRevoked() {
        DelegationFactProjection existing = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-1",
                "GLOBAL", null);
        when(dedupeService.isDuplicate("evt-2")).thenReturn(false);
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.of(existing));

        useCase.apply(revoked("evt-2"));

        assertThat(existing.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(existing.revokedAt()).isEqualTo(T_REVOKE);
        // The validity window is preserved across the transition.
        assertThat(existing.validFrom()).isEqualTo(FROM);
        verify(delegationRepository).save(existing);
        verify(dedupeService).markProcessed("evt-2", "erp.approval.delegation.revoked.v1", "dgr-1");
    }

    @Test
    void outOfOrder_revokeBeforeGrantInsertsRevokedRowWithAbsentWindow() {
        when(dedupeService.isDuplicate("evt-2")).thenReturn(false);
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.empty());

        useCase.apply(revoked("evt-2"));

        ArgumentCaptor<DelegationFactProjection> captor =
                ArgumentCaptor.forClass(DelegationFactProjection.class);
        verify(delegationRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(captor.getValue().validFrom()).isNull();
        assertThat(captor.getValue().validTo()).isNull();
        assertThat(captor.getValue().revokedAt()).isEqualTo(T_REVOKE);
    }

    @Test
    void stickyTerminal_grantAfterRevokeDoesNotRevert() {
        DelegationFactProjection revokedRow = DelegationFactProjection.ofRevoked(
                "dgr-1", "emp-a", "emp-d", "back", T_REVOKE, T_REVOKE, "evt-2");
        when(dedupeService.isDuplicate("evt-late")).thenReturn(false);
        when(delegationRepository.findById("dgr-1")).thenReturn(Optional.of(revokedRow));

        useCase.apply(granted("evt-late"));

        assertThat(revokedRow.status()).isEqualTo(DelegationFactStatus.REVOKED);
        // AC-3: the late grant fills the previously-NULL scope without reverting status.
        assertThat(revokedRow.scope()).isEqualTo("REQUEST");
        assertThat(revokedRow.scopeRequestId()).isEqualTo("appr-1");
        verify(delegationRepository).save(revokedRow);
    }

    @Test
    void duplicateEventIsSkippedWithoutMutation() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(true);

        useCase.apply(granted("evt-1"));

        verify(delegationRepository, never()).save(any());
        verify(dedupeService, never()).markProcessed(anyString(), anyString(), anyString());
    }
}
