package com.example.account.application.service;

import com.example.account.application.command.SocialSignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SocialSignupUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountEventPublisher eventPublisher;

    @Transactional
    public SocialSignupResult execute(SocialSignupCommand command) {
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229
        // introduces dynamic tenant injection from the JWT claim / X-Tenant-Id header.
        TenantId tenantId = TenantId.FAN_PLATFORM;

        String normalizedEmail = command.email().trim().toLowerCase();

        // Check if account with this email already exists within this tenant
        Optional<Account> existing = accountRepository.findByEmail(tenantId, normalizedEmail);
        if (existing.isPresent()) {
            return SocialSignupResult.fromExisting(existing.get());
        }

        try {
            // Create new account (no password for social-only accounts)
            Account account = Account.create(tenantId, command.email());
            account = accountRepository.save(account);

            // Create profile with displayName from provider
            Profile profile = Profile.create(
                    account.getId(),
                    command.displayName(),
                    null,  // locale: use default
                    null   // timezone: use default
            );
            profileRepository.save(profile);

            // Publish account.created outbox event
            eventPublisher.publishAccountCreated(account, profile.getLocale());

            return SocialSignupResult.fromNew(account);
        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent social signup with same email
            // Re-fetch and return existing account
            Account racedAccount = accountRepository.findByEmail(tenantId, normalizedEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "DataIntegrityViolation but account not found for email"));
            return SocialSignupResult.fromExisting(racedAccount);
        }
    }
}
