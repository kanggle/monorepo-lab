package com.example.payment.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for payment-service's own persistence package.
 *
 * <p>Required because {@code libs/java-messaging}'s {@code OutboxJpaConfig}
 * declares its own {@code @EnableJpaRepositories}, which suppresses Spring
 * Boot's default JPA repository auto-scanning from the
 * {@code @SpringBootApplication} base package. Without this, the
 * {@code PaymentJpaRepository} would not be picked up.
 *
 * <p><b>TASK-BE-449 (outbox v2):</b> {@code adapter.out.event} is also scanned
 * so the v2 {@code PaymentOutboxEntity} / {@code PaymentOutboxRepository} (which
 * live with the outbox publisher/writer, an event-output concern) are registered.
 * Mock-repo unit tests do not exercise this wiring — only the full-context
 * {@code @SpringBootTest} integration lane catches a missing scan package.
 *
 * <p><b>Package placement rationale (TASK-BE-137 W4):</b> the lib's
 * {@code OutboxJpaConfig} javadoc recommends an
 * {@code infrastructure.config} package. That recommendation reflects
 * Layered services (e.g. scm-platform/procurement-service), where
 * {@code infrastructure/} is the established cross-cutting bucket.
 * payment-service follows a Hexagonal layout
 * ({@code adapter/} + {@code application/} + {@code domain/}) with no
 * {@code infrastructure/} package; Spring config beans
 * ({@code KafkaConsumerConfig}, {@code StandaloneConfig}, this
 * {@code JpaConfig}) all live in {@code config/} for consistency. The
 * {@code @WebMvcTest} slice isolation concern the lib javadoc warns
 * about does not apply here — payment-service's slice tests
 * ({@code PaymentControllerTest}) use {@code @WebMvcTest(PaymentController.class)}
 * which excludes JPA config beans by default.
 */
@Configuration
@EnableJpaRepositories(basePackages = {
        "com.example.payment.adapter.out.persistence",
        "com.example.payment.adapter.out.event"
})
@EntityScan(basePackages = {
        "com.example.payment.adapter.out.persistence",
        "com.example.payment.adapter.out.event"
})
public class JpaConfig {
}
