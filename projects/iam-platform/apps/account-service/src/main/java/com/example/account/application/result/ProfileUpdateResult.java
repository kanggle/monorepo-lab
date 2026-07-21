package com.example.account.application.result;

import com.example.account.application.util.PhoneMasker;
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
                PhoneMasker.mask(profile.getPhoneNumber()),
                profile.getBirthDate(),
                profile.getLocale(),
                profile.getTimezone(),
                profile.getPreferences()
        );
    }
}
