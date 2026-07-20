package com.example.notification;

import com.example.notification.adapter.in.event.OrderPlacedEventConsumer;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.domain.model.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — notification-service cross-tenant leak regression (ADR-MONO-030 §2.3 AC-4;
 * TASK-BE-372). Drives the admin template surface through {@code TenantContextFilter}
 * (gateway {@code X-Tenant-Id} header → request tenant context) so it proves M2 layer 2
 * (context propagation) + M3 layer 3 (persistence {@code WHERE tenant_id}) together:
 * <ul>
 *   <li>(a) the admin template list under tenant A excludes tenant B's template;</li>
 *   <li>(b) the admin {@code GET /templates/{id}} (gap-fill) on tenant B's template under
 *       tenant A context returns 404 (existence hidden, M3 cross-tenant-read-is-not-found
 *       — not 403);</li>
 *   <li>(c) the SYSTEM path (the OrderPlaced consumer) binds the originating event tenant
 *       on the created notification — no HTTP {@code TenantContext} on the Kafka thread —
 *       and the send-path template resolution stays within that tenant;</li>
 *   <li>(d) the {@code (tenant_id, type, channel)} uniqueness allows the SAME
 *       (type, channel) template in two tenants.</li>
 * </ul>
 *
 * <p>Rows are seeded deterministically via {@link JdbcTemplate} with an explicit
 * {@code tenant_id} (no per-tenant HTTP create seam for templates without a tenant
 * header), then the admin assertions run through the HTTP surface (tenant filter +
 * persistence scoping end-to-end) and the system assertion drives the consumer bean
 * directly. The Docker-free {@code :check} slice never loads the real wiring; this
 * Testcontainers {@code @SpringBootTest} is the authoritative isolation proof.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — notification cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String ROLE_ADMIN = "ECOMMERCE_OPERATOR";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @TestConfiguration
    static class MockMailConfig {
        @Bean
        @Primary
        public NotificationSender mockEmailSender() {
            return new NotificationSender() {
                @Override
                public void send(String recipient, String subject, String body) {
                    // no-op mock so the send path completes without a real SMTP server
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderPlacedEventConsumer orderPlacedEventConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Per-method isolation: the static Postgres container is shared across all methods of
     * this class, and every method seeds an (ORDER_PLACED, EMAIL) template for tenant A/B.
     * Without cleanup the `(tenant_id, type, channel)` unique constraint
     * ({@code uq_template_tenant_type_channel}) makes the second method's seed collide
     * (DuplicateKeyException). Truncate the seeded tables before each method.
     */
    @BeforeEach
    void cleanTemplatesAndNotifications() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM notification_templates");
        jdbcTemplate.update("DELETE FROM push_subscriptions");
    }

    /** Seeds an ORDER_PLACED/EMAIL template for a tenant; returns the template id. */
    private String seedTemplate(String tenantId, String subject) {
        String templateId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO notification_templates (template_id, tenant_id, type, channel, "
                        + "subject, body, created_at, updated_at) "
                        + "VALUES (?, ?, 'ORDER_PLACED', 'EMAIL', ?, ?, ?, ?)",
                templateId, tenantId, subject, "Body {{orderId}}", now, now);
        return templateId;
    }

    private String tenantOfNotification(String notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM notifications WHERE notification_id = ?", String.class, notificationId);
    }

    @Test
    @DisplayName("(a) 관리자 템플릿 목록은 자기 테넌트 템플릿만 포함한다")
    void crossTenantAdminTemplateListIsScopedToTenant() throws Exception {
        String templateIdA = seedTemplate(TENANT_A, "A-subject-" + System.nanoTime());

        // tenant B's admin list does NOT contain tenant A's template.
        mockMvc.perform(get("/api/notifications/templates")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(templateIdA))));

        // tenant A's admin list DOES contain it.
        mockMvc.perform(get("/api/notifications/templates")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(templateIdA)));
    }

    @Test
    @DisplayName("(b) GET /templates/{id} 를 다른 테넌트 컨텍스트로 호출하면 404 (자기 테넌트는 200·body 포함)")
    void crossTenantGetTemplate_returns404() throws Exception {
        String templateIdB = seedTemplate(TENANT_B, "B-subject-" + System.nanoTime());

        // tenant A cannot read tenant B's template — 404, not 403 (existence hidden, M3).
        mockMvc.perform(get("/api/notifications/templates/{id}", templateIdB)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));

        // tenant B reads its own template (200; the detail response carries body).
        mockMvc.perform(get("/api/notifications/templates/{id}", templateIdB)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(templateIdB))
                .andExpect(jsonPath("$.type").value("ORDER_PLACED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.body").exists());
    }

    @Test
    @DisplayName("(d) (tenant_id,type,channel) 유일성은 두 테넌트가 동일 (type,channel)을 갖도록 허용한다")
    void tenantScopedTemplateUniqueness_allowsSameTypeChannelInTwoTenants() {
        // Same (ORDER_PLACED, EMAIL) in two tenants — the new (tenant_id, type, channel)
        // unique constraint must NOT collide (the old global (type, channel) would).
        String templateIdA = seedTemplate(TENANT_A, "dup-A-" + System.nanoTime());
        String templateIdB = seedTemplate(TENANT_B, "dup-B-" + System.nanoTime());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_templates WHERE template_id IN (?, ?)",
                Integer.class, templateIdA, templateIdB);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("(c) 컨슈머가 생성한 알림은 이벤트 테넌트로 바인딩되고 발송경로 템플릿 해석도 테넌트 내에 머문다")
    void consumerCreatedNotification_bindsEventTenant() throws Exception {
        // tenant A owns the ORDER_PLACED/EMAIL template; the event envelope carries tenant A.
        seedTemplate(TENANT_A, "consumer-A-" + System.nanoTime());
        // A same-(type,channel) template in tenant B must NOT be picked up by the tenant-A event.
        seedTemplate(TENANT_B, "consumer-B-" + System.nanoTime());

        String userId = "user-" + UUID.randomUUID();
        String orderId = "order-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "OrderPlaced",
                "occurred_at", "2026-06-14T00:00:00Z",
                "source", "order-service",
                "tenant_id", TENANT_A,
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", 50000)));

        orderPlacedEventConsumer.onMessage(eventJson);

        // Exactly one notification was created, bound to the event's tenant (tenant A), and
        // it resolved tenant A's template (subject rendered from the tenant-A body).
        String notificationId = jdbcTemplate.queryForObject(
                "SELECT notification_id FROM notifications WHERE event_id = ?", String.class, eventId);
        assertThat(tenantOfNotification(notificationId)).isEqualTo(TENANT_A);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    /**
     * TASK-BE-540 case A — a second tenant registering the SAME endpoint must not touch the
     * first tenant's row.
     *
     * <p>An endpoint is issued per (browser, origin, VAPID key). This deployment has one
     * storefront origin and one VAPID keypair, and the tenant comes from the JWT claim rather
     * than the hostname — so the same browser signing in as a user of another tenant produces
     * the SAME endpoint string. The register upsert used to look the endpoint up globally, so
     * it found the other tenant's row and rotated that tenant's keys, returning 200. The
     * per-tenant constraint {@code uq_push_subscriptions_tenant_endpoint} never caught it
     * because the INSERT branch was never taken.
     *
     * <p>Asserted on the stored keys, not on the row count: a count of 2 would also pass if
     * tenant B had inserted its row *and* clobbered tenant A's keys on the way.
     */
    @Test
    @DisplayName("BE-540: 다른 테넌트가 같은 endpoint 를 등록해도 앞 테넌트의 키를 회전시키지 않는다")
    void sameEndpointInSecondTenant_doesNotMutateFirstTenantRow() throws Exception {
        String endpoint = "https://push.example/ep-" + UUID.randomUUID();

        registerPush(TENANT_A, "user-a", endpoint, "p256dh-A", "auth-A")
                .andExpect(status().isCreated());
        registerPush(TENANT_B, "user-b", endpoint, "p256dh-B", "auth-B")
                .andExpect(status().isCreated());

        assertThat(keysOf(TENANT_A, endpoint)).containsExactly("p256dh-A", "auth-A");
        assertThat(keysOf(TENANT_B, endpoint)).containsExactly("p256dh-B", "auth-B");
    }

    /**
     * TASK-BE-540 case A — re-registering within one tenant must still rotate keys (the upsert
     * this fix must not break). F1-style guard: scoping the lookup is not the same as removing it.
     */
    @Test
    @DisplayName("BE-540: 같은 테넌트에서 재등록하면 키가 회전한다(업서트 유지)")
    void sameEndpointSameTenant_stillRotatesKeys() throws Exception {
        String endpoint = "https://push.example/ep-" + UUID.randomUUID();

        registerPush(TENANT_A, "user-a", endpoint, "p256dh-1", "auth-1")
                .andExpect(status().isCreated());
        registerPush(TENANT_A, "user-a", endpoint, "p256dh-2", "auth-2")
                .andExpect(status().isOk());

        assertThat(keysOf(TENANT_A, endpoint)).containsExactly("p256dh-2", "auth-2");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM push_subscriptions WHERE endpoint = ?", Integer.class, endpoint);
        assertThat(rows).isEqualTo(1);
    }

    private ResultActions registerPush(String tenantId, String userId, String endpoint,
                                       String p256dh, String auth) throws Exception {
        return mockMvc.perform(post("/api/notifications/me/push-subscriptions")
                .header(TENANT_HEADER, tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "endpoint", endpoint,
                        "keys", Map.of("p256dh", p256dh, "auth", auth)))));
    }

    /** The stored (p256dh, auth) pair for one tenant's row on this endpoint. */
    private List<String> keysOf(String tenantId, String endpoint) {
        return jdbcTemplate.queryForObject(
                "SELECT p256dh, auth FROM push_subscriptions WHERE tenant_id = ? AND endpoint = ?",
                (rs, n) -> List.of(rs.getString("p256dh"), rs.getString("auth")),
                tenantId, endpoint);
    }
}
