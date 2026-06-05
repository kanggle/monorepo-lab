package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeToCommandMapperTest {

    private final ObjectMapper om = new ObjectMapper();
    private final EnvelopeToCommandMapper mapper = new EnvelopeToCommandMapper(om, "erp");

    private String envelope(String eventId, String tenantId, String approverId, String submitterId,
                            String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalRequestId", "appr-1");
        payload.put("subjectType", "DEPARTMENT");
        payload.put("subjectId", "dept-1");
        if (approverId != null) payload.put("approverId", approverId);
        if (submitterId != null) payload.put("submitterId", submitterId);
        payload.put("tenantId", tenantId);
        payload.put("actor", "emp-submitter");
        if (reason != null) payload.put("reason", reason);
        Map<String, Object> env = new LinkedHashMap<>();
        if (eventId != null) env.put("eventId", eventId);
        env.put("eventType", "erp.approval.submitted");
        env.put("tenantId", tenantId);
        env.put("aggregateType", "ApprovalRequest");
        env.put("aggregateId", "appr-1");
        env.put("payload", payload);
        try {
            return om.writeValueAsString(env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsValidEnvelope() {
        NotifyOnApprovalCommand cmd = mapper.map(
                envelope("evt-1", "erp", "emp-approver", "emp-submitter", null),
                "erp.approval.submitted.v1", NotificationType.APPROVAL_SUBMITTED);
        assertThat(cmd.event().eventId()).isEqualTo("evt-1");
        assertThat(cmd.event().approverId()).isEqualTo("emp-approver");
        assertThat(cmd.event().type()).isEqualTo(NotificationType.APPROVAL_SUBMITTED);
    }

    @Test
    void unparseableEnvelopeIsInvalid() {
        assertThatThrownBy(() -> mapper.map("not-json", "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void nullEventIdIsInvalid() {
        assertThatThrownBy(() -> mapper.map(
                envelope(null, "erp", "emp-approver", "emp-submitter", null),
                "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void nonErpTenantIsInvalid() {
        assertThatThrownBy(() -> mapper.map(
                envelope("evt-1", "other", "emp-approver", "emp-submitter", null),
                "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void nullResolvedRecipientFieldIsInvalid() {
        // submitted → approver is the recipient; a null approverId cannot deliver.
        assertThatThrownBy(() -> mapper.map(
                envelope("evt-1", "erp", null, "emp-submitter", null),
                "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }
}
