package com.wms.outbound.adapter.in.web.controller;

import static com.wms.outbound.adapter.in.web.controller.PickingController.actorId;
import static com.wms.outbound.adapter.in.web.controller.PickingController.callerRoles;

import com.wms.outbound.adapter.in.web.dto.request.ConfirmPackingRequest;
import com.wms.outbound.adapter.in.web.dto.request.CreatePackingUnitRequest;
import com.wms.outbound.adapter.in.web.dto.request.SealPackingUnitRequest;
import com.wms.outbound.adapter.in.web.dto.response.OrderResponse;
import com.wms.outbound.adapter.in.web.dto.response.PackingUnitResponse;
import com.wms.outbound.application.command.ConfirmPackingCommand;
import com.wms.outbound.application.command.CreatePackingUnitCommand;
import com.wms.outbound.application.command.CreatePackingUnitLineCommand;
import com.wms.outbound.application.command.SealPackingUnitCommand;
import com.wms.outbound.application.port.in.ConfirmPackingUseCase;
import com.wms.outbound.application.port.in.CreatePackingUnitUseCase;
import com.wms.outbound.application.port.in.SealPackingUnitUseCase;
import com.wms.outbound.application.port.out.PackingPersistencePort;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.PackingUnitResult;
import com.wms.outbound.domain.exception.PackingUnitNotFoundException;
import com.wms.outbound.domain.model.PackingUnit;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Packing endpoints per {@code outbound-service-api.md} §3:
 * <ul>
 *   <li>{@code POST /api/v1/outbound/orders/{id}/packing-units}</li>
 *   <li>{@code PATCH /api/v1/outbound/packing-units/{id}} — seals the unit
 *       (the v2 add-lines variant in §3.2 is omitted in v1; lines are supplied
 *       at creation time)</li>
 *   <li>{@code POST /api/v1/outbound/orders/{id}/packing/confirm}</li>
 * </ul>
 */
@RestController
public class PackingController {

    private final CreatePackingUnitUseCase createPackingUnit;
    private final SealPackingUnitUseCase sealPackingUnit;
    private final ConfirmPackingUseCase confirmPacking;
    private final PackingPersistencePort packingPersistence;

    public PackingController(CreatePackingUnitUseCase createPackingUnit,
                             SealPackingUnitUseCase sealPackingUnit,
                             ConfirmPackingUseCase confirmPacking,
                             PackingPersistencePort packingPersistence) {
        this.createPackingUnit = createPackingUnit;
        this.sealPackingUnit = sealPackingUnit;
        this.confirmPacking = confirmPacking;
        this.packingPersistence = packingPersistence;
    }

    @PostMapping("/api/v1/outbound/orders/{orderId}/packing-units")
    public ResponseEntity<PackingUnitResponse> createUnit(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePackingUnitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CreatePackingUnitCommand command = new CreatePackingUnitCommand(
                orderId,
                request.cartonNo(),
                request.packingType(),
                request.weightGrams(),
                request.lengthMm(),
                request.widthMm(),
                request.heightMm(),
                request.notes(),
                request.lines().stream()
                        .map(l -> new CreatePackingUnitLineCommand(
                                l.orderLineId(), l.skuId(), l.lotId(), l.qty()))
                        .toList(),
                actorId(jwt),
                callerRoles(authentication));
        PackingUnitResult result = createPackingUnit.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PackingUnitResponse.from(result));
    }

    @PatchMapping("/api/v1/outbound/packing-units/{packingUnitId}")
    public ResponseEntity<PackingUnitResponse> sealUnit(
            @PathVariable UUID packingUnitId,
            @Valid @RequestBody SealPackingUnitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        // Resolve orderId via the unit lookup so the use-case can validate.
        PackingUnit unit = packingPersistence.findById(packingUnitId)
                .orElseThrow(() -> new PackingUnitNotFoundException(packingUnitId));
        SealPackingUnitCommand command = new SealPackingUnitCommand(
                unit.getOrderId(),
                packingUnitId,
                request.version(),
                actorId(jwt),
                callerRoles(authentication));
        PackingUnitResult result = sealPackingUnit.seal(command);
        return ResponseEntity.ok(PackingUnitResponse.from(result));
    }

    @PostMapping("/api/v1/outbound/orders/{orderId}/packing/confirm")
    public ResponseEntity<OrderResponse> confirmPacking(
            @PathVariable UUID orderId,
            @Valid @RequestBody ConfirmPackingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        ConfirmPackingCommand command = new ConfirmPackingCommand(
                orderId,
                request.version(),
                actorId(jwt),
                callerRoles(authentication));
        OrderResult result = confirmPacking.confirm(command);
        return ResponseEntity.ok(OrderResponse.from(result));
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
    }
}
