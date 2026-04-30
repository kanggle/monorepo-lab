package com.example.admin.application;

import java.time.Instant;

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
            String birthDate,
            String locale,
            String timezone
    ) {}
}
