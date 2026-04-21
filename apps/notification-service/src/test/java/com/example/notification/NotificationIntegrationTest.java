package com.example.notification;

import com.example.notification.adapter.in.event.OrderPlacedEventConsumer;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("알림 서비스 통합 테스트")
class NotificationIntegrationTest {

    @TestConfiguration
    static class MockMailConfig {
        @Bean
        @Primary
        public NotificationSender mockEmailSender() {
            return new NotificationSender() {
                @Override
                public void send(String recipient, String subject, String body) {
                    // no-op mock for integration test
                }

                @Override
                public NotificationChannel supportedChannel() {
                    return NotificationChannel.EMAIL;
                }
            };
        }
    }

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_db")
            .withUsername("notification_user")
            .withPassword("notification_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1025");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderPlacedEventConsumer orderPlacedEventConsumer;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Test
    @DisplayName("템플릿 생성 후 이벤트 소비를 통해 알림이 발송되고 이력을 조회할 수 있다")
    void fullFlow_createTemplate_consumeEvent_queryNotification() throws Exception {
        // 1. 템플릿 준비 (다른 테스트에서 이미 생성되었을 수 있으므로 확인 후 생성)
        if (!templateRepository.existsByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL)) {
            mockMvc.perform(post("/api/notifications/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"ORDER_PLACED\",\"channel\":\"EMAIL\"," +
                                    "\"subject\":\"Order {{orderId}} placed\"," +
                                    "\"body\":\"Your order {{orderId}} ({{totalPrice}} won) has been placed.\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.templateId").isNotEmpty());
        }

        // 2. OrderPlaced 이벤트 소비
        String userId = "user-" + UUID.randomUUID();
        String orderId = "order-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "OrderPlaced",
                "occurred_at", "2026-03-28T00:00:00Z",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", 50000
                )
        ));

        orderPlacedEventConsumer.onMessage(eventJson);

        // 3. 알림 이력 조회
        PageResult<Notification> notifications = notificationRepository.findByUserId(userId, PageQuery.of(0, 20));
        assertThat(notifications.content()).hasSize(1);
        assertThat(notifications.content().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notifications.content().get(0).getSubject()).contains(orderId);

        // 4. HTTP API로 알림 이력 조회
        mockMvc.perform(get("/api/notifications/me")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("동일 이벤트 중복 수신 시 알림이 1개만 생성된다")
    void duplicateEvent_createsOnlyOneNotification() throws Exception {
        // 독립적으로 ORDER_PLACED+EMAIL 템플릿이 있는지 확인하고 없으면 생성
        if (!templateRepository.existsByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL)) {
            templateRepository.save(NotificationTemplate.create(
                    TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                    "Order {{orderId}}", "Your order {{orderId}} placed."));
        }

        String userId = "user-dedup-" + UUID.randomUUID();
        String orderId = "order-dedup-" + UUID.randomUUID();
        String orderEventId = UUID.randomUUID().toString();
        String orderEventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", orderEventId,
                "event_type", "OrderPlaced",
                "occurred_at", "2026-03-28T00:00:00Z",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", 30000
                )
        ));

        orderPlacedEventConsumer.onMessage(orderEventJson);
        orderPlacedEventConsumer.onMessage(orderEventJson);

        PageResult<Notification> notifications = notificationRepository.findByUserId(userId, PageQuery.of(0, 100));
        long orderPlacedCount = notifications.content().stream()
                .filter(n -> n.getEventId().equals(orderEventId))
                .count();
        assertThat(orderPlacedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("알림 설정 조회 및 수정이 정상 동작한다")
    void preferenceFlow_getAndUpdate() throws Exception {
        String userId = "user-pref-" + UUID.randomUUID();

        // 기본 설정 조회 (없으면 기본값 생성)
        mockMvc.perform(get("/api/notifications/me/preferences")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.smsEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        // 설정 수정
        mockMvc.perform(put("/api/notifications/me/preferences")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"smsEnabled\":true,\"pushEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.smsEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false));

        // 수정된 설정 다시 조회
        mockMvc.perform(get("/api/notifications/me/preferences")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.smsEnabled").value(true));
    }

    @Test
    @DisplayName("템플릿 CRUD가 정상 동작한다")
    void templateCrud_flow() throws Exception {
        // Create
        var createResult = mockMvc.perform(post("/api/notifications/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PAYMENT_COMPLETED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Payment for {{orderId}}\",\"body\":\"Paid {{amount}} won.\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String templateId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("templateId").asText();

        // List
        mockMvc.perform(get("/api/notifications/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Update
        mockMvc.perform(put("/api/notifications/templates/" + templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Updated payment subject\",\"body\":\"Updated body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(templateId));

        // Duplicate create should fail
        mockMvc.perform(post("/api/notifications/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PAYMENT_COMPLETED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Dup\",\"body\":\"Dup\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ALREADY_EXISTS"));
    }
}
