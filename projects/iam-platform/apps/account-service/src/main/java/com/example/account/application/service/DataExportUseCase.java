package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.DataExportResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * GDPR/PIPA Right to Data Portability use case.
 * Gathers account and profile data for JSON export.
 */
@Service
@RequiredArgsConstructor
public class DataExportUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    /**
     * NET-ZERO overload — header-less callers stay pinned to
     * {@link TenantId#FAN_PLATFORM}, byte-identical to today.
     */
    @Transactional(readOnly = true)
    public DataExportResult execute(String accountId) {
        return execute(accountId, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-467 — tenant-aware data export. Cross-tenant target → 404 via the
     * tenant-scoped {@code findById} (enumeration-safe confinement).
     */
    @Transactional(readOnly = true)
    public DataExportResult execute(String accountId, TenantId tenantId) {
        Account account = accountRepository.findById(tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        DataExportResult.ProfileData profileData = profileRepository.findByAccountId(accountId)
                .map(this::toProfileData)
                .orElse(null);

        return new DataExportResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                account.getCreatedAt(),
                profileData,
                Instant.now()
        );
    }

    private DataExportResult.ProfileData toProfileData(Profile profile) {
        return new DataExportResult.ProfileData(
                profile.getDisplayName(),
                profile.getPhoneNumber(),
                profile.getBirthDate(),
                profile.getLocale(),
                profile.getTimezone()
        );
    }
}
