package com.example.fanplatform.notification.presentation.controller;

import com.example.fanplatform.notification.application.ActorContext;
import com.example.fanplatform.notification.application.ActorContextResolver;
import com.example.fanplatform.notification.application.ListNotificationsUseCase;
import com.example.fanplatform.notification.application.MarkNotificationReadUseCase;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationPage;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.presentation.dto.ApiEnvelope;
import com.example.fanplatform.notification.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only in-app notification inbox (the secondary rest-api surface). A
 * notification is created ONLY by the event consumer — there is no
 * notification-creating endpoint. Every route is scoped to the caller's JWT
 * {@code sub} + {@code tenant_id}; a foreign id → 404 NOTIFICATION_NOT_FOUND.
 * The fan-platform gateway maps {@code /api/v1/notifications/**} →
 * {@code /api/fan/notifications/**}.
 */
@RestController
@RequestMapping("/api/fan/notifications")
@RequiredArgsConstructor
public class NotificationInboxController {

    private static final int MAX_SIZE = 100;

    private final ListNotificationsUseCase listNotifications;
    private final MarkNotificationReadUseCase markNotificationRead;

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<NotificationResponse>>> list(
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        validatePaging(page, size);
        // `unread` is the normative cross-domain filter (notification-inbox-contract.md § 2.1);
        // it maps onto fan's status enum. The pre-existing `status=UNREAD|READ` param is kept
        // for backward compatibility and used only when `unread` is absent.
        NotificationStatus statusFilter = unread != null
                ? (unread ? NotificationStatus.UNREAD : NotificationStatus.READ)
                : parseStatus(status);

        NotificationPage result = listNotifications.list(actor, statusFilter, page, Math.min(size, MAX_SIZE));
        List<NotificationResponse> data = result.content().stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.ofList(data, result.page(), result.size(), result.totalElements()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiEnvelope<NotificationResponse>> markRead(@PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        Notification notification = markNotificationRead.markRead(actor, id);
        return ResponseEntity.ok(ApiEnvelope.of(NotificationResponse.from(notification)));
    }

    private static NotificationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NotificationStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status filter: " + raw + " (expected UNREAD or READ)");
        }
    }

    private static void validatePaging(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
