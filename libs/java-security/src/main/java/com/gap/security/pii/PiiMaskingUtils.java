package com.gap.security.pii;

/**
 * Unified PII masking utility shared by services that emit operator-facing
 * envelopes, query responses, or audit log entries.
 *
 * <p>Methods preserve the existing per-service contracts so that consumers can
 * migrate without observable changes:
 * <ul>
 *   <li>{@link #maskEmail(String)}, {@link #maskAccountId(String)},
 *       {@link #maskPhone(String)} — originally from admin-service</li>
 *   <li>{@link #maskIp(String)}, {@link #truncateFingerprint(String)} —
 *       originally from security-service</li>
 * </ul>
 *
 * <p>All methods return {@code null} for {@code null} input. The class has no
 * framework dependency so it can be used from any layer.
 */
public final class PiiMaskingUtils {

    private PiiMaskingUtils() {
    }

    /**
     * Mask the local-part of an email: keep first char + domain, replace the
     * rest of the local-part with {@code "***"}. Values without an {@code @}
     * are returned unchanged.
     *
     * <pre>
     *   "jane.doe@example.com" -> "j***@example.com"
     *   "a@example.com"        -> "a***@example.com"
     *   "no-at-sign"           -> "no-at-sign"
     * </pre>
     */
    public static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * Mask an account identifier. The canonical account-id is a UUID and is
     * not itself PII, but when admins pass free-form identifiers that happen
     * to embed an email, mask the embedded email. Otherwise return unchanged.
     */
    public static String maskAccountId(String accountId) {
        if (accountId == null) return null;
        if (accountId.contains("@")) return maskEmail(accountId);
        return accountId;
    }

    /**
     * Mask a phone number to canonical {@code "010-****-1234"} format: strip
     * separators, keep the first 3 digits, replace the middle with
     * {@code "****"}, and keep the last 4 digits. Numbers with 7 or fewer
     * digits are returned unchanged.
     *
     * <pre>
     *   "01012345678"      -> "010-****-5678"
     *   "+82-10-1234-5678" -> "821-****-5678"
     * </pre>
     */
    public static String maskPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 7) return phone;
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-****-" + tail;
    }

    /**
     * Mask an IPv4 address by replacing the last octet with {@code "***"}.
     * Inputs that are already masked or have no dot are returned unchanged.
     * Empty/blank input is returned as-is to preserve caller semantics.
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) {
            return ip;
        }
        return ip.substring(0, lastDot + 1) + "***";
    }

    /**
     * Truncate a device fingerprint to its first 12 characters. Strings of
     * length {@code <= 12} (including {@code null}) are returned unchanged.
     */
    public static String truncateFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() <= 12) {
            return fingerprint;
        }
        return fingerprint.substring(0, 12);
    }
}
