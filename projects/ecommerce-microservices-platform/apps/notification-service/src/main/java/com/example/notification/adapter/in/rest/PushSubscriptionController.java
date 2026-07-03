package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.request.DeletePushSubscriptionRequest;
import com.example.notification.adapter.in.rest.dto.request.RegisterPushSubscriptionRequest;
import com.example.notification.adapter.in.rest.dto.response.PushSubscriptionListResponse;
import com.example.notification.adapter.in.rest.dto.response.SubscriptionIdResponse;
import com.example.notification.adapter.in.rest.dto.response.VapidPublicKeyResponse;
import com.example.notification.application.command.RegisterPushSubscriptionCommand;
import com.example.notification.application.port.in.ManagePushSubscriptionUseCase;
import com.example.notification.application.result.RegisterSubscriptionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class PushSubscriptionController {

    private final ManagePushSubscriptionUseCase pushSubscriptionService;

    /** Public: the VAPID public key is not secret; clients need it to subscribe. */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> getVapidPublicKey() {
        return ResponseEntity.ok(VapidPublicKeyResponse.of(pushSubscriptionService.getVapidPublicKey()));
    }

    @PostMapping("/me/push-subscriptions")
    public ResponseEntity<SubscriptionIdResponse> register(
            @RequestHeader("X-User-Id") @NotBlank String userId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @Valid @RequestBody RegisterPushSubscriptionRequest request
    ) {
        RegisterSubscriptionResult result = pushSubscriptionService.register(new RegisterPushSubscriptionCommand(
                userId, request.endpoint(), request.expirationTime(),
                request.keys().p256dh(), request.keys().auth(), userAgent));
        // New subscription → 201, existing endpoint refreshed → 200 (per notification-api.md).
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(SubscriptionIdResponse.from(result));
    }

    /** The caller's push subscriptions (one per browser/device), newest first — for the device list UI. */
    @GetMapping("/me/push-subscriptions")
    public ResponseEntity<PushSubscriptionListResponse> list(
            @RequestHeader("X-User-Id") @NotBlank String userId
    ) {
        return ResponseEntity.ok(
                PushSubscriptionListResponse.from(pushSubscriptionService.listByUser(userId)));
    }

    @DeleteMapping("/me/push-subscriptions")
    public ResponseEntity<Void> unregister(
            @RequestHeader("X-User-Id") @NotBlank String userId,
            @Valid @RequestBody DeletePushSubscriptionRequest request
    ) {
        pushSubscriptionService.unregister(userId, request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
