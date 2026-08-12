package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationRevokedCommand;
import com.example.erp.notification.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>TASK-ERP-BE-043 / ADR-ERP-001 — D.</b> Every fixture here used to pass the
 * tenant literal {@code "erp"}, and two cases ({@code nonErpTenantIsInvalid},
 * {@code nonErpTenantDelegationIsInvalid}) pinned the rejection of anything else.
 * Both facts were wrong about production: erp records carry {@code demo-corp}
 * (the console operator reaches erp by assume-tenant) and <b>zero</b> erp rows
 * have ever carried the string {@code erp} — so the suite was green on a value
 * the system never produces while the real value went to {@code .DLT} and the ERP
 * inbox sat at {@code totalElements 0}. The fixtures now use the real tenant, and
 * the two rejection cases are replaced by the invariant that actually survives
 * the ADR: an envelope naming <b>no</b> tenant is invalid.
 */
class EnvelopeToCommandMapperTest {

    /** The tenant erp records actually carry — never the literal "erp". */
    private static final String TENANT = "demo-corp";

    private final ObjectMapper om = new ObjectMapper();
    private final EnvelopeToCommandMapper mapper = new EnvelopeToCommandMapper(om);

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
                envelope("evt-1", TENANT, "emp-approver", "emp-submitter", null),
                "erp.approval.submitted.v1", NotificationType.APPROVAL_SUBMITTED);
        assertThat(cmd.event().eventId()).isEqualTo("evt-1");
        assertThat(cmd.event().approverId()).isEqualTo("emp-approver");
        assertThat(cmd.event().type()).isEqualTo(NotificationType.APPROVAL_SUBMITTED);
        // The customer tenant is carried onto the notification, not rejected.
        assertThat(cmd.event().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void unparseableEnvelopeIsInvalid() {
        assertThatThrownBy(() -> mapper.map("not-json", "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void nullEventIdIsInvalid() {
        assertThatThrownBy(() -> mapper.map(
                envelope(null, TENANT, "emp-approver", "emp-submitter", null),
                "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    /**
     * Replaces {@code nonErpTenantIsInvalid}. Any tenant is accepted (the ratchet,
     * not this gate, is what notices erp going multi-tenant) — only an envelope
     * that names no tenant at all is invalid.
     */
    @Test
    void anotherTenantIsAcceptedAndCarried() {
        NotifyOnApprovalCommand cmd = mapper.map(
                envelope("evt-1", "other-corp", "emp-approver", "emp-submitter", null),
                "erp.approval.submitted.v1", NotificationType.APPROVAL_SUBMITTED);
        assertThat(cmd.event().tenantId()).isEqualTo("other-corp");
    }

    @Test
    void anEnvelopeThatNamesNoTenantAnywhereIsInvalid() {
        assertThatThrownBy(() -> mapper.map(
                envelope("evt-1", null, "emp-approver", "emp-submitter", null),
                "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("Missing tenantId");
    }

    @Test
    void nullResolvedRecipientFieldIsInvalid() {
        // submitted → approver is the recipient; a null approverId cannot deliver.
        assertThatThrownBy(() -> mapper.map(
                envelope("evt-1", TENANT, null, "emp-submitter", null),
                "t", NotificationType.APPROVAL_SUBMITTED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    // ---- TASK-ERP-BE-014: mapDelegation ----

    private String delegationEnvelope(String eventId, String tenantId, String delegateId,
                                      String validTo, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grantId", "dgr-1");
        payload.put("delegatorId", "emp-A");
        if (delegateId != null) payload.put("delegateId", delegateId);
        payload.put("validFrom", "2026-06-06T00:00:00Z");
        if (validTo != null) payload.put("validTo", validTo);
        if (reason != null) payload.put("reason", reason);
        payload.put("tenantId", tenantId);
        payload.put("actor", "emp-A");
        Map<String, Object> env = new LinkedHashMap<>();
        if (eventId != null) env.put("eventId", eventId);
        env.put("eventType", "erp.approval.delegated");
        env.put("tenantId", tenantId);
        env.put("aggregateType", "DelegationGrant");
        env.put("aggregateId", "dgr-1");
        env.put("payload", payload);
        try {
            return om.writeValueAsString(env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsValidDelegationEnvelope() {
        NotifyOnDelegationCommand cmd = mapper.mapDelegation(
                delegationEnvelope("evt-d", TENANT, "emp-D", "2026-12-31T23:59:59Z", "휴가 대결"),
                "erp.approval.delegated.v1");
        assertThat(cmd.event().eventId()).isEqualTo("evt-d");
        assertThat(cmd.event().grantId()).isEqualTo("dgr-1");
        assertThat(cmd.event().delegatorId()).isEqualTo("emp-A");
        assertThat(cmd.event().delegateId()).isEqualTo("emp-D");
        assertThat(cmd.event().validTo()).isEqualTo("2026-12-31T23:59:59Z");
        assertThat(cmd.event().reason()).isEqualTo("휴가 대결");
        assertThat(cmd.event().type()).isEqualTo(NotificationType.DELEGATION_GRANTED);
    }

    @Test
    void delegationOpenEndedHasNullValidTo() {
        NotifyOnDelegationCommand cmd = mapper.mapDelegation(
                delegationEnvelope("evt-d", TENANT, "emp-D", null, null),
                "erp.approval.delegated.v1");
        assertThat(cmd.event().validTo()).isNull();
        assertThat(cmd.event().reason()).isNull();
    }

    @Test
    void nullDelegateIdIsInvalid() {
        // delegateId is the recipient; absent → cannot deliver → DLT.
        assertThatThrownBy(() -> mapper.mapDelegation(
                delegationEnvelope("evt-d", TENANT, null, null, null),
                "erp.approval.delegated.v1"))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    /** Replaces {@code nonErpTenantDelegationIsInvalid} — see the class javadoc. */
    @Test
    void delegationWithAnotherTenantIsAcceptedAndCarried() {
        NotifyOnDelegationCommand cmd = mapper.mapDelegation(
                delegationEnvelope("evt-d", "other-corp", "emp-D", null, null),
                "erp.approval.delegated.v1");
        assertThat(cmd.event().tenantId()).isEqualTo("other-corp");
    }

    @Test
    void delegationEnvelopeThatNamesNoTenantIsInvalid() {
        assertThatThrownBy(() -> mapper.mapDelegation(
                delegationEnvelope("evt-d", null, "emp-D", null, null),
                "erp.approval.delegated.v1"))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("Missing tenantId");
    }

    // ---- TASK-ERP-BE-016: mapDelegationRevoked ----

    private String revokedEnvelope(String eventId, String tenantId, String delegateId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grantId", "dgr-1");
        payload.put("delegatorId", "emp-A");
        if (delegateId != null) payload.put("delegateId", delegateId);
        if (reason != null) payload.put("reason", reason);
        payload.put("tenantId", tenantId);
        payload.put("actor", "emp-A");
        Map<String, Object> env = new LinkedHashMap<>();
        if (eventId != null) env.put("eventId", eventId);
        env.put("eventType", "erp.approval.delegation.revoked");
        env.put("tenantId", tenantId);
        env.put("aggregateType", "DelegationGrant");
        env.put("aggregateId", "dgr-1");
        env.put("payload", payload);
        try {
            return om.writeValueAsString(env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsValidRevokedEnvelope() {
        NotifyOnDelegationRevokedCommand cmd = mapper.mapDelegationRevoked(
                revokedEnvelope("evt-r", TENANT, "emp-D", "휴가 복귀"),
                "erp.approval.delegation.revoked.v1");
        assertThat(cmd.event().eventId()).isEqualTo("evt-r");
        assertThat(cmd.event().grantId()).isEqualTo("dgr-1");
        assertThat(cmd.event().delegatorId()).isEqualTo("emp-A");
        assertThat(cmd.event().delegateId()).isEqualTo("emp-D");
        assertThat(cmd.event().reason()).isEqualTo("휴가 복귀");
        assertThat(cmd.event().type()).isEqualTo(NotificationType.DELEGATION_REVOKED);
    }

    @Test
    void revokedWithoutReasonHasNullReason() {
        NotifyOnDelegationRevokedCommand cmd = mapper.mapDelegationRevoked(
                revokedEnvelope("evt-r", TENANT, "emp-D", null),
                "erp.approval.delegation.revoked.v1");
        assertThat(cmd.event().reason()).isNull();
    }

    @Test
    void nullDelegateIdRevokedIsInvalid() {
        assertThatThrownBy(() -> mapper.mapDelegationRevoked(
                revokedEnvelope("evt-r", TENANT, null, null),
                "erp.approval.delegation.revoked.v1"))
                .isInstanceOf(InvalidEnvelopeException.class);
    }
}
