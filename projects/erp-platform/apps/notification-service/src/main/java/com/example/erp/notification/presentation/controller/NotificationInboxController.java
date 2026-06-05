package com.example.erp.notification.presentation.controller;

import com.example.erp.notification.application.MarkNotificationReadUseCase;
import com.example.erp.notification.application.QueryInboxUseCase;
import com.example.erp.notification.application.query.InboxPage;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.presentation.dto.ApiEnvelope;
import com.example.erp.notification.presentation.dto.NotificationResponse;
import com.example.erp.notification.presentation.security.ReadAuthorizationGate;
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
 */
@RestController
@RequestMapping("/api/erp/notifications")
@RequiredArgsConstructor
public class NotificationInboxController {

    private static final int MAX_SIZE = 100;

    private final QueryInboxUseCase queryInbox;
    private final MarkNotificationReadUseCase markRead;
    private final ReadAuthorizationGate readGate;

    @Value("${erpplatform.oauth2.required-tenant-id:erp}")
    private String tenantId;

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
        InboxPage result = queryInbox.list(tenantId, recipientId, readFilter, page,
                Math.min(size, MAX_SIZE));
        List<NotificationResponse> data = result.content().stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.ofList(data, result.page(),
                Math.min(size, MAX_SIZE), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<NotificationResponse>> getOne(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        Notification notification = queryInbox.getOne(tenantId, recipient(jwt), id);
        return ResponseEntity.ok(ApiEnvelope.of(NotificationResponse.from(notification)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiEnvelope<NotificationResponse>> markRead(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        readGate.requireRead(jwt);
        Notification notification = markRead.markRead(tenantId, recipient(jwt), id);
        return ResponseEntity.ok(ApiEnvelope.of(NotificationResponse.from(notification)));
    }

    private String recipient(Jwt jwt) {
        return jwt.getSubject();
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
