package com.wms.inventory.adapter.in.web.controller;

import com.wms.inventory.adapter.in.web.dto.response.InventoryResponse;
import com.wms.inventory.adapter.in.web.dto.response.PageResponse;
import com.wms.inventory.application.port.in.QueryInventoryUseCase;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints over the inventory aggregate. Authoritative reference:
 * {@code specs/contracts/http/inventory-service-api.md} §1.
 *
 * <p>{@code GET /by-key} returns 404 ({@code INVENTORY_NOT_FOUND}) when no
 * row exists — callers treat that as zero stock per the contract note.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryQueryController {

    private final QueryInventoryUseCase queryInventory;

    public InventoryQueryController(QueryInventoryUseCase queryInventory) {
        this.queryInventory = queryInventory;
    }

    @GetMapping
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<InventoryResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) Boolean hasStock,
            @RequestParam(required = false) Integer minAvailable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        InventoryListCriteria criteria = new InventoryListCriteria(
                warehouseId, locationId, skuId, lotId, hasStock, minAvailable,
                page, size, sort);
        return PageResponse.from(queryInventory.list(criteria), InventoryResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public ResponseEntity<InventoryResponse> getById(@PathVariable UUID id) {
        InventoryView view = queryInventory.findById(id)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found: " + id));
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"v" + view.version() + "\"")
                .body(InventoryResponse.from(view));
    }

    @GetMapping("/by-key")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public ResponseEntity<InventoryResponse> getByKey(
            @RequestParam UUID locationId,
            @RequestParam UUID skuId,
            @RequestParam(required = false) UUID lotId) {
        InventoryView view = queryInventory.findByKey(locationId, skuId, lotId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for (location, sku, lot)"));
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"v" + view.version() + "\"")
                .body(InventoryResponse.from(view));
    }
}
