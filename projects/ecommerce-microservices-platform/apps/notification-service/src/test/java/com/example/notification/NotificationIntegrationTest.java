package com.example.notification;

import com.example.notification.adapter.in.event.OrderPlacedEventConsumer;
import com.example.notification.adapter.in.event.PaymentCompletedEventConsumer;
import com.example.notification.domain.tenant.TenantContext;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
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
@Tag("integration")
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
    private PaymentCompletedEventConsumer paymentCompletedEventConsumer;

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
                            .header("X-User-Role", "ECOMMERCE_OPERATOR")
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
        PageResult<Notification> notifications = notificationRepository.findByUserId(userId, new PageQuery(0, 20, null, null));
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

        PageResult<Notification> notifications = notificationRepository.findByUserId(userId, new PageQuery(0, 100, null, null));
        long orderPlacedCount = notifications.content().stream()
                .filter(n -> n.getEventId().equals(orderEventId))
                .count();
        assertThat(orderPlacedCount).isEqualTo(1);
    }

    /**
     * TASK-BE-539 AC-1/AC-2 — a single event fans out to one row per <em>sendable</em> channel.
     *
     * <p>{@code NotificationSendService} loops {@code NotificationChannel.values()} and saves one
     * {@code Notification} per channel that has both a template and a registered sender, all
     * carrying the same {@code event_id}. {@code uq_notifications_event_id} as defined by V4
     * permits only one row per {@code event_id}, so the <em>second</em> channel's insert violates
     * it at commit-time flush — on a first delivery, not a redelivery. The whole transaction rolls
     * back, {@code existsByEventId} is therefore still empty on every retry, and the event burns
     * all three attempts before landing in the DLQ with no send record.
     *
     * <p>This test must be RED against the V4 index shape (AC-2). If it passes before the V8
     * migration, it has failed to reproduce the firing condition — most likely because only one
     * channel actually resolved a sender or a template.
     */
    @Test
    @DisplayName("BE-539: 한 이벤트가 발송 가능한 채널마다 1행씩 커밋한다 (V8 인덱스 이전에는 RED)")
    void multiChannelEvent_commitsOneRowPerSendableChannel() throws Exception {
        seedTemplate(TemplateType.PAYMENT_COMPLETED, NotificationChannel.EMAIL,
                "Payment {{orderId}}", "Paid {{amount}}.");
        seedTemplate(TemplateType.PAYMENT_COMPLETED, NotificationChannel.PUSH,
                "결제 완료", "{{amount}} 결제 완료.");

        String userId = "user-multichannel-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        paymentCompletedEventConsumer.onMessage(paymentCompletedEventJson(eventId, userId, null));

        PageResult<Notification> notifications =
                notificationRepository.findByUserId(userId, new PageQuery(0, 100, null, null));

        // Asserted as a set, not a count: a count of 2 would also pass if both rows were EMAIL.
        assertThat(notifications.content())
                .extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.PUSH);
        assertThat(notifications.content())
                .allSatisfy(n -> assertThat(n.getEventId()).isEqualTo(eventId));
    }

    /**
     * TASK-BE-539 AC-3 — the V8 index must not weaken redelivery dedup.
     *
     * <p>F1 in the task: widening the index is not the same as dropping it. The pre-check at
     * {@code NotificationSendService:52} still has to short-circuit a redelivered event, and the
     * row count has to stay at one-per-channel rather than doubling.
     */
    @Test
    @DisplayName("BE-539 AC-3: 동일 event_id 재전송은 채널당 1행을 유지한다")
    void multiChannelEvent_redelivery_doesNotAddRows() throws Exception {
        seedTemplate(TemplateType.PAYMENT_COMPLETED, NotificationChannel.EMAIL,
                "Payment {{orderId}}", "Paid {{amount}}.");
        seedTemplate(TemplateType.PAYMENT_COMPLETED, NotificationChannel.PUSH,
                "결제 완료", "{{amount}} 결제 완료.");

        String userId = "user-redelivery-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        paymentCompletedEventConsumer.onMessage(paymentCompletedEventJson(eventId, userId, null));
        paymentCompletedEventConsumer.onMessage(paymentCompletedEventJson(eventId, userId, null));

        PageResult<Notification> notifications =
                notificationRepository.findByUserId(userId, new PageQuery(0, 100, null, null));
        assertThat(notifications.content())
                .extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.PUSH);
    }

    /**
     * TASK-BE-539 AC-4 — the same {@code event_id} arriving for two tenants must not collide.
     *
     * <p>F2 in the task: adding {@code channel} but not {@code tenant_id} leaves the pre-check
     * (tenant-scoped, {@code NotificationSendService:52}) and the constraint (global under V4)
     * disagreeing about scope — the same defect class as TASK-BE-540. Event ids are only unique
     * per producer, so two tenants can legitimately carry the same one.
     */
    @Test
    @DisplayName("BE-539 AC-4: 서로 다른 테넌트의 동일 event_id 는 각자 자기 행을 갖는다")
    void sameEventIdAcrossTenants_eachTenantKeepsItsOwnRows() throws Exception {
        String otherTenant = "omni-corp";
        seedTemplateFor(TenantContext.DEFAULT_TENANT_ID, TemplateType.PAYMENT_COMPLETED,
                NotificationChannel.EMAIL, "Payment {{orderId}}", "Paid {{amount}}.");
        seedTemplateFor(otherTenant, TemplateType.PAYMENT_COMPLETED,
                NotificationChannel.EMAIL, "Payment {{orderId}}", "Paid {{amount}}.");

        String userId = "user-crosstenant-" + UUID.randomUUID();
        String sharedEventId = UUID.randomUUID().toString();

        paymentCompletedEventConsumer.onMessage(
                paymentCompletedEventJson(sharedEventId, userId, TenantContext.DEFAULT_TENANT_ID));
        paymentCompletedEventConsumer.onMessage(
                paymentCompletedEventJson(sharedEventId, userId, otherTenant));

        assertThat(notificationsOfTenant(TenantContext.DEFAULT_TENANT_ID, userId)).hasSize(1);
        assertThat(notificationsOfTenant(otherTenant, userId)).hasSize(1);
    }

    private String paymentCompletedEventJson(String eventId, String userId, String tenantId)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "PaymentCompleted",
                "occurred_at", "2026-07-20T00:00:00Z",
                "source", "payment-service",
                "tenant_id", tenantId != null ? tenantId : TenantContext.DEFAULT_TENANT_ID,
                "payload", Map.of(
                        "orderId", "order-" + UUID.randomUUID(),
                        "userId", userId,
                        "amount", 50000,
                        "paidAt", "2026-07-20T00:00:00Z"
                )
        ));
    }

    private void seedTemplate(TemplateType type, NotificationChannel channel, String subject, String body) {
        seedTemplateFor(TenantContext.DEFAULT_TENANT_ID, type, channel, subject, body);
    }

    /** Templates are per-(tenant, type, channel); {@code create} reads the tenant off the context. */
    private void seedTemplateFor(String tenantId, TemplateType type, NotificationChannel channel,
                                 String subject, String body) {
        try {
            TenantContext.set(tenantId);
            if (templateRepository.findByTypeAndChannel(type, channel, tenantId).isEmpty()) {
                templateRepository.save(NotificationTemplate.create(type, channel, subject, body));
            }
        } finally {
            TenantContext.clear();
        }
    }

    private List<Notification> notificationsOfTenant(String tenantId, String userId) {
        try {
            TenantContext.set(tenantId);
            return notificationRepository.findByUserId(userId, new PageQuery(0, 100, null, null)).content();
        } finally {
            TenantContext.clear();
        }
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
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PAYMENT_COMPLETED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Payment for {{orderId}}\",\"body\":\"Paid {{amount}} won.\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String templateId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("templateId").asText();

        // List
        mockMvc.perform(get("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Update
        mockMvc.perform(put("/api/notifications/templates/" + templateId)
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Updated payment subject\",\"body\":\"Updated body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(templateId));

        // Duplicate create should fail
        mockMvc.perform(post("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PAYMENT_COMPLETED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Dup\",\"body\":\"Dup\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ALREADY_EXISTS"));
    }
}
