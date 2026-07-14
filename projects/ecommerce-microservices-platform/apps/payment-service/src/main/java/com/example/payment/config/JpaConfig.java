package com.example.payment.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for payment-service's own persistence package.
 *
 * <p>Scopes repository/entity scanning to payment-service's own packages. It used to be
 * mandatory: {@code libs/java-messaging}'s {@code OutboxJpaConfig} declared its own
 * app-wide {@code @EnableJpaRepositories}, which suppressed Spring Boot's default JPA
 * repository auto-scanning from the {@code @SpringBootApplication} base package — without
 * this class the {@code PaymentJpaRepository} would not have been picked up. TASK-MONO-406
 * deleted that lib config, so this declaration is now payment-service's own (deliberately
 * narrower) choice rather than a workaround.
 *
 * <p><b>TASK-BE-449 (outbox v2):</b> {@code adapter.out.event} is also scanned
 * so the v2 {@code PaymentOutboxEntity} / {@code PaymentOutboxRepository} (which
 * live with the outbox publisher/writer, an event-output concern) are registered.
 * Mock-repo unit tests do not exercise this wiring — only the full-context
 * {@code @SpringBootTest} integration lane catches a missing scan package.
 *
 * <p><b>Package placement rationale (TASK-BE-137 W4):</b> the lib's
 * {@code OutboxJpaConfig} javadoc recommended an
 * {@code infrastructure.config} package (that class was deleted by TASK-MONO-406;
 * the reasoning is recorded here because the placement it produced still stands).
 * That recommendation reflected
 * Layered services (e.g. scm-platform/procurement-service), where
 * {@code infrastructure/} is the established cross-cutting bucket.
 * payment-service follows a Hexagonal layout
 * ({@code adapter/} + {@code application/} + {@code domain/}) with no
 * {@code infrastructure/} package; Spring config beans
 * ({@code KafkaConsumerConfig}, {@code StandaloneConfig}, this
 * {@code JpaConfig}) all live in {@code config/} for consistency. The
 * {@code @WebMvcTest} slice isolation concern that lib javadoc warned
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
