package com.example.account.presentation.dto.response;

import com.example.account.application.result.GdprDeleteResult;

import java.time.Instant;

public record GdprDeleteResponse(
        String accountId,
        String status,
        String emailHash,
        Instant maskedAt
) {
    public static GdprDeleteResponse from(GdprDeleteResult result) {
        return new GdprDeleteResponse(
                result.accountId(),
                result.status(),
                result.emailHash(),
                result.maskedAt()
        );
    }
}
