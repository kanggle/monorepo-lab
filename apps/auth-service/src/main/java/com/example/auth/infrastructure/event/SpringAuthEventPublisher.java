package com.example.auth.infrastructure.event;

import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.service.AuthMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!standalone")
public class SpringAuthEventPublisher implements AuthEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final AuthMetricsRecorder authMetrics;

    @Override
    public void publish(AuthEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Event publishing failed: eventType={}", event.eventType(), e);
            authMetrics.incrementEventPublishFailure(event.eventType());
        }
    }
}
