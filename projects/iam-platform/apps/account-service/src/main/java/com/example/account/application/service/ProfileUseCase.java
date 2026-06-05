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

    @Transactional(readOnly = true)
    public AccountMeResult getMe(String accountId) {
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229
        Account account = accountRepository.findById(TenantId.FAN_PLATFORM, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Profile profile = profileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return AccountMeResult.from(account, profile);
    }

    @Transactional
    public ProfileUpdateResult updateProfile(UpdateProfileCommand command) {
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229
        accountRepository.findById(TenantId.FAN_PLATFORM, command.accountId())
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
