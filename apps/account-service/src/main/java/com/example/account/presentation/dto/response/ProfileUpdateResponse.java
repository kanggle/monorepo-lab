package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProfileUpdateResult;

import java.time.LocalDate;

public record ProfileUpdateResponse(
        String displayName,
        String phoneNumber,
        LocalDate birthDate,
        String locale,
        String timezone,
        String preferences
) {
    public static ProfileUpdateResponse from(ProfileUpdateResult result) {
        return new ProfileUpdateResponse(
                result.displayName(),
                result.phoneNumber(),
                result.birthDate(),
                result.locale(),
                result.timezone(),
                result.preferences()
        );
    }
}
