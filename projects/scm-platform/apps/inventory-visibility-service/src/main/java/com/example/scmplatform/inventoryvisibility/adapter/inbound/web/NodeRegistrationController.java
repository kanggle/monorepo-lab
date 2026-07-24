package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.NodeResponse;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ObserveStockRequest;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ObserveStockResponse;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.RegisterNodeRequest;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ObservedLine;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Operator/onboarding endpoints for the two mutating actions on an otherwise
 * read-only inventory-visibility API (S5):
 * <ul>
 *   <li>POST /api/inventory-visibility/nodes — explicitly register a
 *       THIRD_PARTY_LOGISTICS node (ADR-MONO-054 §D2 / TASK-SCM-BE-046,
 *       201 new / 200 idempotent repeat)</li>
 *   <li>POST /api/inventory-visibility/nodes/{nodeId}/observed-stock — record an
 *       absolute reading of stock held at an already-registered 3PL node
 *       (ADR-MONO-054 §D4 / TASK-SCM-BE-047)</li>
 * </ul>
 * Kept separate from the read-only {@link InventoryVisibilityController} — the
 * mutating surface is deliberately isolated rather than bloating the read
 * controller. Neither endpoint auto-registers a node.
 */
@RestController
@RequestMapping("/api/inventory-visibility")
@RequiredArgsConstructor
public class NodeRegistrationController {

    private final RegisterThirdPartyLogisticsNodeService registrationService;
    private final InventoryVisibilityApplicationService visibilityService;
    private final ClockPort clock;

    @PostMapping("/nodes")
    public ResponseEntity<ApiEnvelope<NodeResponse>> registerNode(
            @RequestBody RegisterNodeRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String tenantId = TenantClaimExtractor.extractTenantId(jwt);
        RegisterThirdPartyLogisticsNodeResult result =
                registrationService.register(tenantId, request.nodeExternalId(), request.name());

        NodeResponse body = NodeResponse.from(result.node());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiEnvelope.of(body));
    }

    /**
     * POST /api/inventory-visibility/nodes/{nodeId}/observed-stock — record an
     * absolute observed-stock reading for an existing THIRD_PARTY_LOGISTICS node
     * (ADR-MONO-054 §D4 / TASK-SCM-BE-047). {@code observedAt} defaults to "now"
     * (via {@link ClockPort}, consistent with the application service's clock use)
     * when omitted. Unknown node → 404 ({@code NodeNotFoundException}); wrong-type
     * or cross-tenant node → 409 ({@code NodeTypeConflictException},
     * {@code NODE_TYPE_CONFLICT} — reused from TASK-SCM-BE-046); blank sku / negative
     * quantity / empty lines → 422 ({@code IllegalArgumentException}, the existing
     * {@code VALIDATION_ERROR} convention).
     */
    @PostMapping("/nodes/{nodeId}/observed-stock")
    public ResponseEntity<ApiEnvelope<ObserveStockResponse>> observeStock(
            @PathVariable String nodeId,
            @RequestBody ObserveStockRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String tenantId = TenantClaimExtractor.extractTenantId(jwt);
        List<ObservedLine> lines = validateAndMapLines(request.lines());
        Instant observedAt = request.observedAt() != null ? request.observedAt() : clock.now();

        visibilityService.applyThirdPartyObservedStock(nodeId, tenantId, observedAt, lines);

        ObserveStockResponse body = new ObserveStockResponse(nodeId, lines.size(), observedAt);
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    private List<ObservedLine> validateAndMapLines(List<ObserveStockRequest.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        return lines.stream()
                .map(line -> {
                    if (line.skuCode() == null || line.skuCode().isBlank()) {
                        throw new IllegalArgumentException("skuCode must not be blank");
                    }
                    if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException(
                                "quantity must not be negative: skuCode=" + line.skuCode());
                    }
                    return new ObservedLine(line.skuCode(), line.quantity());
                })
                .toList();
    }
}
