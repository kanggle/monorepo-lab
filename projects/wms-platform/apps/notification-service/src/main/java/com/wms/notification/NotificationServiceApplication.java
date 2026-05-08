package com.wms.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * notification-service — alert routing + Slack channel adapter (v1).
 *
 * <p>Service Type: {@code event-consumer} (single — no REST surface in v1).
 * The only inbound trust boundary is Kafka; the only outbound channel in v1
 * is Slack incoming webhooks.
 *
 * <p>Authoritative spec: {@code specs/services/notification-service/architecture.md}.
 */
@SpringBootApplication
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
