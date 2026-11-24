package com.example.account.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-664 — the shipped {@code application.yml} exposes {@code /actuator/prometheus}.
 *
 * <p>Without {@code management.endpoints.web.exposure.include} Spring Boot exposes {@code health}
 * only, so the Prometheus {@code account-service} job got a 404 and not one account-service metric
 * was ever scraped (including {@code account_outbox_lag_seconds}, fixed in TASK-BE-662). Reads the
 * real file rather than a test fixture — the point is that the shipped configuration is right. No
 * profile file ({@code application-e2e.yml}, the test profile) overrides the key.
 */
class ActuatorExposureConfigTest {

    @Test
    @DisplayName("management.endpoints.web.exposure.include carries prometheus (same value as the gateway/security siblings)")
    void prometheusEndpointIsExposed() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        yaml.afterPropertiesSet();
        Properties props = yaml.getObject();
        assertThat(props).as("application.yml must be readable from the test classpath").isNotNull();

        String include = props.getProperty("management.endpoints.web.exposure.include");
        assertThat(include).as("management.endpoints.web.exposure.include").isNotNull();
        assertThat(Arrays.stream(include.split(",")).map(String::trim))
                .containsExactly("health", "info", "prometheus", "metrics");
        // TASK-MONO-770 stays: a mail outage must not mark the service DOWN.
        assertThat(props.getProperty("management.health.mail.enabled")).isEqualTo("false");
    }
}
