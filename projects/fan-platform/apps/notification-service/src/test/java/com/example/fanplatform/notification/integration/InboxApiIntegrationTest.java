package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.infrastructure.jpa.NotificationJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The inbox read API: list (tenant + account scoped, status filter) + idempotent
 * mark-read; a cross-account id → 404 (no leak) (architecture.md § Inbox Read
 * API, AC-4).
 */
class InboxApiIntegrationTest extends NotificationServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    NotificationJpaRepository notifications;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        truncateAll();
        awaitListenersAssigned();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> post(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(h), String.class);
    }

    private int dataSize(ResponseEntity<String> resp) throws Exception {
        JsonNode root = mapper.readTree(resp.getBody());
        return root.path("data").size();
    }

    @Test
    @DisplayName("list is account-scoped + status-filtered; mark-read is idempotent; cross-account → 404")
    void inboxLifecycle() throws Exception {
        // Seed: two notifications for acc-1, one for acc-2 (via the consume path).
        producer().send(TOPIC_ACTIVATED, "mem-a", activatedEnvelope("evt-a", "mem-a", "acc-1", "PREMIUM"));
        producer().send(TOPIC_CANCELED, "mem-b", canceledEnvelope("evt-b", "mem-b", "acc-1", "PREMIUM"));
        producer().send(TOPIC_ACTIVATED, "mem-c", activatedEnvelope("evt-c", "mem-c", "acc-2", "MEMBERS_ONLY"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(notifications.count()).isEqualTo(3));

        String acc1 = jwt.signFanToken("acc-1");
        String acc2 = jwt.signFanToken("acc-2");

        // acc-1 sees only its own two.
        assertThat(dataSize(get("/api/fan/notifications", acc1))).isEqualTo(2);
        // status=UNREAD → both still unread.
        assertThat(dataSize(get("/api/fan/notifications?status=UNREAD", acc1))).isEqualTo(2);
        // acc-2 sees only its own one.
        assertThat(dataSize(get("/api/fan/notifications", acc2))).isEqualTo(1);

        // Mark one of acc-1's notifications read.
        String acc1NotificationId = notifications.findAll().stream()
                .filter(n -> n.getAccountId().equals("acc-1"))
                .map(Notification::getId)
                .findFirst()
                .orElseThrow();

        assertThat(post("/api/fan/notifications/" + acc1NotificationId + "/read", acc1).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // Re-mark is an idempotent 200.
        assertThat(post("/api/fan/notifications/" + acc1NotificationId + "/read", acc1).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Now exactly one of acc-1's notifications is READ.
        assertThat(dataSize(get("/api/fan/notifications?status=READ", acc1))).isEqualTo(1);
        assertThat(dataSize(get("/api/fan/notifications?status=UNREAD", acc1))).isEqualTo(1);

        // acc-2 cannot read acc-1's notification → 404 (no existence leak).
        assertThat(post("/api/fan/notifications/" + acc1NotificationId + "/read", acc2).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
