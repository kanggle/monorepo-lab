package com.example.auth.domain.session;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Single source for the IP-masking format used by HTTP responses and outbox events.
 *
 * <p>Spec: specs/services/auth-service/device-session.md "IP Masking Format".
 *
 * <ul>
 *   <li>IPv4 — last two octets replaced with {@code *}: {@code 192.168.1.42 -> 192.168.*.*}.
 *   <li>IPv6 — top 48 bits kept (first three groups), the rest collapsed to {@code ::*}:
 *       {@code 2001:db8:85a3:1:2:3:4:5 -> 2001:db8:85a3::*}.
 *   <li>Null / blank / unparseable input — {@code "unknown"} (no IP exposure at all).
 * </ul>
 */
public final class IpMasker {

    public static final String UNKNOWN = "unknown";

    private IpMasker() {}

    public static String mask(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = ipAddress.trim();
        // Quick fingerprint: presence of ':' implies IPv6; '.' implies IPv4.
        // For ambiguous strings we fall back to InetAddress.
        try {
            InetAddress addr = InetAddress.getByName(trimmed);
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) {
                return maskIpv4(bytes);
            }
            if (bytes.length == 16) {
                return maskIpv6(bytes);
            }
            return UNKNOWN;
        } catch (UnknownHostException | SecurityException e) {
            return UNKNOWN;
        }
    }

    private static String maskIpv4(byte[] bytes) {
        int o1 = bytes[0] & 0xFF;
        int o2 = bytes[1] & 0xFF;
        return o1 + "." + o2 + ".*.*";
    }

    private static String maskIpv6(byte[] bytes) {
        // Take first 48 bits (= first 3 16-bit groups) and append "::*".
        int g1 = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        int g2 = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        int g3 = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
        return String.format(Locale.ROOT, "%x:%x:%x::*", g1, g2, g3);
    }
}
