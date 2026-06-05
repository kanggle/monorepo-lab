package com.example.erp.notification.integration;

import com.example.erp.notification.infrastructure.persistence.jpa.NotificationJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test (Testcontainers MySQL + Kafka + MockWebServer
 * JWKS). Covers AC-1..AC-3: publish the 4 approval events → consume → Notification
 * created with the correct recipient + DELIVERED delivery; dedupe; recipient-
 * scoped inbox read + foreign 404; idempotent mark-read.
 */
class NotificationEndToEndIntegrationTest extends AbstractNotificationIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private NotificationJpaEntity awaitOneFor(String recipientId) {
        await().atMost(Duration.ofSeconds(30)).until(() ->
                notificationJpa.findAll().stream()
                        .anyMatch(n -> n.getRecipientId().equals(recipientId)));
        return notificationJpa.findAll().stream()
                .filter(n -> n.getRecipientId().equals(recipientId))
                .findFirst().orElseThrow();
    }

    @Test
    void submittedNotifiesApproverAndDeliversInApp() throws Exception {
        String apprId = newId();
        String approver = "emp-approver-" + newId();
        String submitter = "emp-submitter-" + newId();
        publish(TOPIC_SUBMITTED, apprId, approvalEnvelope(newId(),
                "erp.approval.submitted", apprId, approver, submitter, null, null));

        NotificationJpaEntity n = awaitOneFor(approver);
        assertThat(n.getType().name()).isEqualTo("APPROVAL_SUBMITTED");
        assertThat(n.getSourceId()).isEqualTo(apprId);
        assertThat(n.isRead()).isFalse();

        // Delivery DELIVERED, attempt_count=1 (IN_APP).
        var deliveries = deliveryJpa.findAll().stream()
                .filter(d -> d.getNotificationId().equals(n.getId())).toList();
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getStatus().name()).isEqualTo("DELIVERED");
        assertThat(deliveries.get(0).getAttemptCount()).isEqualTo(1);
        assertThat(deliveries.get(0).getChannel().name()).isEqualTo("IN_APP");
    }

    @Test
    void approvedNotifiesSubmitterRejectedWithReason() throws Exception {
        String apprId = newId();
        String approver = "emp-approver-" + newId();
        String submitter = "emp-submitter-" + newId();
        publish(TOPIC_REJECTED, apprId, approvalEnvelope(newId(),
                "erp.approval.rejected", apprId, approver, submitter,
                "2026-06-05T11:00:00Z", "예산 근거 부족"));

        NotificationJpaEntity n = awaitOneFor(submitter);
        assertThat(n.getType().name()).isEqualTo("APPROVAL_REJECTED");
        assertThat(n.getBody()).contains("reason=예산 근거 부족");
    }

    @Test
    void withdrawnNotifiesApprover() throws Exception {
        String apprId = newId();
        String approver = "emp-approver-" + newId();
        String submitter = "emp-submitter-" + newId();
        publish(TOPIC_WITHDRAWN, apprId, approvalEnvelope(newId(),
                "erp.approval.withdrawn", apprId, approver, submitter,
                "2026-06-05T11:00:00Z", "기안 내용 수정 필요"));

        NotificationJpaEntity n = awaitOneFor(approver);
        assertThat(n.getType().name()).isEqualTo("APPROVAL_WITHDRAWN");
    }

    @Test
    void duplicateEventIdYieldsOneNotification() throws Exception {
        String apprId = newId();
        String eventId = newId();
        String approver = "emp-approver-" + newId();
        String submitter = "emp-submitter-" + newId();
        String env = approvalEnvelope(eventId, "erp.approval.submitted", apprId,
                approver, submitter, null, null);
        publish(TOPIC_SUBMITTED, apprId, env);
        awaitOneFor(approver);

        // Re-deliver the same eventId → dedupe skips it.
        publish(TOPIC_SUBMITTED, apprId, env);
        Thread.sleep(3000);

        long count = notificationJpa.findAll().stream()
                .filter(n -> n.getRecipientId().equals(approver)).count();
        assertThat(count).isEqualTo(1);
        assertThat(processedEventJpa.existsById(eventId)).isTrue();
    }

    @Test
    void inboxIsRecipientScopedAndMarkReadIdempotent() throws Exception {
        String apprId = newId();
        String approver = "emp-approver-" + newId();
        String submitter = "emp-submitter-" + newId();
        publish(TOPIC_SUBMITTED, apprId, approvalEnvelope(newId(),
                "erp.approval.submitted", apprId, approver, submitter, null, null));
        NotificationJpaEntity n = awaitOneFor(approver);

        String approverToken = erpTokenForRecipient(approver);
        String submitterToken = erpTokenForRecipient(submitter);

        // Approver sees their notification.
        HttpResponse<String> list = get("/api/erp/notifications", approverToken);
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(list.body());
        assertThat(body.at("/data").isArray()).isTrue();
        assertThat(body.at("/data").toString()).contains(n.getId());

        // The submitter (a different recipient) does NOT see the approver's notification.
        HttpResponse<String> foreign = get("/api/erp/notifications/" + n.getId(), submitterToken);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(foreign.body()).at("/code").asText())
                .isEqualTo("NOTIFICATION_NOT_FOUND");

        // Mark-read (first call) sets readAt.
        HttpResponse<String> mark1 = post("/api/erp/notifications/" + n.getId() + "/read", approverToken);
        assertThat(mark1.statusCode()).isEqualTo(200);
        JsonNode m1 = objectMapper.readTree(mark1.body());
        assertThat(m1.at("/data/read").asBoolean()).isTrue();
        String readAt1 = m1.at("/data/readAt").asText();
        assertThat(readAt1).isNotBlank();

        // Re-mark idempotent — same readAt preserved.
        HttpResponse<String> mark2 = post("/api/erp/notifications/" + n.getId() + "/read", approverToken);
        assertThat(mark2.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(mark2.body()).at("/data/readAt").asText())
                .isEqualTo(readAt1);
    }

    @Test
    void invalidEnvelopeIsNotPersisted() throws Exception {
        // Null eventId → invalid → immediate DLT (no notification persisted).
        String apprId = newId();
        String approver = "emp-approver-" + newId();
        String bad = approvalEnvelope(null, "erp.approval.submitted", apprId,
                approver, "emp-submitter", null, null);
        long before = notificationJpa.count();
        publish(TOPIC_SUBMITTED, apprId, bad);
        Thread.sleep(3000);
        // No notification for this approver was created.
        assertThat(notificationJpa.findAll().stream()
                .anyMatch(n -> n.getRecipientId().equals(approver))).isFalse();
        assertThat(notificationJpa.count()).isGreaterThanOrEqualTo(before);
    }

    @Test
    void delegatedNotifiesDelegate() throws Exception {
        // TASK-ERP-BE-014: erp.approval.delegated.v1 → notify the delegate.
        String grantId = "dgr-" + newId();
        String delegator = "emp-A-" + newId();
        String delegate = "emp-D-" + newId();
        publish(TOPIC_DELEGATED, grantId, delegationEnvelope(newId(), grantId,
                delegator, delegate, "2026-12-31T23:59:59Z", "휴가 대결"));

        NotificationJpaEntity n = awaitOneFor(delegate);
        assertThat(n.getType().name()).isEqualTo("DELEGATION_GRANTED");
        assertThat(n.getSourceType().name()).isEqualTo("DELEGATION");
        assertThat(n.getSourceId()).isEqualTo(grantId);
        assertThat(n.getTitle()).isEqualTo("결재 권한 위임됨");
        assertThat(n.getBody()).contains("delegatorId=" + delegator, "validTo=2026-12-31T23:59:59Z",
                "reason=휴가 대결");

        var deliveries = deliveryJpa.findAll().stream()
                .filter(d -> d.getNotificationId().equals(n.getId())).toList();
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getStatus().name()).isEqualTo("DELIVERED");
        assertThat(deliveries.get(0).getAttemptCount()).isEqualTo(1);
    }

    @Test
    void duplicateDelegationEventIdYieldsOneNotification() throws Exception {
        String grantId = "dgr-" + newId();
        String eventId = newId();
        String delegate = "emp-D-" + newId();
        String env = delegationEnvelope(eventId, grantId, "emp-A-" + newId(),
                delegate, null, null);
        publish(TOPIC_DELEGATED, grantId, env);
        NotificationJpaEntity n = awaitOneFor(delegate);
        // Open-ended grant → body renders "무기한".
        assertThat(n.getBody()).contains("validTo=무기한");

        publish(TOPIC_DELEGATED, grantId, env);
        Thread.sleep(3000);

        long count = notificationJpa.findAll().stream()
                .filter(x -> x.getRecipientId().equals(delegate)).count();
        assertThat(count).isEqualTo(1);
        assertThat(processedEventJpa.existsById(eventId)).isTrue();
    }

    @Test
    void delegationRevokedNotifiesDelegate() throws Exception {
        // TASK-ERP-BE-016: erp.approval.delegation.revoked.v1 → notify the delegate.
        String grantId = "dgr-" + newId();
        String delegator = "emp-A-" + newId();
        String delegate = "emp-D-" + newId();
        publish(TOPIC_DELEGATION_REVOKED, grantId, delegationRevokedEnvelope(newId(), grantId,
                delegator, delegate, "휴가 복귀"));

        NotificationJpaEntity n = awaitOneFor(delegate);
        assertThat(n.getType().name()).isEqualTo("DELEGATION_REVOKED");
        assertThat(n.getSourceType().name()).isEqualTo("DELEGATION");
        assertThat(n.getSourceId()).isEqualTo(grantId);
        assertThat(n.getTitle()).isEqualTo("위임 권한 회수됨");
        assertThat(n.getBody()).contains("delegatorId=" + delegator, "reason=휴가 복귀");

        var deliveries = deliveryJpa.findAll().stream()
                .filter(d -> d.getNotificationId().equals(n.getId())).toList();
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getStatus().name()).isEqualTo("DELIVERED");
    }

    @Test
    void duplicateRevokedEventIdYieldsOneNotification() throws Exception {
        String grantId = "dgr-" + newId();
        String eventId = newId();
        String delegate = "emp-D-" + newId();
        String env = delegationRevokedEnvelope(eventId, grantId, "emp-A-" + newId(), delegate, null);
        publish(TOPIC_DELEGATION_REVOKED, grantId, env);
        NotificationJpaEntity n = awaitOneFor(delegate);
        assertThat(n.getBody()).doesNotContain("reason=");

        publish(TOPIC_DELEGATION_REVOKED, grantId, env);
        Thread.sleep(3000);

        long count = notificationJpa.findAll().stream()
                .filter(x -> x.getRecipientId().equals(delegate)).count();
        assertThat(count).isEqualTo(1);
        assertThat(processedEventJpa.existsById(eventId)).isTrue();
    }

    @Test
    void recipientMappingCrossCheck() throws Exception {
        // submitted for approver-A and approved for submitter-B land in correct inboxes.
        String apprA = newId();
        String approverA = "emp-A-" + newId();
        String subA = "emp-subA-" + newId();
        publish(TOPIC_SUBMITTED, apprA, approvalEnvelope(newId(),
                "erp.approval.submitted", apprA, approverA, subA, null, null));

        String apprB = newId();
        String approverB = "emp-B-" + newId();
        String subB = "emp-subB-" + newId();
        publish(TOPIC_APPROVED, apprB, approvalEnvelope(newId(),
                "erp.approval.approved", apprB, approverB, subB, "2026-06-05T11:00:00Z", null));

        NotificationJpaEntity a = awaitOneFor(approverA);
        NotificationJpaEntity b = awaitOneFor(subB);
        assertThat(a.getType().name()).isEqualTo("APPROVAL_SUBMITTED");
        assertThat(b.getType().name()).isEqualTo("APPROVAL_APPROVED");
        // approverA did NOT get the approved notification; subB did NOT get submitted.
        List<NotificationJpaEntity> all = notificationJpa.findAll();
        assertThat(all.stream().filter(n -> n.getRecipientId().equals(subA)).count()).isZero();
        assertThat(all.stream().filter(n -> n.getRecipientId().equals(approverB)).count()).isZero();
    }
}
