package com.example.auth.application.result;

import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.IpMasker;
import com.example.auth.domain.session.SessionContext;

import java.time.Instant;

/**
 * Presentation-friendly view of an active {@link DeviceSession}. IP and user-agent are
 * masked / family-only here so the controller can emit the contract format directly.
 */
public record DeviceSessionResult(
        String deviceId,
        String userAgentFamily,
        String ipMasked,
        String geoCountry,
        Instant issuedAt,
        Instant lastSeenAt,
        boolean current
) {

    public static DeviceSessionResult of(DeviceSession s, boolean current) {
        return new DeviceSessionResult(
                s.getDeviceId(),
                userAgentFamilyOf(s.getUserAgent()),
                IpMasker.mask(s.getIpLast()),
                s.getGeoLast() != null ? s.getGeoLast() : "XX",
                s.getIssuedAt(),
                s.getLastSeenAt(),
                current);
    }

    private static String userAgentFamilyOf(String userAgent) {
        // Reuses SessionContext's canonical extraction so HTTP responses and
        // outbox payloads stay consistent.
        return new SessionContext(null, userAgent, null).userAgentFamily();
    }
}
