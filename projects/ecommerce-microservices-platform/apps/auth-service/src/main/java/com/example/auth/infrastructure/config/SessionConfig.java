package com.example.auth.infrastructure.config;

import com.example.auth.domain.service.SessionProperties;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "auth.session")
public class SessionConfig implements SessionProperties {

    @Positive
    private int maxConcurrent = 5;

    @Positive
    private long inactivityTimeout = 604800;

    @Override
    public int maxConcurrentSessions() {
        return maxConcurrent;
    }

    @Override
    public long inactivityTimeoutSeconds() {
        return inactivityTimeout;
    }
}
