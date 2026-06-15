package com.example.account.infrastructure.persistence;

import com.example.account.application.port.AccountIdentityBindingReader;
import com.example.account.application.port.AuthServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): infrastructure adapter for
 * {@link AccountIdentityBindingReader}. Reads the cross-tenant
 * {@code account_id → identity_id} bindings via the native projection on
 * {@link AccountJpaRepository} (the same direct-repository pattern cross-tenant batch
 * jobs use), keeping {@code identity_id} unmapped on the entity.
 */
@Component
@RequiredArgsConstructor
class AccountIdentityBindingReaderAdapter implements AccountIdentityBindingReader {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public List<AuthServicePort.CredentialIdentityBinding> findLinkedBindings() {
        return accountJpaRepository.findAllIdentityBindings().stream()
                .map(v -> new AuthServicePort.CredentialIdentityBinding(
                        v.getAccountId(), v.getIdentityId()))
                .toList();
    }
}
