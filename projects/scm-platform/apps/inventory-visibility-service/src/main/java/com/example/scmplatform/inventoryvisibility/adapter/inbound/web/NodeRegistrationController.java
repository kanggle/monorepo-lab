package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.NodeResponse;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.RegisterNodeRequest;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator endpoint to explicitly register a THIRD_PARTY_LOGISTICS inventory node
 * (ADR-MONO-054 §D2 / TASK-SCM-BE-046). Kept separate from the read-only
 * {@link InventoryVisibilityController} — this is the one mutating REST surface
 * on an otherwise read-only API (S5), so it is deliberately isolated rather than
 * bloating the read controller.
 *
 * <p>Endpoint per {@code specs/contracts/http/inventory-visibility-api.md}:
 * <ul>
 *   <li>POST /api/inventory-visibility/nodes — register a 3PL node (201 new / 200 idempotent repeat)</li>
 * </ul>
 *
 * <p>Registers only THIRD_PARTY_LOGISTICS nodes — wms warehouse nodes stay
 * auto-registered from {@code wms.inventory.*} events (unchanged).
 */
@RestController
@RequestMapping("/api/inventory-visibility")
@RequiredArgsConstructor
public class NodeRegistrationController {

    private final RegisterThirdPartyLogisticsNodeService registrationService;

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
}
