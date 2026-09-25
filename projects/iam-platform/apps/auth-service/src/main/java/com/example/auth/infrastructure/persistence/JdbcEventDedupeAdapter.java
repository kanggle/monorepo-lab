package com.example.auth.infrastructure.persistence;

import com.example.messaging.dedupe.EventDedupePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * TASK-BE-601 — {@link EventDedupePort} over auth-service's {@code processed_events} table
 * (V0004: {@code event_id VARCHAR(36)} primary key, {@code event_type}, {@code processed_at}).
 * The table has existed since the v1 inbox pattern and had no reader after TASK-BE-450;
 * the {@code account.locked} consumer is its first user.
 *
 * <p>The dedupe signal is the affected-row count of {@code INSERT IGNORE}: 1 = first
 * sighting (run the work), 0 = the primary key already exists (skip). This is a
 * primary-key decision, not an exists-then-insert check, so two concurrent deliveries of
 * the same event cannot both run the work. {@code INSERT IGNORE} also downgrades other
 * errors (e.g. truncation) to warnings, which would read as a duplicate — the only inputs
 * are a {@link UUID#toString()} (always 36 chars) and a caller-supplied event type, which
 * is why the length of the latter is checked here rather than trusted.
 *
 * <p>{@link Propagation#MANDATORY}: the row must commit or roll back with the work it
 * guards. If the work throws, the caller's transaction rolls back and so does this row —
 * the redelivered event is then processed again, which is the retry the consumer wants.
 */
@Slf4j
@Component
public class JdbcEventDedupeAdapter implements EventDedupePort {

    static final String INSERT_SQL =
            "INSERT IGNORE INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)";

    /** {@code processed_events.event_type} is {@code VARCHAR(100)}. */
    private static final int EVENT_TYPE_MAX = 100;

    private final JdbcOperations jdbcOperations;

    public JdbcEventDedupeAdapter(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (eventType == null || eventType.isBlank() || eventType.length() > EVENT_TYPE_MAX) {
            throw new IllegalArgumentException("eventType must be 1.." + EVENT_TYPE_MAX + " characters");
        }
        int inserted = jdbcOperations.update(
                INSERT_SQL, eventId.toString(), eventType, Timestamp.from(Instant.now()));
        if (inserted == 0) {
            log.debug("event {} ({}) already processed — skipping", eventId, eventType);
            return Outcome.IGNORED_DUPLICATE;
        }
        work.run();
        return Outcome.APPLIED;
    }
}
