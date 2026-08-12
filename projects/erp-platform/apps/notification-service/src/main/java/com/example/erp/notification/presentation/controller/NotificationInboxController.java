package com.example.erp.notification.presentation.controller;

import com.example.common.page.PageResult;
import com.example.erp.notification.application.MarkNotificationReadUseCase;
import com.example.erp.notification.application.QueryInboxUseCase;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.presentation.dto.ApiEnvelope;
import com.example.erp.notification.presentation.dto.NotificationResponse;
import com.example.erp.notification.presentation.security.ReadAuthorizationGate;
import com.example.security.oauth2.TenantClaimValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only in-app inbox (rest-api). Notifications are created ONLY by the event
 * consumer — there is no notification-creating REST endpoint. Every endpoint is
 * recipient-scoped to the caller's JWT {@code sub} (E6 data-scope, fail-closed);
 * a foreign-recipient id is 404 {@code NOTIFICATION_NOT_FOUND} (no existence
 * leak). Endpoints per notification-api.md:
 * <ul>
 *   <li>GET {@code /api/erp/notifications} — caller's inbox (?unread/page/size)</li>
 *   <li>GET {@code /api/erp/notifications/{id}} — single own notification</li>
 *   <li>POST {@code /api/erp/notifications/{id}/read} — idempotent mark-read</li>
 * </ul>
 *
 * <p><b>Query tenant = the caller's own validated claim, not a constant</b>
 * (TASK-ERP-BE-043 / ADR-ERP-001 — D). Every method used to pass
 * {@code erpplatform.oauth2.required-tenant-id} (default {@code "erp"}) as the
 * query tenant. That is the HTTP <b>domain key</b>
 * ({@link ReadAuthorizationGate} reads the same property and correctly names its
 * field {@code domainKey}), so the inbox was asking for rows of a tenant that has
 * never existed: rows are written with the envelope's tenant ({@code demo-corp}).
 * Fixing only the consumer gate would have left the inbox at
 * {@code totalElements 0} with a full table behind it — the write axis and the
 * read axis have to be the same axis. The claim is already validated upstream by
 * the entitlement-trust dual-accept gate (decode-time validator +
 * {@code TenantClaimEnforcer}), so this reads a trusted value, and narrowing by it
 * can only ever shrink the result set (it composes with, and never widens, the
 * recipient scoping). A platform super-admin's wildcard {@code tenant_id = "*"}
 * scopes to a tenant literally named {@code *} and therefore sees nothing — the
 * same outcome as before this change (the constant {@code erp} matched no row
 * either), and correct in kind: a notification inbox is recipient-owned, and the
 * super-admin persona is not a recipient.
 */
@RestController
@RequestMapping("/api/erp/notifications")
@RequiredArgsConstructor
public class NotificationInboxController {

    private static final int MAX_SIZE = 100;

    private final QueryInboxUseCase queryInbox;
    private final MarkNotificationReadUseCase markRead;
    private final ReadAuthorizationGate readGate;

    /**
     * Fallback for a token that carries no {@code tenant_id} claim at all. Keeps
     * the pre-BE-043 behaviour for that (unexercised) shape rather than widening
     * the query — it is the domain key, so it matches no row and the inbox is
     * empty, which is the fail-closed answer for a caller that cannot name a tenant.
     */
    @Value("${erpplatform.oauth2.required-tenant-id:erp}")
    private String fallbackTenantId;

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<NotificationResponse>>> list(
            @RequestParam(required = false) Boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        validatePaging(page, size);
        String recipientId = recipient(jwt);

        // unread=true → only unread; unread=false → only read; omitted → all.
        Boolean readFilter = unread == null ? null : !unread;
        PageResult<Notification> result = queryInbox.list(tenantOf(jwt), recipientId, readFilter,
                page, Math.min(size, MAX_SIZE));
        List<NotificationResponse> data = result.content().stream()
                .map(NotificationResponse::from)
                .toList();
        // page/size sourced from the result object (not the raw request) — guaranteed to agree
        // since QueryInboxUseCase.list echoes the exact page/size it was called with (AC-4).
        return ResponseEntity.ok(ApiEnvelope.ofList(data, result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<NotificationResponse>> getOne(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        Notification notification = queryInbox.getOne(tenantOf(jwt), recipient(jwt), id);
        return ResponseEntity.ok(ApiEnvelope.of(NotificationResponse.from(notification)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiEnvelope<NotificationResponse>> markRead(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        Notification notification = markRead.markRead(tenantOf(jwt), recipient(jwt), id);
        return ResponseEntity.ok(ApiEnvelope.of(NotificationResponse.from(notification)));
    }

    private String recipient(Jwt jwt) {
        return jwt.getSubject();
    }

    /** The caller's own (already dual-accept-validated) tenant — see the class javadoc. */
    private String tenantOf(Jwt jwt) {
        String claim = jwt == null
                ? null : jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        return claim == null || claim.isBlank() ? fallbackTenantId : claim;
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
