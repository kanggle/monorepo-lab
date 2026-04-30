package com.example.account.application.command;

import java.time.LocalDate;

public record UpdateProfileCommand(
        String accountId,
        String displayName,
        String phoneNumber,
        LocalDate birthDate,
        String locale,
        String timezone,
        String preferences
) {
}
