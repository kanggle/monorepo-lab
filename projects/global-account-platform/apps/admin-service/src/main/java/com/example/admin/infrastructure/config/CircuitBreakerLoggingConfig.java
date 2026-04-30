package com.example.admin.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a DEBUG-level listener on every Resilience4j {@link CircuitBreaker}
 * to log state transitions (CLOSED ↔ OPEN ↔ HALF_OPEN) and slow-call / error
 * events. Installed for every CB currently in the registry plus any added
 * later (e.g. dynamically configured instances), fulfilling TASK-BE-033's
 * "CB state transition DEBUG logging" acceptance item.
 *
 * <p>Kept verbose enough for operator triage but gated to DEBUG so normal
 * production logs stay clean.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerLoggingConfig {

    private final CircuitBreakerRegistry registry;

    @PostConstruct
    void attachListeners() {
        registry.getAllCircuitBreakers().forEach(this::attach);
        registry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));
    }

    private void attach(CircuitBreaker cb) {
        cb.getEventPublisher()
                .onStateTransition(event -> log.debug(
                        "[cb:{}] state transition {} -> {}",
                        cb.getName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> log.debug(
                        "[cb:{}] call not permitted (OPEN)", cb.getName()))
                .onError(event -> log.debug(
                        "[cb:{}] recorded error: {} (duration={}ms)",
                        cb.getName(),
                        event.getThrowable() != null ? event.getThrowable().getClass().getSimpleName() : "n/a",
                        event.getElapsedDuration().toMillis()))
                .onSlowCallRateExceeded(event -> log.debug(
                        "[cb:{}] slow call rate exceeded: {}%",
                        cb.getName(), event.getSlowCallRate()));
    }
}
