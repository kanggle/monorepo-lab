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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountEventPublisher eventPublisher;
    private final AccountIdentityProvisioner accountIdentityProvisioner;

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

            // TASK-BE-381 (ADR-036 M1): born-unified — assign the central identity at creation.
            assignBornUnifiedIdentity(tenantId, account);

            // Create profile with displayName from provider
            Profile profile = Profile.create(
                    account.getId(),
                    command.displayName(),
                    null,  // locale: use default
                    null   // timezone: use default
            );
            profileRepository.save(profile);

            // Publish account.created outbox event
            eventPublisher.publishAccountCreated(account, account.getTenantId().value(), profile.getLocale());

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

    /**
     * TASK-BE-381 (ADR-MONO-036 P1/P2, M1): mint the account's central identity at
     * creation (born-unified), fail-soft. The mint runs in a REQUIRES_NEW transaction
     * ({@link AccountIdentityProvisioner}) so a failure cannot poison this registration
     * transaction; on any failure the account is born unlinked (identity_id stays NULL,
     * reconciled later) — registration never blocks on the identity infrastructure
     * (the ADR-034 availability stance). The {@code reuseExisting} mint converges the
     * consumer and operator sides on the SAME identity (ADR-036 P1).
     */
    private void assignBornUnifiedIdentity(TenantId tenantId, Account account) {
        String identityId;
        try {
            identityId = accountIdentityProvisioner.mintIdentity(tenantId.value(), account.getEmail());
        } catch (RuntimeException e) {
            log.warn("born-unified identity mint failed (fail-soft, account {} born unlinked) tenant={}: {}",
                    account.getId(), tenantId.value(), e.toString());
            return;
        }
        if (identityId != null) {
            accountRepository.assignIdentityId(tenantId, account.getId(), identityId);
        }
    }
}
