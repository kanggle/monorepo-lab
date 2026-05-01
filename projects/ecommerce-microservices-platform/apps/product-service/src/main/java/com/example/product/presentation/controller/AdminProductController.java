package com.example.product.presentation.controller;

import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.presentation.dto.AddVariantRequest;
import com.example.product.presentation.dto.AdjustStockRequest;
import com.example.product.presentation.dto.AdjustStockResponse;
import com.example.product.presentation.dto.RegisterProductRequest;
import com.example.product.presentation.dto.RegisterProductResponse;
import com.example.product.presentation.dto.UpdateProductRequest;
import com.example.product.presentation.dto.UpdateVariantRequest;
import com.example.product.application.dto.VariantDetail;
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final RegisterProductService registerProductService;
    private final UpdateProductService updateProductService;
    private final DeleteProductService deleteProductService;
    private final AdjustStockService adjustStockService;
    private final VariantManagementService variantManagementService;

    @PostMapping
    public ResponseEntity<RegisterProductResponse> register(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody RegisterProductRequest request) {
        validateAdminRole(userRole);
        UUID id = registerProductService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterProductResponse.from(id));
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<RegisterProductResponse> update(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        validateAdminRole(userRole);
        UUID id = updateProductService.update(request.toCommand(productId));
        return ResponseEntity.ok(RegisterProductResponse.from(id));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId) {
        validateAdminRole(userRole);
        deleteProductService.delete(productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<VariantDetail> addVariant(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @Valid @RequestBody AddVariantRequest request) {
        validateAdminRole(userRole);
        VariantDetail variant = variantManagementService.addVariant(
                productId, request.optionName(), request.stock(), request.additionalPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(variant);
    }

    @PatchMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<VariantDetail> updateVariant(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        validateAdminRole(userRole);
        VariantDetail variant = variantManagementService.updateVariant(
                productId, variantId, request.optionName(), request.additionalPrice());
        return ResponseEntity.ok(variant);
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        validateAdminRole(userRole);
        variantManagementService.removeVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{productId}/stock")
    public ResponseEntity<AdjustStockResponse> adjustStock(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustStockRequest request) {
        validateAdminRole(userRole);
        AdjustStockResult result = adjustStockService.adjust(request.toCommand(productId));
        return ResponseEntity.ok(AdjustStockResponse.from(result));
    }

    private void validateAdminRole(String userRole) {
        if (!ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException();
        }
    }
}
