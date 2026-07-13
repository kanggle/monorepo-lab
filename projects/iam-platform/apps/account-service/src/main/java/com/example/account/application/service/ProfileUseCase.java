package com.example.account.application.service;

import com.example.account.application.command.UpdateProfileCommand;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.AccountMeResult;
import com.example.account.application.result.ProfileUpdateResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    /**
     * NET-ZERO overload — a header-less caller stays pinned to {@link TenantId#FAN_PLATFORM},
     * byte-identical to the pre-BE-507 behaviour.
     */
    @Transactional(readOnly = true)
    public AccountMeResult getMe(String accountId) {
        return getMe(accountId, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-507 — tenant-aware profile read. The tenant comes from the gateway-propagated
     * {@code X-Tenant-Id} (the caller's token claim), so an ecommerce consumer's profile is
     * found in their own tenant instead of only in fan-platform.
     */
    @Transactional(readOnly = true)
    public AccountMeResult getMe(String accountId, TenantId tenantId) {
        Account account = accountRepository.findById(tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Profile profile = profileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return AccountMeResult.from(account, profile);
    }

    /**
     * NET-ZERO overload — see {@link #getMe(String)}.
     */
    @Transactional
    public ProfileUpdateResult updateProfile(UpdateProfileCommand command) {
        return updateProfile(command, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-507 — tenant-aware profile update. A cross-tenant target resolves through the
     * tenant-scoped {@code findById} to a 404 (enumeration-safe confinement, as BE-467).
     */
    @Transactional
    public ProfileUpdateResult updateProfile(UpdateProfileCommand command, TenantId tenantId) {
        accountRepository.findById(tenantId, command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        Profile profile = profileRepository.findByAccountId(command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        profile.update(
                command.displayName(),
                command.phoneNumber(),
                command.birthDate(),
                command.locale(),
                command.timezone(),
                command.preferences()
        );

        profileRepository.save(profile);

        return ProfileUpdateResult.from(profile);
    }
}
