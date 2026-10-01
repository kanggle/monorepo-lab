package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The one Spring test context every consumer-pool integration test (pool flag ON) shares.
 *
 * <p>TASK-BE-615 — {@code @DynamicPropertySource} methods and {@code @MockitoBean} fields are part of
 * the context-cache key. When each pool IT declared its own copies (identical in content), Spring built
 * one context per class, each holding its own connection pool against the single shared Testcontainers
 * MySQL — and the second one tipped the suite into MySQL «Too many connections» (1040), surfacing as an
 * unrelated class failing to load. Declaring them once, here, makes the subclasses share one context.
 * 🔴 Add pool ITs as subclasses; do not re-declare these in a subclass (that forks the context again).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractConsumerPoolIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void consumerPoolOn(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("iam.consumer-pool.enabled", () -> "true");
    }

    @MockitoBean protected AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") protected KafkaTemplate kafkaTemplate;
    @MockitoBean protected AccountOutboxPublisher accountOutboxPublisher;
}
