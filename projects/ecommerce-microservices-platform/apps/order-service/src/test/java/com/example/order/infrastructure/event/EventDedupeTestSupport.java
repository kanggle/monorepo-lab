package com.example.order.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import org.mockito.invocation.InvocationOnMock;

/**
 * Shared {@link EventDedupePort} mock-answer helper for order-service consumer unit tests
 * (ADR-MONO-058 D7, TASK-BE-569). {@code EventDedupePort#process(eventId, eventType, work)}
 * is a Mockito-mocked interface method — unlike the old boolean {@code isDuplicate(...)},
 * an unstubbed call returns {@code null} and never invokes {@code work}, so every consumer
 * test that expects the happy-path business call to run must stub {@code process(...)} to
 * actually run the supplied {@link Runnable}. This helper centralizes that answer.
 */
final class EventDedupeTestSupport {

    private EventDedupeTestSupport() {
    }

    /** Runs the {@code work} argument (index 2) and returns {@code APPLIED}. */
    static EventDedupePort.Outcome runWork(InvocationOnMock invocation) {
        ((Runnable) invocation.getArgument(2)).run();
        return EventDedupePort.Outcome.APPLIED;
    }
}
