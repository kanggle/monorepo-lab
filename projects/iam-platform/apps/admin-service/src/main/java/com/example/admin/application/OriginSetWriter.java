package com.example.admin.application;

import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.domain.rbac.DomainGrantSource;
import com.example.admin.domain.rbac.DomainPermissionSetPresets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * TASK-MONO-773 OD-4 · OD-5 (ADR-MONO-083 OD-5 P9 · data-model.md § operator_domain_set_grant «ORIGIN 행») — the ONE
 * code path that writes a company's first administrator's work-permission set: one {@code operator_domain_set_grant}
 * row (operator, the company, preset {@code all-domains-full}, source {@code ORIGIN}, {@code granted_by NULL}).
 *
 * <p>Its two callers are the two places a company's first administrator is born, each inside its own atomic
 * transaction: the self-service creation ({@link FirstAdminProvisioner}, S3) and the acceptance of a first-admin
 * invitation ({@link OperatorInvitationAcceptanceWriter}, S4). 🔴 No entitlement check — ADR-083 D5's one exception:
 * the tenant may have zero subscriptions; the ceiling is auth-service's {@code ∩ entitled} at token time (P3). Never
 * {@code '*'} (V0054 CHECK), never another company.
 */
@Component
@RequiredArgsConstructor
public class OriginSetWriter {

    private final DomainPermissionSetPort setPort;

    /** Joins the caller's transaction (the port's writes do). */
    public void write(long operatorInternalId, String tenantId, Instant now) {
        write(setPort, operatorInternalId, tenantId, now);
    }

    /** The shared body — {@link FirstAdminProvisioner} calls it with its own port (S3's constructor unchanged). */
    static void write(DomainPermissionSetPort setPort, long operatorInternalId, String tenantId, Instant now) {
        DomainPermissionSetPort.SetView preset = setPort.findPresets().stream()
                .filter(s -> DomainPermissionSetPresets.ALL_DOMAINS_FULL.equals(s.systemKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "system preset '" + DomainPermissionSetPresets.ALL_DOMAINS_FULL + "' is missing (V0052)"));
        setPort.insertGrant(operatorInternalId, tenantId, preset.internalId(), DomainGrantSource.ORIGIN,
                null, null, now);
    }
}
