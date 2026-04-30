package com.example.account.presentation.dto.response;

import com.example.account.application.result.AccountMeResult;

import java.time.Instant;
import java.time.LocalDate;

public record AccountMeResponse(
        String accountId,
        String email,
        String status,
        ProfileResponse profile,
        Instant createdAt
) {
    public record ProfileResponse(
            String displayName,
            String phoneNumber,
            LocalDate birthDate,
            String locale,
            String timezone,
            String preferences
    ) {
    }

    public static AccountMeResponse from(AccountMeResult result) {
        var p = result.profile();
        ProfileResponse profile = new ProfileResponse(
                p.displayName(),
                p.phoneNumber(),
                p.birthDate(),
                p.locale(),
                p.timezone(),
                p.preferences()
        );
        return new AccountMeResponse(
                result.accountId(),
                result.email(),
                result.status(),
                profile,
                result.createdAt()
        );
    }
}
