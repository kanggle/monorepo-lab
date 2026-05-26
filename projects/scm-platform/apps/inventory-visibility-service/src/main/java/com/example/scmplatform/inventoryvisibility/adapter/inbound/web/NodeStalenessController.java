package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.StalenessResponse;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/inventory-visibility/staleness — node-by-node staleness status.
 * Acceptance Criteria #13.
 */
@RestController
@RequestMapping("/api/inventory-visibility")
@RequiredArgsConstructor
public class NodeStalenessController {

    private final InventoryVisibilityApplicationService applicationService;

    @GetMapping("/staleness")
    public ResponseEntity<ApiEnvelope<List<StalenessResponse>>> getStaleness(
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = TenantClaimExtractor.extractTenantId(jwt);
        List<StalenessResponse> responses = applicationService.getStaleness(tenantId)
                .stream()
                .map(StalenessResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.of(responses));
    }
}
