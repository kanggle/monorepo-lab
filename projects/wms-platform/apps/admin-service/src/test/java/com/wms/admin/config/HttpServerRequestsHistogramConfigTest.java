package com.wms.admin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-836 — the shipped {@code application.yml} turns on histogram buckets for
 * {@code http.server.requests}.
 *
 * <p>wms {@code HighLatencyP99} queries {@code http_server_requests_seconds_bucket}, which
 * Spring Boot exports only with buckets on. {@code libs/java-observability} turns them on for
 * its consumers; this service does not use that library, so the setting lives in its own
 * {@code application.yml}. Reads the real file — the point is that the shipped configuration
 * is right.
 */
class HttpServerRequestsHistogramConfigTest {

    @Test
    @DisplayName("management.metrics.distribution.percentiles-histogram.http.server.requests=true")
    void histogramBucketsEnabled() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        yaml.afterPropertiesSet();
        Properties props = yaml.getObject();
        assertThat(props).as("application.yml must be readable from the test classpath").isNotNull();

        assertThat(props.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests"))
                .isEqualTo("true");
    }
}
