package com.example.messaging.outbox;

/**
 * Strategy for mapping an outbox row's {@code eventType} to a Kafka topic.
 *
 * <p>Provided per service so the topic naming convention stays inside the
 * service's bounded context (e.g. {@code <bounded-context>.<verb> → <prefix>.<bounded-context>.<verb>.v1}).
 *
 * <p>Throws {@link IllegalArgumentException} for unknown event types — callers
 * treat this as a non-retryable poison-pill condition and may route the row
 * to a dead-letter table.
 */
@FunctionalInterface
public interface TopicResolver {

    /**
     * Resolve the Kafka topic for the given event type.
     *
     * @param eventType dotted event type from the outbox row
     * @return target Kafka topic
     * @throws IllegalArgumentException if no topic mapping exists
     */
    String resolveTopic(String eventType);
}
