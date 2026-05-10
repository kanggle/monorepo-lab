package com.wms.outbound.adapter.in.web.controller;

import com.wms.outbound.adapter.in.web.dto.response.RetryTmsNotificationResponse;
import com.wms.outbound.adapter.in.web.util.RequestContext;
import com.wms.outbound.application.command.RetryTmsNotificationCommand;
import com.wms.outbound.application.port.in.RetryTmsNotificationUseCase;
import com.wms.outbound.application.result.RetryTmsNotificationResult;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shipment-scoped operational endpoints, per
 * {@code outbound-service-api.md} §4.3:
 *
 * <ul>
 *   <li>{@code POST /api/v1/outbound/shipments/{id}:retry-tms-notify} —
 *       Manual TMS retry. Auth: {@code OUTBOUND_ADMIN}. Requires
 *       {@code Idempotency-Key}.</li>
 * </ul>
 *
 * <p>Path syntax {@code {id}:retry-tms-notify} matches the existing
 * {@code OrderController} {@code {id}:cancel} convention — the colon is a
 * literal path segment, not a Spring placeholder.
 */
@RestController
@RequestMapping("/api/v1/outbound/shipments")
public class ShipmentController {

    private final RetryTmsNotificationUseCase retryTmsNotification;

    public ShipmentController(RetryTmsNotificationUseCase retryTmsNotification) {
        this.retryTmsNotification = retryTmsNotification;
    }

    @PostMapping("/{shipmentId}:retry-tms-notify")
    public ResponseEntity<RetryTmsNotificationResponse> retryTmsNotify(
            @PathVariable UUID shipmentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        RequestContext.requireIdempotencyKey(idempotencyKey);
        RetryTmsNotificationCommand command = new RetryTmsNotificationCommand(
                shipmentId,
                RequestContext.actorId(jwt),
                RequestContext.callerRoles(authentication));
        RetryTmsNotificationResult result = retryTmsNotification.retry(command);
        return ResponseEntity.ok(RetryTmsNotificationResponse.from(result));
    }
}
