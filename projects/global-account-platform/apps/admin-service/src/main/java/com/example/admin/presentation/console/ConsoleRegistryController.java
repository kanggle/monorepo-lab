package com.example.admin.presentation.console;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.console.ConsoleProduct;
import com.example.admin.application.console.ConsoleRegistry;
import com.example.admin.application.console.ConsoleRegistryUseCase;
import com.example.admin.application.console.ProductOperatorContext;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-296: platform-console product/tenant registry read surface.
 *
 * <p>{@code GET /api/admin/console/registry} — operator-authenticated (the
 * operator JWT is verified by {@code OperatorAuthenticationFilter} before this
 * controller is reached), read-only, tenant-aware. Returns the data-driven
 * catalog the console renders.
 *
 * <p>No {@code @RequiresPermission} and no {@code X-Operator-Reason}: any valid
 * operator JWT may call this (analogous to {@code GET /api/admin/me}). The
 * response is scoped by the operator's own tenant — a low-privilege operator
 * simply sees fewer selectable tenants and never another tenant's ids
 * ({@code rules/traits/multi-tenant.md} M3/M6).
 *
 * <p>Authoritative contract:
 * {@code specs/contracts/http/console-registry-api.md}.
 */
@RestController
@RequestMapping("/api/admin/console")
@RequiredArgsConstructor
public class ConsoleRegistryController {

    private final ConsoleRegistryUseCase consoleRegistryUseCase;

    @GetMapping("/registry")
    public ResponseEntity<RegistryResponse> registry() {
        OperatorContext operator = OperatorContextHolder.require();
        ConsoleRegistry registry = consoleRegistryUseCase.execute(operator);
        return ResponseEntity.ok(RegistryResponse.from(registry));
    }

    // ---- Response DTOs ------------------------------------------------------

    /**
     * Wire shape — mirrors console-integration-contract § 2.2 exactly:
     * {@code productKey}, {@code displayName}, {@code available},
     * {@code tenants}, {@code baseRoute}, {@code operatorContext}
     * (TASK-BE-304, optional, omitted when {@code null}).
     */
    public record ProductResponse(
            String productKey,
            String displayName,
            boolean available,
            List<String> tenants,
            String baseRoute,
            // TASK-BE-304: per-operator per-product profile attributes. Field-level
            // @JsonInclude.NON_NULL — when the application returns null, the
            // serializer omits the field entirely (the contract requires
            // omission, NEVER literal "operatorContext":null — see
            // console-registry-api.md § Per-operator profile attributes
            // "Why omitted, not `null`"). Scope-narrow to this field; if a future
            // field needs the same discipline, annotate it individually.
            @JsonInclude(JsonInclude.Include.NON_NULL)
            ProductOperatorContextResponse operatorContext
    ) {
        static ProductResponse from(ConsoleProduct p) {
            return new ProductResponse(
                    p.productKey(), p.displayName(), p.available(),
                    p.tenants(), p.baseRoute(),
                    ProductOperatorContextResponse.from(p.operatorContext()));
        }
    }

    /**
     * TASK-BE-304: wire DTO for {@link ProductOperatorContext}. Sibling to
     * {@link ProductResponse}. v1 carries only {@code defaultAccountId};
     * future per-operator per-product attributes nest here.
     */
    public record ProductOperatorContextResponse(String defaultAccountId) {
        static ProductOperatorContextResponse from(ProductOperatorContext ctx) {
            return ctx == null ? null : new ProductOperatorContextResponse(ctx.defaultAccountId());
        }
    }

    public record RegistryResponse(List<ProductResponse> products) {
        static RegistryResponse from(ConsoleRegistry r) {
            return new RegistryResponse(
                    r.products().stream().map(ProductResponse::from).toList());
        }
    }
}
