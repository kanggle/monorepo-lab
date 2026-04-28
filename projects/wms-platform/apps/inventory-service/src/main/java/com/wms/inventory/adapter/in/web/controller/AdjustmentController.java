package com.wms.inventory.adapter.in.web.controller;

import com.wms.inventory.adapter.in.web.dto.request.CreateAdjustmentRequest;
import com.wms.inventory.adapter.in.web.dto.request.MarkDamagedRequest;
import com.wms.inventory.adapter.in.web.dto.request.WriteOffDamagedRequest;
import com.wms.inventory.adapter.in.web.dto.response.AdjustmentResponse;
import com.wms.inventory.adapter.in.web.dto.response.PageResponse;
import com.wms.inventory.application.command.AdjustStockCommand;
import com.wms.inventory.application.command.AdjustStockCommand.AdjustOperation;
import com.wms.inventory.application.port.in.AdjustStockUseCase;
import com.wms.inventory.application.port.in.QueryAdjustmentUseCase;
import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.result.AdjustmentResult;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.domain.exception.AdjustmentNotFoundException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

/**
 * Adjustment / mark-damaged / write-off-damaged + read endpoints.
 *
 * <p>Authoritative reference:
 * {@code specs/contracts/http/inventory-service-api.md} §2.
 *
 * <p>Method-level {@code @PreAuthorize} mirrors the contract route-level
 * authorization. Bucket-conditional rules (e.g. {@code RESERVED}-bucket
 * adjustments require {@code INVENTORY_ADMIN}) are enforced inside the
 * application service per {@code architecture.md} §Security; the controller
 * only forwards caller authorities into the command.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class AdjustmentController {

    private final AdjustStockUseCase adjustStock;
    private final QueryAdjustmentUseCase queryAdjustment;

    public AdjustmentController(AdjustStockUseCase adjustStock,
                                QueryAdjustmentUseCase queryAdjustment) {
        this.adjustStock = adjustStock;
        this.queryAdjustment = queryAdjustment;
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasRole('INVENTORY_WRITE') or hasRole('INVENTORY_ADMIN')")
    public ResponseEntity<AdjustmentResponse> createAdjustment(
            @Valid @RequestBody CreateAdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InventoryValidationException("Idempotency-Key header is required");
        }
        if (request.delta() == 0) {
            throw new InventoryValidationException("delta must be non-zero");
        }
        if (request.reasonNote() == null || request.reasonNote().trim().length() < 3) {
            throw new com.wms.inventory.domain.exception.AdjustmentReasonRequiredException(
                    "reasonNote is required and must be at least 3 non-blank characters");
        }
        AdjustStockCommand command = new AdjustStockCommand(
                AdjustOperation.REGULAR,
                request.inventoryId(), request.bucket(), request.delta(),
                request.reasonCode(), request.reasonNote(),
                actorId(jwt), idempotencyKey,
                callerRoles(authentication));
        AdjustmentResult result = adjustStock.adjust(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdjustmentResponse.from(result));
    }

    @PostMapping("/{inventoryId}/mark-damaged")
    @PreAuthorize("hasRole('INVENTORY_WRITE') or hasRole('INVENTORY_ADMIN')")
    public ResponseEntity<AdjustmentResponse> markDamaged(
            @PathVariable UUID inventoryId,
            @Valid @RequestBody MarkDamagedRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InventoryValidationException("Idempotency-Key header is required");
        }
        if (request.reasonNote() == null || request.reasonNote().trim().length() < 3) {
            throw new com.wms.inventory.domain.exception.AdjustmentReasonRequiredException(
                    "reasonNote is required and must be at least 3 non-blank characters");
        }
        AdjustStockCommand command = new AdjustStockCommand(
                AdjustOperation.MARK_DAMAGED,
                inventoryId, Bucket.AVAILABLE, request.quantity(),
                ReasonCode.ADJUSTMENT_DAMAGE, request.reasonNote(),
                actorId(jwt), idempotencyKey,
                callerRoles(authentication));
        AdjustmentResult result = adjustStock.adjust(command);
        return ResponseEntity.ok(AdjustmentResponse.from(result));
    }

    @PostMapping("/{inventoryId}/write-off-damaged")
    @PreAuthorize("hasRole('INVENTORY_ADMIN')")
    public ResponseEntity<AdjustmentResponse> writeOffDamaged(
            @PathVariable UUID inventoryId,
            @Valid @RequestBody WriteOffDamagedRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InventoryValidationException("Idempotency-Key header is required");
        }
        if (request.reasonNote() == null || request.reasonNote().trim().length() < 3) {
            throw new com.wms.inventory.domain.exception.AdjustmentReasonRequiredException(
                    "reasonNote is required and must be at least 3 non-blank characters");
        }
        AdjustStockCommand command = new AdjustStockCommand(
                AdjustOperation.WRITE_OFF_DAMAGED,
                inventoryId, Bucket.DAMAGED, request.quantity(),
                ReasonCode.DAMAGE_WRITE_OFF, request.reasonNote(),
                actorId(jwt), idempotencyKey,
                callerRoles(authentication));
        AdjustmentResult result = adjustStock.adjust(command);
        return ResponseEntity.ok(AdjustmentResponse.from(result));
    }

    @GetMapping("/adjustments/{id}")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public AdjustmentResponse getAdjustment(@PathVariable UUID id) {
        AdjustmentView view = queryAdjustment.findById(id)
                .orElseThrow(() -> new AdjustmentNotFoundException(
                        "Adjustment not found: " + id));
        return AdjustmentResponse.fromView(view);
    }

    @GetMapping("/{inventoryId}/adjustments")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<AdjustmentResponse> listForInventory(
            @PathVariable UUID inventoryId,
            @RequestParam(required = false) ReasonCode reasonCode,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdjustmentListCriteria criteria = new AdjustmentListCriteria(
                inventoryId, reasonCode, createdAfter, createdBefore, page, size);
        return PageResponse.from(queryAdjustment.list(criteria), AdjustmentResponse::fromView);
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<AdjustmentResponse> list(
            @RequestParam(required = false) UUID inventoryId,
            @RequestParam(required = false) ReasonCode reasonCode,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (inventoryId == null && createdAfter == null) {
            throw new InventoryValidationException(
                    "createdAfter is required when inventoryId is not supplied");
        }
        AdjustmentListCriteria criteria = new AdjustmentListCriteria(
                inventoryId, reasonCode, createdAfter, createdBefore, page, size);
        return PageResponse.from(queryAdjustment.list(criteria), AdjustmentResponse::fromView);
    }

    private static Set<String> callerRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
