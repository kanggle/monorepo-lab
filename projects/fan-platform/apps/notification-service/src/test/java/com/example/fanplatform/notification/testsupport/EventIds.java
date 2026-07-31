package com.example.fanplatform.notification.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deterministic test-only {@code eventId} generator (TASK-FAN-BE-042).
 *
 * <p>Production {@code eventId}s are always a UUID (both fan-platform producers
 * mint one via {@code UuidV7.randomUuid()}); {@code EventDedupePort} itself is
 * typed {@code UUID}. Test fixtures historically used short human-readable
 * labels (e.g. {@code "evt-c1"}) which are not valid UUID strings — {@link #uuid}
 * maps a label to a stable, valid UUID (name-based UUIDv3 via
 * {@link UUID#nameUUIDFromBytes}) so fixtures stay readable in test source while
 * satisfying the port's contract on the wire / at the consumer boundary. Calling
 * {@code uuid(label)} twice with the same label always returns the same UUID
 * string, so a producer call and its corresponding assertion stay in sync.
 */
public final class EventIds {

    private EventIds() {
    }

    public static String uuid(String label) {
        return UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
