package com.example.auth.application.result;

import java.util.List;

/**
 * Outcome of {@link com.example.auth.application.RegisterOrUpdateDeviceSessionUseCase}.
 *
 * @param deviceId         the device_id of the (new or existing) active session for this
 *                         (account, fingerprint). Caller stamps it into the JWT and the
 *                         refresh_token row.
 * @param newSession       true if a brand-new device_session row was inserted, false if
 *                         the existing active row was just touched (last_seen_at update).
 * @param evictedDeviceIds device_ids evicted in the same transaction by the
 *                         concurrent-session limit. Empty when no eviction happened.
 */
public record RegisterDeviceSessionResult(
        String deviceId,
        boolean newSession,
        List<String> evictedDeviceIds
) {
}
