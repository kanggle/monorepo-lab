package com.example.account.application.result;

import com.example.account.domain.profile.Profile;

import java.time.LocalDate;

public record ProfileUpdateResult(
        String displayName,
        String phoneNumber,
        LocalDate birthDate,
        String locale,
        String timezone,
        String preferences
) {
    public static ProfileUpdateResult from(Profile profile) {
        return new ProfileUpdateResult(
                profile.getDisplayName(),
                maskPhoneNumber(profile.getPhoneNumber()),
                profile.getBirthDate(),
                profile.getLocale(),
                profile.getTimezone(),
                profile.getPreferences()
        );
    }

    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return phoneNumber;
        }
        int len = phoneNumber.length();
        if (len <= 7) {
            return phoneNumber.substring(0, 3) + "-****";
        }
        String last4 = phoneNumber.substring(len - 4);
        String prefix = phoneNumber.substring(0, 3);
        return prefix + "-****-" + last4;
    }
}
