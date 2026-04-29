package com.wms.outbound.adapter.in.web.controller;

import static com.wms.outbound.adapter.in.web.controller.PickingController.actorId;
import static com.wms.outbound.adapter.in.web.controller.PickingController.callerRoles;

import com.wms.outbound.adapter.in.web.dto.request.ConfirmShippingRequest;
import com.wms.outbound.adapter.in.web.dto.response.ShipmentResponse;
import com.wms.outbound.application.command.ConfirmShippingCommand;
import com.wms.outbound.application.port.in.ConfirmShippingUseCase;
import com.wms.outbound.application.result.ShipmentResult;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shipping endpoint per {@code outbound-service-api.md} §4.1:
 * <ul>
 *   <li>{@code POST /api/v1/outbound/orders/{id}/shipments}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/outbound/orders")
public class ShippingController {

    private final ConfirmShippingUseCase confirmShipping;

    public ShippingController(ConfirmShippingUseCase confirmShipping) {
        this.confirmShipping = confirmShipping;
    }

    @PostMapping("/{orderId}/shipments")
    public ResponseEntity<ShipmentResponse> confirmShipping(
            @PathVariable UUID orderId,
            @Valid @RequestBody ConfirmShippingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        ConfirmShippingCommand command = new ConfirmShippingCommand(
                orderId,
                request.version(),
                request.carrierCode(),
                actorId(jwt),
                callerRoles(authentication));
        ShipmentResult result = confirmShipping.confirm(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentResponse.from(result));
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
    }
}
