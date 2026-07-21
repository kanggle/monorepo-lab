package com.example.account.application.result;

import com.example.account.application.util.PhoneMasker;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;

import java.time.Instant;
import java.time.LocalDate;

public record AccountMeResult(
        String accountId,
        String email,
        String status,
        ProfileResult profile,
        Instant createdAt
) {
    public record ProfileResult(
            String displayName,
            String phoneNumber,
            LocalDate birthDate,
            String locale,
            String timezone,
            String preferences
    ) {
    }

    public static AccountMeResult from(Account account, Profile profile) {
        ProfileResult profileResult = new ProfileResult(
                profile.getDisplayName(),
                PhoneMasker.mask(profile.getPhoneNumber()),
                profile.getBirthDate(),
                profile.getLocale(),
                profile.getTimezone(),
                profile.getPreferences()
        );
        return new AccountMeResult(
                account.getId(),
                account.getEmail(),
                account.getStatus().name(),
                profileResult,
                account.getCreatedAt()
        );
    }
}
