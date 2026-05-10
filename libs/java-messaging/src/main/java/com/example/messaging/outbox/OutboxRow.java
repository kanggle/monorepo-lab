package com.example.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract for a single row in a service's outbox table.
 *
 * <p>Each service owns its own outbox table because the columns required differ
 * by domain (some need {@code event_version}, some need {@code retry_count} / {@code last_error},
 * some store binary payloads). The shared {@link AbstractOutboxPublisher} consumes
 * rows through this interface so it can drive every service's table without taking
 * a hard dependency on a single JPA entity class.
 *
 * <p>Implementations are typically JPA entities. The reference implementation is
 * {@link OutboxRowEntity}; services that already have a richer schema implement
 * this interface on their own entity instead.
 *
 * <p>Contract semantics:
 * <ul>
 *   <li>{@link #getEventId()} — globally unique identifier; used as the Kafka
 *       record header {@code eventId} and downstream as the dedupe key. Typically
 *       UUIDv7 so the column also doubles as the natural sort order.</li>
 *   <li>{@link #getEventType()} — dotted name (e.g. {@code <bounded-context>.<verb>}).
 *       The publisher's {@code TopicResolver} consumes this to map to the Kafka
 *       topic.</li>
 *   <li>{@link #getAggregateId()} — the business aggregate identifier; serialized
 *       to the Kafka record key for partition affinity unless a {@link #getPartitionKey()}
 *       override is provided.</li>
 *   <li>{@link #getPartitionKey()} — explicit partition key when ordering must
 *       follow a different aggregate (e.g. saga events keyed by {@code sagaId}).
 *       Returns {@code null} to fall back to {@link #getAggregateId()}.</li>
 *   <li>{@link #getPayload()} — opaque envelope JSON; the publisher does not
 *       parse it.</li>
 *   <li>{@link #getOccurredAt()} — domain timestamp (when the business event happened).</li>
 *   <li>{@link #getPublishedAt()} — set by {@link #markPublished(Instant)} after
 *       Kafka ACK; pending rows have {@code null}.</li>
 *   <li>{@link #getRetries()} — count of failed publish attempts. Optional —
 *       services without retry tracking may return {@code 0}.</li>
 *   <li>{@link #getLastError()} — last failure message for ops triage. Optional.</li>
 * </ul>
 */
public interface OutboxRow {

    /**
     * Globally unique event identifier (typically UUIDv7).
     */
    UUID getEventId();

    /**
     * Dotted event type (e.g. {@code <bounded-context>.<verb>}).
     */
    String getEventType();

    /**
     * Aggregate type (e.g. {@code Order}).
     */
    String getAggregateType();

    /**
     * Aggregate identifier (the business key associated with the event).
     */
    String getAggregateId();

    /**
     * Optional explicit partition key. When {@code null} the publisher falls back
     * to {@link #getAggregateId()}.
     */
    default String getPartitionKey() {
        return null;
    }

    /**
     * The envelope payload as JSON text (the publisher does not parse).
     */
    String getPayload();

    /**
     * Domain timestamp — when the business event happened.
     */
    Instant getOccurredAt();

    /**
     * Publish timestamp — {@code null} while the row is still pending.
     */
    Instant getPublishedAt();

    /**
     * Failed-publish-attempt counter. Defaults to {@code 0} for services that
     * do not track retries.
     */
    default int getRetries() {
        return 0;
    }

    /**
     * Last error message for ops triage. Defaults to {@code null} for services
     * that do not track errors.
     */
    default String getLastError() {
        return null;
    }

    /**
     * Mark the row as published after a Kafka ACK. The implementation persists
     * the timestamp and any related state (e.g. status transition).
     */
    void markPublished(Instant at);

    /**
     * Increment the retry counter and (optionally) record the latest error.
     * Default no-op so simple implementations need not store retry state.
     */
    default void recordFailure(String error) {
        // no-op by default
    }
}
