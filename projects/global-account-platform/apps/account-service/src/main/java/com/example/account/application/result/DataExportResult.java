package com.example.account.application.result;

import java.time.Instant;
import java.time.LocalDate;

public record DataExportResult(
        String accountId,
        String email,
        String status,
        Instant createdAt,
        ProfileData profile,
        Instant exportedAt
) {

    public record ProfileData(
            String displayName,
            String phoneNumber,
            LocalDate birthDate,
            String locale,
            String timezone
    ) {
    }
}
