package com.wms.inventory.adapter.in.web.controller;

import com.wms.inventory.adapter.in.web.dto.request.CreateTransferRequest;
import com.wms.inventory.adapter.in.web.dto.response.PageResponse;
import com.wms.inventory.adapter.in.web.dto.response.TransferResponse;
import com.wms.inventory.application.command.TransferStockCommand;
import com.wms.inventory.application.port.in.QueryTransferUseCase;
import com.wms.inventory.application.port.in.TransferStockUseCase;
import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.TransferResult;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.TransferNotFoundException;
import com.wms.inventory.domain.model.TransferReasonCode;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class TransferController {

    private final TransferStockUseCase transferStock;
    private final QueryTransferUseCase queryTransfer;

    public TransferController(TransferStockUseCase transferStock,
                              QueryTransferUseCase queryTransfer) {
        this.transferStock = transferStock;
        this.queryTransfer = queryTransfer;
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasRole('INVENTORY_WRITE') or hasRole('INVENTORY_ADMIN')")
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InventoryValidationException("Idempotency-Key header is required");
        }
        TransferStockCommand command = new TransferStockCommand(
                request.sourceLocationId(), request.targetLocationId(),
                request.skuId(), request.lotId(), request.quantity(),
                request.reasonCode(), request.reasonNote(),
                actorId(jwt), idempotencyKey);
        TransferResult result = transferStock.transfer(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TransferResponse.from(result));
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public TransferResponse getById(@PathVariable UUID id) {
        TransferView view = queryTransfer.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(
                        "Transfer not found: " + id));
        return TransferResponse.fromView(view);
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<TransferResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sourceLocationId,
            @RequestParam(required = false) UUID targetLocationId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) TransferReasonCode reasonCode,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TransferListCriteria criteria = new TransferListCriteria(
                warehouseId, sourceLocationId, targetLocationId, skuId,
                reasonCode, createdAfter, createdBefore, page, size);
        return PageResponse.from(queryTransfer.list(criteria), TransferResponse::fromView);
    }

    private static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
