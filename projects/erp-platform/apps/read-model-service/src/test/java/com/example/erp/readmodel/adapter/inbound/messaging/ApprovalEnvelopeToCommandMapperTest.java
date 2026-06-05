package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.ApprovalFactCommand;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ApprovalEnvelopeToCommandMapper}: status bound to topic,
 * payload extraction, finalizedAt/reason absent on submitted, and invalid /
 * unmappable envelope → {@link InvalidEnvelopeException} (immediate DLT).
 */
class ApprovalEnvelopeToCommandMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApprovalEnvelopeToCommandMapper mapper =
            new ApprovalEnvelopeToCommandMapper(objectMapper);

    private static final String SUBMITTED_JSON = """
            { "eventId": "evt-1", "eventType": "erp.approval.submitted",
              "occurredAt": "2026-01-01T00:00:00Z", "tenantId": "erp",
              "source": "erp-platform-approval-service", "aggregateType": "ApprovalRequest",
              "aggregateId": "appr-1",
              "payload": { "approvalRequestId": "appr-1", "subjectType": "DEPARTMENT",
                "subjectId": "dept-1", "approverId": "emp-appr", "submitterId": "emp-sub",
                "tenantId": "erp", "occurredAt": "2026-01-01T00:00:00Z", "actor": "emp-sub" } }
            """;

    private static final String REJECTED_JSON = """
            { "eventId": "evt-2", "eventType": "erp.approval.rejected",
              "occurredAt": "2026-01-02T00:00:00Z", "tenantId": "erp",
              "source": "erp-platform-approval-service", "aggregateType": "ApprovalRequest",
              "aggregateId": "appr-1",
              "payload": { "approvalRequestId": "appr-1", "subjectType": "EMPLOYEE",
                "subjectId": "emp-1", "approverId": "emp-appr", "submitterId": "emp-sub",
                "tenantId": "erp", "occurredAt": "2026-01-02T00:00:00Z", "actor": "emp-appr",
                "finalizedAt": "2026-01-02T01:00:00Z", "reason": "예산 근거 부족" } }
            """;

    @Test
    void mapsSubmittedWithSubmittedAtAndNoFinalizedOrReason() {
        ApprovalFactCommand cmd = mapper.map(SUBMITTED_JSON, "erp.approval.submitted.v1",
                ApprovalStatus.SUBMITTED);

        assertThat(cmd.status()).isEqualTo(ApprovalStatus.SUBMITTED);
        assertThat(cmd.approvalRequestId()).isEqualTo("appr-1");
        assertThat(cmd.subjectType()).isEqualTo(ApprovalSubjectType.DEPARTMENT);
        assertThat(cmd.subjectId()).isEqualTo("dept-1");
        assertThat(cmd.approverId()).isEqualTo("emp-appr");
        assertThat(cmd.submittedAt()).isNotNull();
        assertThat(cmd.finalizedAt()).isNull();
        assertThat(cmd.reason()).isNull();
    }

    @Test
    void mapsRejectedWithFinalizedAtAndReasonNoSubmittedAt() {
        ApprovalFactCommand cmd = mapper.map(REJECTED_JSON, "erp.approval.rejected.v1",
                ApprovalStatus.REJECTED);

        assertThat(cmd.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(cmd.subjectType()).isEqualTo(ApprovalSubjectType.EMPLOYEE);
        assertThat(cmd.submittedAt()).isNull();
        assertThat(cmd.finalizedAt()).isNotNull();
        assertThat(cmd.reason()).isEqualTo("예산 근거 부족");
    }

    @Test
    void nullEventIdIsInvalidEnvelope() {
        String json = SUBMITTED_JSON.replace("\"eventId\": \"evt-1\"", "\"eventId\": null");
        assertThatThrownBy(() -> mapper.map(json, "erp.approval.submitted.v1",
                ApprovalStatus.SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void missingPayloadIsInvalidEnvelope() {
        String json = """
                { "eventId": "evt-1", "aggregateId": "appr-1" }
                """;
        assertThatThrownBy(() -> mapper.map(json, "erp.approval.submitted.v1",
                ApprovalStatus.SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void unknownSubjectTypeIsInvalidEnvelope() {
        String json = SUBMITTED_JSON.replace("\"subjectType\": \"DEPARTMENT\"",
                "\"subjectType\": \"GALAXY\"");
        assertThatThrownBy(() -> mapper.map(json, "erp.approval.submitted.v1",
                ApprovalStatus.SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void malformedJsonIsInvalidEnvelope() {
        assertThatThrownBy(() -> mapper.map("{not-json", "erp.approval.submitted.v1",
                ApprovalStatus.SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }
}
