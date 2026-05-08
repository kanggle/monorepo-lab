package com.wms.notification.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * SHA-256 of {@code eventId + ':' + channelId + ':' + recipient}. Stable
 * across retries and JVM restarts — guarantees we never write a second
 * delivery row for the same (event, channel, recipient) tuple even under
 * concurrent routing.
 */
public final class IdempotencyKeys {

    private IdempotencyKeys() {}

    public static String forDelivery(UUID eventId, String channelId, String recipient) {
        String input = eventId + ":" + channelId + ":" + recipient;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] bytes = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
