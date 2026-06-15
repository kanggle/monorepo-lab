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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountEventPublisher eventPublisher;
    private final AuthServicePort authServicePort;
    private final AccountIdentityProvisioner accountIdentityProvisioner;

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

            // TASK-BE-381/382 (ADR-036 M1/M2): born-unified — mint+assign the central identity at
            // creation; the resolved identityId is propagated to the credential row (M2).
            String identityId = mintAndAssignIdentity(tenantId, account);

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
            //   TASK-BE-313: pass the account's tenantId (signup public-flow may have
            //   the default tenant; pass-through preserves the existing fallback semantics
            //   in auth-service's CreateCredentialUseCase when tenantId is null).
            //   TASK-MONO-263 (ADR-032 D5 step 4): accountType is no longer carried —
            //   the account_type claim/column is gone.
            try {
                authServicePort.createCredential(
                        account.getId(), account.getEmail(), command.password(),
                        account.getTenantId().value(), identityId);
            } catch (AuthServicePort.CredentialAlreadyExistsConflict e) {
                throw new AccountAlreadyExistsException(command.email());
            }

            // Publish outbox event only after credential is persisted (avoids leaking
            // "account created" if the credential write later fails).
            eventPublisher.publishAccountCreated(account, account.getTenantId().value(), profile.getLocale());

            return SignupResult.from(account);
        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent signup with same email
            throw new AccountAlreadyExistsException(command.email());
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
     *
     * @return the resolved central {@code identity_id}, or {@code null} when the mint
     *         failed (fail-soft) — the caller propagates it to the credential row (M2).
     */
    private String mintAndAssignIdentity(TenantId tenantId, Account account) {
        String identityId;
        try {
            identityId = accountIdentityProvisioner.mintIdentity(tenantId.value(), account.getEmail());
        } catch (RuntimeException e) {
            log.warn("born-unified identity mint failed (fail-soft, account {} born unlinked) tenant={}: {}",
                    account.getId(), tenantId.value(), e.toString());
            return null;
        }
        if (identityId != null) {
            accountRepository.assignIdentityId(tenantId, account.getId(), identityId);
        }
        return identityId;
    }
}
