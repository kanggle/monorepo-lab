package com.example.account.application.service;

import com.example.account.application.port.AccountIdentityBindingReader;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.port.AuthServicePort.CredentialIdentityBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialIdentityBackfillUseCase (ADR-036 M4) unit tests")
class CredentialIdentityBackfillUseCaseTest {

    @Mock
    private AccountIdentityBindingReader bindingReader;

    @Mock
    private AuthServicePort authServicePort;

    @InjectMocks
    private CredentialIdentityBackfillUseCase useCase;

    @Test
    @DisplayName("reads linked bindings and pushes them to auth, reporting the propagated counts")
    void propagatesBindings() {
        List<CredentialIdentityBinding> bindings = List.of(
                new CredentialIdentityBinding("acc-1", "idy-1"),
                new CredentialIdentityBinding("acc-2", "idy-2"));
        given(bindingReader.findLinkedBindings()).willReturn(bindings);
        given(authServicePort.backfillCredentialIdentities(bindings)).willReturn(1);

        CredentialIdentityBackfillUseCase.Result result = useCase.execute();

        assertThat(result.accountsScanned()).isEqualTo(2);
        assertThat(result.credentialsUpdated()).isEqualTo(1);
        verify(authServicePort).backfillCredentialIdentities(bindings);
    }

    @Test
    @DisplayName("no linked accounts → does not call auth (net-zero)")
    void noLinkedAccounts() {
        given(bindingReader.findLinkedBindings()).willReturn(List.of());

        CredentialIdentityBackfillUseCase.Result result = useCase.execute();

        assertThat(result.accountsScanned()).isZero();
        assertThat(result.credentialsUpdated()).isZero();
        verify(authServicePort, never()).backfillCredentialIdentities(anyList());
    }
}
