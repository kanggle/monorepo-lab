package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.request.UpdatePreferenceRequest;
import com.example.notification.adapter.in.rest.dto.response.NotificationDetailResponse;
import com.example.notification.adapter.in.rest.dto.response.NotificationListResponse;
import com.example.notification.adapter.in.rest.dto.response.PreferenceResponse;
import com.example.notification.application.command.UpdatePreferenceCommand;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.application.result.GetNotificationResult;
import com.example.notification.application.result.GetPreferenceResult;
import com.example.notification.application.result.ListNotificationsResult;
import com.example.notification.application.port.in.ManagePreferenceUseCase;
import com.example.notification.application.port.in.QueryNotificationUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final QueryNotificationUseCase notificationQueryService;
    private final ManagePreferenceUseCase preferenceService;

    @GetMapping("/me")
    public ResponseEntity<NotificationListResponse> getMyNotifications(
            @RequestHeader("X-User-Id") @NotBlank String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<ListNotificationsResult.NotificationSummary> notifications = notificationQueryService.getNotifications(
                userId, new PageQuery(Math.max(page, 0), size < 1 ? 20 : Math.min(size, PageQuery.MAX_SIZE), null, null));
        return ResponseEntity.ok(NotificationListResponse.from(notifications));
    }

    @GetMapping("/me/{notificationId}")
    public ResponseEntity<NotificationDetailResponse> getNotificationDetail(
            @RequestHeader("X-User-Id") @NotBlank String userId,
            @PathVariable String notificationId
    ) {
        GetNotificationResult result = notificationQueryService.getNotificationDetail(userId, notificationId);
        return ResponseEntity.ok(NotificationDetailResponse.from(result));
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<PreferenceResponse> getPreferences(
            @RequestHeader("X-User-Id") @NotBlank String userId
    ) {
        GetPreferenceResult result = preferenceService.getPreference(userId);
        return ResponseEntity.ok(PreferenceResponse.from(result));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @RequestHeader("X-User-Id") @NotBlank String userId,
            @Valid @RequestBody UpdatePreferenceRequest request
    ) {
        UpdatePreferenceCommand command = new UpdatePreferenceCommand(
                userId, request.emailEnabled(), request.smsEnabled(), request.pushEnabled());
        GetPreferenceResult result = preferenceService.updatePreference(command);
        return ResponseEntity.ok(PreferenceResponse.from(result));
    }
}
