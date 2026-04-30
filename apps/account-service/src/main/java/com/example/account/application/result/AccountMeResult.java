package com.example.account.application.result;

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
                maskPhoneNumber(profile.getPhoneNumber()),
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

    /**
     * Masks phone number: 010-1234-5678 -> 010-****-5678
     */
    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return phoneNumber;
        }
        // Keep first 3 and last 4, mask the rest
        int len = phoneNumber.length();
        if (len <= 7) {
            return phoneNumber.substring(0, 3) + "-****";
        }
        String last4 = phoneNumber.substring(len - 4);
        String prefix = phoneNumber.substring(0, 3);
        return prefix + "-****-" + last4;
    }
}
