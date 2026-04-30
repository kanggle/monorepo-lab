package com.example.auth.application.command;

/**
 * Logout command.
 *
 * @param refreshToken the caller's refresh token
 * @param deviceId     caller-provided device id, typically injected by the gateway as
 *                     {@code X-Device-Id}. May be {@code null} for legacy access tokens
 *                     issued before D5 that do not carry a {@code device_id} claim —
 *                     in that case the session revoke is best-effort via the refresh
 *                     token's own {@code device_id} column.
 */
public record LogoutCommand(
        String refreshToken,
        String deviceId
) {
    public LogoutCommand(String refreshToken) {
        this(refreshToken, null);
    }
}
