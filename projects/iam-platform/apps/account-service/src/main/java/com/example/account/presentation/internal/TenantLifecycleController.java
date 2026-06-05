package com.example.account.presentation.internal;

import com.example.account.application.result.TenantPageResult;
import com.example.account.application.result.TenantResult;
import com.example.account.application.service.TenantProvisionUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * TASK-BE-250: Internal tenant lifecycle CRUD API.
 * Called exclusively by admin-service; protected by InternalApiFilter (X-Internal-Token).
 *
 * <p>URL prefix: {@code /internal/tenants}
 * <p>Authentication: {@code X-Internal-Token} header validated by {@code InternalApiFilter}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants")
public class TenantLifecycleController {

    private final TenantProvisionUseCase tenantProvisionUseCase;

    /**
     * POST /internal/tenants
     * Create a new tenant. tenantId must be unique; returns 409 TENANT_ALREADY_EXISTS if duplicate.
     */
    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantResult result = tenantProvisionUseCase.create(
                request.tenantId(), request.displayName(), request.tenantType());
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(result));
    }

    /**
     * PATCH /internal/tenants/{tenantId}
     * Update displayName and/or status. At least one field required.
     */
    @PatchMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        TenantResult result = tenantProvisionUseCase.update(
                tenantId, request.displayName(), request.status());
        return ResponseEntity.ok(TenantResponse.from(result));
    }

    /**
     * GET /internal/tenants
     * Paginated list with optional status/tenantType filters.
     */
    @GetMapping
    public ResponseEntity<TenantPageResponse> listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TenantPageResult result = tenantProvisionUseCase.list(status, tenantType, page, size);
        return ResponseEntity.ok(TenantPageResponse.from(result));
    }

    /**
     * GET /internal/tenants/{tenantId}
     * Single tenant detail.
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        TenantResult result = tenantProvisionUseCase.get(tenantId);
        return ResponseEntity.ok(TenantResponse.from(result));
    }

    // ---- DTOs ----------------------------------------------------------------

    public record CreateTenantRequest(
            @NotBlank String tenantId,
            @NotBlank @Size(min = 1, max = 100) String displayName,
            @NotBlank String tenantType
    ) {}

    public record UpdateTenantRequest(
            @Size(min = 1, max = 100) String displayName,
            String status
    ) {}

    public record TenantResponse(
            String tenantId,
            String displayName,
            String tenantType,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TenantResponse from(TenantResult result) {
            return new TenantResponse(
                    result.tenantId(),
                    result.displayName(),
                    result.tenantType(),
                    result.status(),
                    result.createdAt(),
                    result.updatedAt()
            );
        }
    }

    public record TenantPageResponse(
            java.util.List<TenantResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static TenantPageResponse from(TenantPageResult result) {
            return new TenantPageResponse(
                    result.items().stream().map(TenantResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }
}
