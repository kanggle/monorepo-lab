package com.example.auth.application;

import com.example.auth.domain.repository.CredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackfillCredentialIdentityUseCase (ADR-036 M4) unit tests")
class BackfillCredentialIdentityUseCaseTest {

    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private BackfillCredentialIdentityUseCase useCase;

    @Test
    @DisplayName("writes each binding via the M2 writer and sums the rows actually updated")
    void appliesBindingsAndCountsUpdated() {
        given(credentialRepository.assignIdentityId("acc-1", "idy-1")).willReturn(1);
        given(credentialRepository.assignIdentityId("acc-2", "idy-2")).willReturn(0); // already linked → net-zero

        BackfillCredentialIdentityUseCase.Result result = useCase.execute(List.of(
                new BackfillCredentialIdentityUseCase.Binding("acc-1", "idy-1"),
                new BackfillCredentialIdentityUseCase.Binding("acc-2", "idy-2")));

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        verify(credentialRepository).assignIdentityId("acc-1", "idy-1");
        verify(credentialRepository).assignIdentityId("acc-2", "idy-2");
    }

    @Test
    @DisplayName("skips blank/null bindings (never calls the writer with empty keys) — net-zero")
    void skipsBlankBindings() {
        BackfillCredentialIdentityUseCase.Result result = useCase.execute(List.of(
                new BackfillCredentialIdentityUseCase.Binding("acc-x", "  "),
                new BackfillCredentialIdentityUseCase.Binding("", "idy-x"),
                new BackfillCredentialIdentityUseCase.Binding("acc-n", null)));

        assertThat(result.requested()).isEqualTo(3);
        assertThat(result.updated()).isZero();
        verify(credentialRepository, never()).assignIdentityId(anyString(), anyString());
    }

    @Test
    @DisplayName("empty batch → net-zero")
    void emptyBatch() {
        BackfillCredentialIdentityUseCase.Result result = useCase.execute(List.of());
        assertThat(result.requested()).isZero();
        assertThat(result.updated()).isZero();
        verify(credentialRepository, never()).assignIdentityId(anyString(), anyString());
    }
}
