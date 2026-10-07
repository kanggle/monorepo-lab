package com.example.erp.notification.integration;

import com.example.erp.notification.infrastructure.persistence.jpa.NotificationJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TASK-MONO-776 AC-6 (notification-api.md § v1.1): an {@code APPROVAL_SUBMITTED} notification
 * addressed to the approver EMPLOYEE is visible to the ACCOUNT linked to that employee, 404 to a
 * different account, and an unlinked account sees an empty inbox. Account ({@code acc-…}) and
 * employee ({@code emp-…}) ids are deliberately distinct — the base stub's identity convention
 * (sub "emp-x" ↔ employee "emp-x") would make the two coincide and hide the predicate.
 */
class RecipientIsLinkedEmployeeIntegrationTest extends AbstractNotificationIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void resetLinks() {
        ACCOUNT_LINKS.clear();
        UNLINKED_ACCOUNTS.clear();
        meHttpStatus = 200;
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void submittedNotificationReachesTheLinkedAccountOnly() throws Exception {
        String apprId = newId();
        String approverEmp = "emp-approver-" + newId();
        String approverAcc = "acc-approver-" + newId();
        String otherAcc = "acc-other-" + newId();
        String strangerAcc = "acc-stranger-" + newId();
        ACCOUNT_LINKS.put(approverAcc, approverEmp);
        ACCOUNT_LINKS.put(otherAcc, "emp-other-" + newId());
        UNLINKED_ACCOUNTS.add(strangerAcc);

        publish(TOPIC_SUBMITTED, apprId, approvalEnvelope(newId(), "erp.approval.submitted",
                apprId, approverEmp, "emp-submitter-" + newId(), null, null));
        await().atMost(Duration.ofSeconds(30)).until(() -> notificationJpa.findAll().stream()
                .anyMatch(n -> n.getRecipientId().equals(approverEmp)));
        NotificationJpaEntity n = notificationJpa.findAll().stream()
                .filter(x -> x.getRecipientId().equals(approverEmp)).findFirst().orElseThrow();

        HttpResponse<String> mine = get("/api/erp/notifications", erpTokenForRecipient(approverAcc));
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(mine.body()).at("/data").toString()).contains(n.getId());

        HttpResponse<String> other = get("/api/erp/notifications/" + n.getId(),
                erpTokenForRecipient(otherAcc));
        assertThat(other.statusCode()).isEqualTo(404);

        HttpResponse<String> stranger = get("/api/erp/notifications",
                erpTokenForRecipient(strangerAcc));
        assertThat(stranger.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(stranger.body());
        assertThat(body.at("/meta/totalElements").asLong()).isZero();
    }

    @Test
    void masterdataOutageIs503NotAnEmptyInbox() throws Exception {
        meHttpStatus = 503;
        HttpResponse<String> res = get("/api/erp/notifications",
                erpTokenForRecipient("acc-any-" + newId()));
        assertThat(res.statusCode()).isEqualTo(503);
        assertThat(objectMapper.readTree(res.body()).at("/code").asText())
                .isEqualTo("SERVICE_UNAVAILABLE");
    }
}
