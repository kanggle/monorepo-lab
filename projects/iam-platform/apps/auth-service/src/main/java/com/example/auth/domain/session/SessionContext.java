package com.example.auth.domain.session;

/**
 * Value object representing session context information (IP, user agent, device, geo).
 */
public record SessionContext(
        String ipAddress,
        String userAgent,
        String deviceFingerprint,
        String geoCountry
) {
    /**
     * Backward-compatible constructor that defaults geoCountry to "XX".
     */
    public SessionContext(String ipAddress, String userAgent, String deviceFingerprint) {
        this(ipAddress, userAgent, deviceFingerprint, "XX");
    }

    public String ipMasked() {
        // Delegates to the canonical IpMasker (two-octet IPv4 / 48-bit IPv6 rule).
        // See specs/services/auth-service/device-session.md "IP Masking Format".
        return IpMasker.mask(ipAddress);
    }

    public String userAgentFamily() {
        if (userAgent == null || userAgent.isEmpty()) {
            return "unknown";
        }
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari")) return "Safari";
        return "Other";
    }

    public String resolvedGeoCountry() {
        return geoCountry != null ? geoCountry : "XX";
    }
}
