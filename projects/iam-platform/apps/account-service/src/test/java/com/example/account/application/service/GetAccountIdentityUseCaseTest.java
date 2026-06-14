package com.example.account.application.service;

import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAccountIdentityUseCase (ADR-MONO-034 step 3b — account_id → identity_id resolve)")
class GetAccountIdentityUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private GetAccountIdentityUseCase useCase;

    @Test
    @DisplayName("identity 가 있는 계정 → identity_id 반환")
    void resolvesIdentity() {
        when(accountRepository.findIdentityId(new TenantId("fan-platform"), "acc-1"))
                .thenReturn(Optional.of("identity-123"));

        Optional<String> result = useCase.execute("fan-platform", "acc-1");

        assertThat(result).contains("identity-123");
    }

    @Test
    @DisplayName("없는/foreign 계정 또는 identity 미배선 → empty (enumeration-safe, fail-soft)")
    void missingOrUnlinked_returnsEmpty() {
        when(accountRepository.findIdentityId(new TenantId("fan-platform"), "ghost"))
                .thenReturn(Optional.empty());

        Optional<String> result = useCase.execute("fan-platform", "ghost");

        assertThat(result).isEmpty();
    }
}
