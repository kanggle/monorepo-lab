package com.example.product.presentation.controller;

import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.dto.ProductListResult;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.model.ProductStatus;
import com.example.product.presentation.dto.AddVariantRequest;
import com.example.product.presentation.dto.AdjustStockRequest;
import com.example.product.presentation.dto.AdjustStockResponse;
import com.example.product.presentation.dto.ProductListResponse;
import com.example.product.presentation.dto.RegisterProductRequest;
import com.example.product.presentation.dto.RegisterProductResponse;
import com.example.product.presentation.dto.UpdateProductRequest;
import com.example.product.presentation.dto.UpdateVariantRequest;
import com.example.product.application.dto.VariantDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    /** Page-size cap mirrored from the public {@link ProductController}. */
    private static final int MAX_PAGE_SIZE = 100;

    private final RegisterProductService registerProductService;
    private final UpdateProductService updateProductService;
    private final DeleteProductService deleteProductService;
    private final AdjustStockService adjustStockService;
    private final VariantManagementService variantManagementService;
    private final QueryProductService queryProductService;

    /**
     * Operator-plane tenant-scoped product list snapshot — the platform-console
     * Operator Overview composition leg (ADR-MONO-030 Step 4 facet a-후속-2,
     * TASK-MONO-243).
     *
     * <p>Query path is byte-identical to the public {@link ProductController#list}
     * — same {@link QueryProductService#findAll} read, same {@code categoryId} /
     * {@code status} filters, same {@link #MAX_PAGE_SIZE} page-size cap, same
     * {@link ProductListResponse} envelope ({@code content[]}, {@code page},
     * {@code size}, {@code totalElements}). The Operator Overview leg calls it
     * with {@code ?page=0&size=1} (no status filter), so {@code totalElements}
     * surfaces the tenant's total catalog size (honest metric — {@link ProductStatus}
     * is {@code ON_SALE|SOLD_OUT|HIDDEN}, with no boolean "active", so a status
     * count would be ambiguous during catalog setup).
     *
     * <p><b>Authorization — deliberately does NOT apply any ecommerce-local RBAC.</b>
     * This read MUST NOT require an ecommerce-local {@code ADMIN} role. Rationale
     * (consistent with the erp/finance Operator Overview legs, which are gated
     * purely by federation entitlement-trust):
     * <ol>
     *   <li>The caller is a platform-console <b>OPERATOR</b> presenting an IAM
     *       OIDC token (no ecommerce-local {@code ADMIN} role claim) — requiring
     *       {@code ADMIN} here would make the overview card permanently
     *       {@code forbidden}.</li>
     *   <li>Authorization is enforced at the ecommerce <b>gateway</b>:
     *       {@code AccountTypeEnforcementFilter} requires
     *       {@code account_type=OPERATOR} for {@code /api/admin/**}, and
     *       {@code TenantClaimValidator} requires a non-blank {@code tenant_id};
     *       the gateway injects the trusted {@code X-Tenant-Id} and strips client
     *       headers (header-trust service — product-service is not a JWT resource
     *       server).</li>
     *   <li>Tenant isolation is the repository {@code WHERE tenant_id} chokepoint
     *       (Step 2 / M6) — this read is tenant-scoped automatically via the
     *       existing {@code TenantContext} / {@code TenantContextFilter}; no new
     *       scoping code is introduced.</li>
     * </ol>
     * This is a read-only federated snapshot, so the write-plane ecommerce-local
     * RBAC is a different concern and intentionally not applied.
     */
    @GetMapping
    public ProductListResponse list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        ProductListResult result = queryProductService.findAll(categoryId, status, page, cappedSize);
        return ProductListResponse.from(result);
    }

    /**
     * Operator-plane product registration (ADR-MONO-031 Phase 1a, TASK-BE-366).
     *
     * <p><b>Authorization is enforced at the ecommerce gateway, not here</b>
     * (write-plane mirror of {@link #list}'s rationale, extended from the
     * read leg established in TASK-MONO-243):
     * {@code AccountTypeEnforcementFilter} requires {@code account_type=OPERATOR}
     * for {@code /api/admin/**}, {@code TenantClaimValidator} requires a non-blank
     * {@code tenant_id}, and the repository {@code WHERE tenant_id} chokepoint
     * (Step 2 / M6) enforces tenant isolation. The platform-console OPERATOR
     * presents an IAM OIDC token with no ecommerce-local {@code ADMIN} role claim,
     * so write-plane ecommerce-local RBAC is intentionally not applied.
     */
    @PostMapping
    public ResponseEntity<RegisterProductResponse> register(
            @Valid @RequestBody RegisterProductRequest request) {
        UUID id = registerProductService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterProductResponse.from(id));
    }

    /**
     * Operator-plane product update — authorization at the gateway
     * (OPERATOR + {@code tenant_id} + {@code WHERE tenant_id}); write-plane
     * ecommerce-local RBAC intentionally not applied. See {@link #register}.
     */
    @PatchMapping("/{productId}")
    public ResponseEntity<RegisterProductResponse> update(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        UUID id = updateProductService.update(request.toCommand(productId));
        return ResponseEntity.ok(RegisterProductResponse.from(id));
    }

    /**
     * Operator-plane product deletion — authorization at the gateway
     * (OPERATOR + {@code tenant_id} + {@code WHERE tenant_id}); write-plane
     * ecommerce-local RBAC intentionally not applied. See {@link #register}.
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID productId) {
        deleteProductService.delete(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Operator-plane variant addition — authorization at the gateway
     * (OPERATOR + {@code tenant_id} + {@code WHERE tenant_id}); write-plane
     * ecommerce-local RBAC intentionally not applied. See {@link #register}.
     */
    @PostMapping("/{productId}/variants")
    public ResponseEntity<VariantDetail> addVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody AddVariantRequest request) {
        VariantDetail variant = variantManagementService.addVariant(
                productId, request.optionName(), request.stock(), request.additionalPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(variant);
    }

    /**
     * Operator-plane variant update — authorization at the gateway
     * (OPERATOR + {@code tenant_id} + {@code WHERE tenant_id}); write-plane
     * ecommerce-local RBAC intentionally not applied. See {@link #register}.
     */
    @PatchMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<VariantDetail> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        VariantDetail variant = variantManagementService.updateVariant(
                productId, variantId, request.optionName(), request.additionalPrice());
        return ResponseEntity.ok(variant);
    }

    /**
     * Operator-plane variant deletion — authorization at the gateway
     * (OPERATOR + {@code tenant_id} + {@code WHERE tenant_id}); write-plane
     * ecommerce-local RBAC intentionally not applied. See {@link #register}.
     */
    @DeleteMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        variantManagementService.removeVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Operator-plane stock adjustment — authorization at the gateway
     * (OPERATOR + {@code tenant_id} + {@code WHERE tenant_id}); write-plane
     * ecommerce-local RBAC intentionally not applied. See {@link #register}.
     */
    @PatchMapping("/{productId}/stock")
    public ResponseEntity<AdjustStockResponse> adjustStock(
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustStockRequest request) {
        AdjustStockResult result = adjustStockService.adjust(request.toCommand(productId));
        return ResponseEntity.ok(AdjustStockResponse.from(result));
    }
}
