package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID keypair configuration for Web Push (TASK-BE-464), bound from
 * {@code app.notification.push.vapid.*}. Keys are base64url-encoded; {@code subject} is the
 * VAPID contact (a {@code mailto:} or {@code https:} URL).
 *
 * <p><b>The private key is a secret</b> — inject via env/secret, never commit it. When either
 * key is blank (dev/standalone default), push is treated as not configured: delivery is
 * skipped with a WARN and the context still boots (net-zero, fail-closed prohibited).
 */
@ConfigurationProperties(prefix = "app.notification.push.vapid")
public record WebPushProperties(String publicKey, String privateKey, String subject) {

    public boolean isConfigured() {
        return isPresent(publicKey) && isPresent(privateKey);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
