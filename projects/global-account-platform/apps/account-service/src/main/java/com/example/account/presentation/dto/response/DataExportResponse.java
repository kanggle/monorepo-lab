package com.example.account.presentation.dto.response;

import com.example.account.application.result.DataExportResult;

import java.time.Instant;
import java.time.LocalDate;

public record DataExportResponse(
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

    public static DataExportResponse from(DataExportResult result) {
        ProfileData profileData = null;
        if (result.profile() != null) {
            profileData = new ProfileData(
                    result.profile().displayName(),
                    result.profile().phoneNumber(),
                    result.profile().birthDate(),
                    result.profile().locale(),
                    result.profile().timezone()
            );
        }
        return new DataExportResponse(
                result.accountId(),
                result.email(),
                result.status(),
                result.createdAt(),
                profileData,
                result.exportedAt()
        );
    }
}
