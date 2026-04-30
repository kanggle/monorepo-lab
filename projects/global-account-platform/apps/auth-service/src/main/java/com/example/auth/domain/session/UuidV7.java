package com.example.auth.domain.session;

import java.util.UUID;

/**
 * Forwarding shim. The canonical implementation now lives in
 * {@link com.example.common.id.UuidV7} (promoted in TASK-BE-028c under the
 * shared-library policy as a pure, multi-service utility).
 *
 * <p>Kept here only to avoid churning {@code device-session} callers
 * (notably {@code RegisterOrUpdateDeviceSessionUseCase}) in the same
 * increment that promoted the utility. A follow-up cleanup task should
 * migrate all call sites to {@code com.example.common.id.UuidV7} and
 * delete this shim.
 *
 * <p><b>Migration path:</b> replace {@code com.example.auth.domain.session.UuidV7}
 * imports with {@code com.example.common.id.UuidV7}. The API surface is
 * identical ({@link #randomUuid()}, {@link #randomString()},
 * {@link #timestampMs(UUID)}).
 *
 * <p>New code MUST NOT reference this class — it is slated for removal.
 *
 * @deprecated use {@link com.example.common.id.UuidV7} directly; scheduled
 *     for removal once all {@code auth-service} call sites are migrated.
 */
@Deprecated(forRemoval = true, since = "TASK-BE-028c")
public final class UuidV7 {

    private UuidV7() {}

    /** Forwards to {@link com.example.common.id.UuidV7#randomUuid()}. */
    public static UUID randomUuid() {
        return com.example.common.id.UuidV7.randomUuid();
    }

    /** Forwards to {@link com.example.common.id.UuidV7#randomString()}. */
    public static String randomString() {
        return com.example.common.id.UuidV7.randomString();
    }

    /** Forwards to {@link com.example.common.id.UuidV7#timestampMs(UUID)}. */
    public static long timestampMs(UUID uuid) {
        return com.example.common.id.UuidV7.timestampMs(uuid);
    }
}
