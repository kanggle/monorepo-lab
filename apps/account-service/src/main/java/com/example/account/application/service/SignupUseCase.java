package com.example.account.application.service;

import com.example.account.application.command.SignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.SignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountEventPublisher eventPublisher;
    private final AuthServicePort authServicePort;

    @Transactional
    public SignupResult execute(SignupCommand command) {
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229
        // introduces dynamic tenant injection from the JWT claim / X-Tenant-Id header.
        TenantId tenantId = TenantId.FAN_PLATFORM;

        // Check email uniqueness within this tenant (primary defense: DB unique constraint)
        if (accountRepository.existsByEmail(tenantId, command.email().trim().toLowerCase())) {
            throw new AccountAlreadyExistsException(command.email());
        }

        try {
            // Create account (Email value object validates and normalizes)
            Account account = Account.create(tenantId, command.email());
            account = accountRepository.save(account);

            // Create profile
            Profile profile = Profile.create(
                    account.getId(),
                    command.displayName(),
                    command.locale(),
                    command.timezone()
            );
            profileRepository.save(profile);

            // TASK-BE-063: persist credential in auth-service via /internal/auth/credentials.
            // Any failure (409 from a racing signup, 5xx, timeout) propagates and rolls back
            // the account + profile rows above — signup is atomic end-to-end.
            try {
                authServicePort.createCredential(account.getId(), account.getEmail(), command.password());
            } catch (AuthServicePort.CredentialAlreadyExistsConflict e) {
                throw new AccountAlreadyExistsException(command.email());
            }

            // Publish outbox event only after credential is persisted (avoids leaking
            // "account created" if the credential write later fails).
            eventPublisher.publishAccountCreated(account, profile.getLocale());

            return SignupResult.from(account);
        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent signup with same email
            throw new AccountAlreadyExistsException(command.email());
        }
    }
}
