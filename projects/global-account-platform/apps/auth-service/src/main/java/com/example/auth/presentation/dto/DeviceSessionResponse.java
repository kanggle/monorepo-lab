package com.example.auth.presentation.dto;

import com.example.auth.application.result.DeviceSessionResult;

import java.time.Instant;

public record DeviceSessionResponse(
        String deviceId,
        String userAgentFamily,
        String ipMasked,
        String geoCountry,
        Instant issuedAt,
        Instant lastSeenAt,
        boolean current
) {
    public static DeviceSessionResponse from(DeviceSessionResult r) {
        return new DeviceSessionResponse(
                r.deviceId(), r.userAgentFamily(), r.ipMasked(),
                r.geoCountry(), r.issuedAt(), r.lastSeenAt(), r.current());
    }
}
