package com.example.shipping.interfaces.rest.controller;

import com.example.shipping.application.command.UpdateShippingStatusCommand;
import com.example.shipping.application.result.ShippingPeriodCountResult;
import com.example.shipping.application.result.ShippingResult;
import com.example.shipping.application.result.ShippingSummary;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.shipping.application.service.RefreshTrackingService;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.application.service.ShippingQueryService;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.interfaces.rest.dto.request.UpdateShippingStatusRequest;
import com.example.shipping.interfaces.rest.dto.response.ShippingListResponse;
import com.example.shipping.interfaces.rest.dto.response.ShippingResponse;
import com.example.shipping.interfaces.rest.dto.response.ShippingSummaryResponse;
import com.example.shipping.interfaces.rest.dto.response.UpdateShippingStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shippings")
public class ShippingController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ShippingCommandService shippingCommandService;
    private final ShippingQueryService shippingQueryService;
    private final RefreshTrackingService refreshTrackingService;

    @GetMapping("/summary")
    public ResponseEntity<ShippingSummaryResponse> getSummary(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role header is required") String userRole
    ) {
        ShippingPeriodCountResult result = shippingQueryService.getSummary(userRole);
        return ResponseEntity.ok(ShippingSummaryResponse.from(result));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ShippingResponse> getShippingByOrderId(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @PathVariable String orderId
    ) {
        ShippingResult result = shippingQueryService.getShippingByOrderId(orderId, userId);
        return ResponseEntity.ok(ShippingResponse.from(result));
    }

    @PutMapping("/{shippingId}/status")
    public ResponseEntity<UpdateShippingStatusResponse> updateShippingStatus(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role header is required") String userRole,
            @PathVariable String shippingId,
            @Valid @RequestBody UpdateShippingStatusRequest request
    ) {
        ShippingStatus targetStatus = parseStatus(request.status());

        boolean deductWmsInventory = Boolean.TRUE.equals(request.deductWmsInventory());
        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shippingId, targetStatus, request.trackingNumber(), request.carrier(),
                deductWmsInventory, userRole);
        UpdateShippingStatusResult result = shippingCommandService.updateStatus(command);
        return ResponseEntity.ok(UpdateShippingStatusResponse.from(result));
    }

    /**
     * Admin-triggered carrier tracking refresh (TASK-BE-293): fetch the shipment's
     * carrier status and advance it forward accordingly. Best-effort — a carrier
     * outage / unknown status leaves the shipment unchanged (200 with the current
     * status). Default {@code shipping.carrier.mode=mock} = no-op.
     */
    @PostMapping("/{shippingId}/refresh-tracking")
    public ResponseEntity<UpdateShippingStatusResponse> refreshTracking(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role header is required") String userRole,
            @PathVariable String shippingId
    ) {
        UpdateShippingStatusResult result = refreshTrackingService.refreshFromCarrier(shippingId, userRole);
        return ResponseEntity.ok(UpdateShippingStatusResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<ShippingListResponse> listShippings(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role header is required") String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        ShippingStatus shippingStatus = status != null && !status.isBlank() ? parseStatus(status) : null;
        PageQuery pageQuery = new PageQuery(safePage, safeSize, "createdAt", "DESC");
        PageResult<ShippingSummary> result = shippingQueryService.listShippings(userRole, shippingStatus, pageQuery);
        return ResponseEntity.ok(ShippingListResponse.from(result));
    }

    private ShippingStatus parseStatus(String status) {
        try {
            return ShippingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new com.example.shipping.domain.exception.InvalidShippingException(
                    "Invalid shipping status: " + status);
        }
    }
}
