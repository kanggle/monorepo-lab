package com.example.admin.application.tenant;

import com.example.admin.application.OperatorInvitationResult;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-MONO-773 S4 — the tenant response's {@code firstAdmin} summary (admin-api.md § POST /api/admin/tenants
 * «테넌트 응답의 {@code firstAdmin} 요약»), judged when read (no job — OD-2):
 *
 * <ul>
 *   <li>{@code ACCEPTED} — a first-admin invitation was accepted (the tenant is normally ACTIVE; PENDING = only the
 *       activation is missing — R4);</li>
 *   <li>{@code INVITED} / {@code EXPIRED} — a PENDING tenant with a PENDING first-admin invitation, before / at or
 *       after its {@code expiresAt};</li>
 *   <li>{@code NONE} — a PENDING tenant without a PENDING first-admin invitation (cancelled · compensation failed) —
 *       email / invitationId / expiresAt all {@code null};</li>
 *   <li>{@code null} — any other tenant (pre-773, self-service, cleaned up).</li>
 * </ul>
 * A page is answered with ONE read of {@code operator_invitation} («테넌트마다 호출 금지»).
 */
@Service
@RequiredArgsConstructor
public class TenantFirstAdminSummaryQuery {

    public static final String INVITED = "INVITED";
    public static final String EXPIRED = "EXPIRED";
    public static final String NONE = "NONE";
    public static final String ACCEPTED = "ACCEPTED";

    private final OperatorInvitationPort invitationPort;

    /** The summary of one tenant (single GET · PATCH). */
    public FirstAdminSummary summarize(TenantSummary tenant) {
        if (tenant == null) {
            return null;
        }
        return summarizeAll(List.of(tenant)).get(tenant.tenantId());
    }

    /** tenantId → summary ({@code null} values are omitted from the map — absent ⟺ {@code null}). */
    public Map<String, FirstAdminSummary> summarizeAll(Collection<TenantSummary> tenants) {
        Map<String, FirstAdminSummary> out = new HashMap<>();
        if (tenants == null || tenants.isEmpty()) {
            return out;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (TenantSummary t : tenants) {
            if (t != null && t.tenantId() != null) {
                ids.add(t.tenantId());
            }
        }
        Map<String, List<InvitationView>> byTenant = new HashMap<>();
        for (InvitationView v : invitationPort.findFirstAdminInvitations(ids)) {
            byTenant.computeIfAbsent(v.tenantId(), k -> new ArrayList<>()).add(v);
        }
        Instant now = Instant.now();
        for (TenantSummary t : tenants) {
            if (t == null || t.tenantId() == null) {
                continue;
            }
            FirstAdminSummary s = judge(t.status(), byTenant.getOrDefault(t.tenantId(), List.of()), now);
            if (s != null) {
                out.put(t.tenantId(), s);
            }
        }
        return out;
    }

    /** The summary of a just-issued invitation (the POST response — always {@code INVITED}). */
    public static FirstAdminSummary ofIssued(OperatorInvitationResult invitation) {
        if (invitation == null) {
            return null;
        }
        return new FirstAdminSummary(invitation.expired() ? EXPIRED : INVITED, invitation.email(),
                invitation.invitationId(), invitation.expiresAt());
    }

    /** {@code rows} — this tenant's first-admin invitations, newest first. */
    static FirstAdminSummary judge(String tenantStatus, List<InvitationView> rows, Instant now) {
        for (InvitationView v : rows) {
            if ("ACCEPTED".equals(v.status())) {
                return new FirstAdminSummary(ACCEPTED, v.email(), v.invitationId(), v.expiresAt());
            }
        }
        if (!"PENDING".equals(tenantStatus)) {
            return null;
        }
        for (InvitationView v : rows) {
            if ("PENDING".equals(v.status())) {
                String state = OperatorInvitationResult.isExpired(v.status(), v.expiresAt(), now) ? EXPIRED : INVITED;
                return new FirstAdminSummary(state, v.email(), v.invitationId(), v.expiresAt());
            }
        }
        return new FirstAdminSummary(NONE, null, null, null);
    }

    /** One tenant's first-admin summary. */
    public record FirstAdminSummary(String state, String email, String invitationId, Instant expiresAt) {}
}
