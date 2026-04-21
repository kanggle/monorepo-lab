package com.example.user.infrastructure.metrics;

import com.example.observability.metrics.EventMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMetrics {

    private final MeterRegistry registry;

    public void incrementEventPublishFailure(String eventType) {
        registry.counter(EventMetricNames.EVENT_PUBLISH_FAILURE_TOTAL,
                EventMetricNames.TAG_SERVICE, "user-service",
                EventMetricNames.TAG_EVENT_TYPE, eventType)
                .increment();
    }
}
