package com.example.admin.presentation;

import com.example.admin.application.ManagePartnershipParticipantUseCase;
import com.example.admin.application.ManagePartnershipParticipantUseCase.ParticipantResult;
import com.example.admin.application.PartnershipManagementUseCase;
import com.example.admin.application.PartnershipManagementUseCase.InvitePartnershipCommand;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.application.port.TenantPartnershipPort.PartnershipView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.ScopeSet;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-477 / ADR-MONO-045 — the cross-org partnership management surface. Each
 * mutation is gated by {@code @RequiresPermission(partnership.manage)} (AOP) + the D2
 * {@link com.example.admin.application.TenantScopeGuard} on the acting-side tenant
 * (inside the use-case) + reason-gated. Colon-verb transitions per AIP-136.
 *
 * <p>The acting tenant is the {@code X-Tenant-Id} header (the actor's switched/active
 * tenant): the host for invite, the partner for accept/participants, either party for
 * suspend/reactivate/terminate.
 */
@RestController
@RequestMapping("/api/admin/partnerships")
@RequiredArgsConstructor
public class PartnershipAdminController {

    private final PartnershipManagementUseCase managementUseCase;
    private final ManagePartnershipParticipantUseCase participantUseCase;

    // ── invite ────────────────────────────────────────────────────────────────

    @PostMapping
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<PartnershipResponse> invite(
            @RequestHeader(value = "X-Tenant-Id", required = false) String hostTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestBody InvitePartnershipRequest body) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        ScopeDto scope = body == null ? null : body.delegatedScope();
        InvitePartnershipCommand cmd = new InvitePartnershipCommand(
                body == null ? null : body.partnerTenantId(),
                scope == null ? null : scope.domains(),
                scope == null ? null : scope.roles());
        PartnershipView created = managementUseCase.invite(
                hostTenantId, cmd, OperatorContextHolder.require(), reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(PartnershipResponse.from(created));
    }

    // ── lifecycle (colon verbs, AIP-136) ────────────────────────────────────────

    @PostMapping("/{partnershipId}:accept")
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<PartnershipResponse> accept(
            @PathVariable String partnershipId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        PartnershipView updated = managementUseCase.accept(
                partnershipId, actingTenantId, OperatorContextHolder.require(), reason);
        return ResponseEntity.ok(PartnershipResponse.from(updated));
    }

    @PostMapping("/{partnershipId}:suspend")
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<PartnershipResponse> suspend(
            @PathVariable String partnershipId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        PartnershipView updated = managementUseCase.suspend(
                partnershipId, actingTenantId, OperatorContextHolder.require(), reason);
        return ResponseEntity.ok(PartnershipResponse.from(updated));
    }

    @PostMapping("/{partnershipId}:reactivate")
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<PartnershipResponse> reactivate(
            @PathVariable String partnershipId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        PartnershipView updated = managementUseCase.reactivate(
                partnershipId, actingTenantId, OperatorContextHolder.require(), reason);
        return ResponseEntity.ok(PartnershipResponse.from(updated));
    }

    @PostMapping("/{partnershipId}:terminate")
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<PartnershipResponse> terminate(
            @PathVariable String partnershipId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        PartnershipView updated = managementUseCase.terminate(
                partnershipId, actingTenantId, OperatorContextHolder.require(), reason);
        return ResponseEntity.ok(PartnershipResponse.from(updated));
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @GetMapping
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<PartnershipListResponse> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        TenantPartnershipPort.PartnershipPage result =
                managementUseCase.list(actingTenantId, role, status, Math.max(page, 0), cappedSize);
        List<PartnershipListItem> items = result.content().stream()
                .map(v -> PartnershipListItem.from(v, actingTenantId,
                        managementUseCase.participantCount(v.internalId())))
                .toList();
        return ResponseEntity.ok(new PartnershipListResponse(
                items, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    // ── participants ─────────────────────────────────────────────────────────

    @PostMapping("/{partnershipId}/participants/{operatorId}")
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<ParticipantResponse> addParticipant(
            @PathVariable String partnershipId,
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestBody(required = false) AddParticipantRequest body) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        ScopeDto scope = body == null ? null : body.participantScope();
        ParticipantResult result = participantUseCase.addParticipant(
                partnershipId, operatorId, actingTenantId,
                scope == null ? null : scope.domains(),
                scope == null ? null : scope.roles(),
                OperatorContextHolder.require(), reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(ParticipantResponse.from(result));
    }

    @DeleteMapping("/{partnershipId}/participants/{operatorId}")
    @RequiresPermission(Permission.PARTNERSHIP_MANAGE)
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String partnershipId,
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String actingTenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason) {
        String reason = ControllerReasonSupport.requireReason(headerReason);
        participantUseCase.removeParticipant(
                partnershipId, operatorId, actingTenantId, OperatorContextHolder.require(), reason);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /** {@code {domains, roles}} scope shape shared by request + response. */
    public record ScopeDto(List<String> domains, List<String> roles) {
        static ScopeDto from(ScopeSet scope) {
            return scope == null ? null : new ScopeDto(scope.domains(), scope.roles());
        }
    }

    public record InvitePartnershipRequest(String partnerTenantId, ScopeDto delegatedScope) {}

    public record AddParticipantRequest(ScopeDto participantScope) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartnershipResponse(
            String partnershipId,
            String hostTenantId,
            String partnerTenantId,
            String status,
            ScopeDto delegatedScope,
            String invitedAt,
            String acceptedAt,
            String terminatedAt
    ) {
        static PartnershipResponse from(PartnershipView v) {
            return new PartnershipResponse(
                    v.partnershipId(),
                    v.hostTenantId(),
                    v.partnerTenantId(),
                    v.status().name(),
                    ScopeDto.from(v.delegatedScope()),
                    iso(v.invitedAt()),
                    iso(v.acceptedAt()),
                    iso(v.terminatedAt()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartnershipListItem(
            String partnershipId,
            String hostTenantId,
            String partnerTenantId,
            String status,
            ScopeDto delegatedScope,
            String myRole,
            String invitedAt,
            String acceptedAt,
            int participantCount
    ) {
        static PartnershipListItem from(PartnershipView v, String activeTenant, int participantCount) {
            String myRole = activeTenant != null && activeTenant.equals(v.hostTenantId())
                    ? "host" : "partner";
            return new PartnershipListItem(
                    v.partnershipId(), v.hostTenantId(), v.partnerTenantId(), v.status().name(),
                    ScopeDto.from(v.delegatedScope()), myRole,
                    iso(v.invitedAt()), iso(v.acceptedAt()), participantCount);
        }
    }

    public record PartnershipListResponse(
            List<PartnershipListItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParticipantResponse(
            String partnershipId,
            String operatorId,
            ScopeDto participantScope,
            String assignedAt
    ) {
        static ParticipantResponse from(ParticipantResult r) {
            return new ParticipantResponse(
                    r.partnershipId(), r.operatorId(),
                    ScopeDto.from(r.participantScope()), iso(r.assignedAt()));
        }
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
