package com.wms.inventory.adapter.in.web.controller;

import com.wms.inventory.adapter.in.web.dto.response.MovementResponse;
import com.wms.inventory.adapter.in.web.dto.response.PageResponse;
import com.wms.inventory.application.port.in.MovementQueryUseCase;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints over the W2 movement ledger. Authoritative reference:
 * {@code specs/contracts/http/inventory-service-api.md} §5.
 *
 * <p>The cross-row endpoint requires {@code occurredAfter} when
 * {@code inventoryId} is absent — the criteria record's compact constructor
 * surfaces a {@code 400 VALIDATION_ERROR} for that case.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class MovementQueryController {

    private final MovementQueryUseCase movementQuery;

    public MovementQueryController(MovementQueryUseCase movementQuery) {
        this.movementQuery = movementQuery;
    }

    @GetMapping("/{inventoryId}/movements")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<MovementResponse> listForInventory(
            @PathVariable UUID inventoryId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) Bucket bucket,
            @RequestParam(required = false) ReasonCode reasonCode,
            @RequestParam(required = false) Instant occurredAfter,
            @RequestParam(required = false) Instant occurredBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MovementListCriteria criteria = new MovementListCriteria(
                inventoryId, null, null, movementType, bucket, reasonCode,
                occurredAfter, occurredBefore, page, size);
        return PageResponse.from(movementQuery.list(criteria), MovementResponse::from);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<MovementResponse> listCrossRow(
            @RequestParam(required = false) UUID inventoryId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) Bucket bucket,
            @RequestParam(required = false) ReasonCode reasonCode,
            @RequestParam(required = false) Instant occurredAfter,
            @RequestParam(required = false) Instant occurredBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MovementListCriteria criteria = new MovementListCriteria(
                inventoryId, locationId, skuId, movementType, bucket, reasonCode,
                occurredAfter, occurredBefore, page, size);
        return PageResponse.from(movementQuery.list(criteria), MovementResponse::from);
    }
}
