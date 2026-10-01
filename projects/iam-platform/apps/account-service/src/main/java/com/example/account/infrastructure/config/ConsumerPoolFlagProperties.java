package com.example.account.infrastructure.config;

import com.example.account.application.port.ConsumerPoolFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TASK-BE-614 — reads {@code iam.consumer-pool.enabled} (env {@code IAM_CONSUMER_POOL_ENABLED}).
 * Default {@code false}; see {@link ConsumerPoolFlag} for why.
 */
@Component
public class ConsumerPoolFlagProperties implements ConsumerPoolFlag {

    private final boolean enabled;

    public ConsumerPoolFlagProperties(@Value("${iam.consumer-pool.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
