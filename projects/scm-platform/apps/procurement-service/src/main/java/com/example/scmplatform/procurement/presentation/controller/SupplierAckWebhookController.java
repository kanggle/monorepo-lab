package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand;
import com.example.scmplatform.procurement.infrastructure.security.WebhookSignatureVerifier;
import com.example.scmplatform.procurement.presentation.dto.ApiEnvelope;
import com.example.scmplatform.procurement.presentation.dto.PurchaseOrderResponse;
import com.example.scmplatform.procurement.presentation.dto.SupplierAckWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound webhook from supplier — supplier ack of a previously-submitted PO.
 *
 * <p>v1: shared-secret check via the {@code X-Supplier-Signature} header.
 * v2: HMAC + timestamp + replay-protection (rules/traits/integration-heavy.md
 * I6 — webhooks must verify signature + timestamp + idempotency).
 *
 * <p>Signature verification is delegated to {@link WebhookSignatureVerifier}
 * (infrastructure/security) — this controller contains no security logic.
 */
@RestController
@RequestMapping("/api/procurement/webhooks/supplier-ack")
@RequiredArgsConstructor
public class SupplierAckWebhookController {

    private final PurchaseOrderApplicationService service;
    private final WebhookSignatureVerifier signatureVerifier;

    @PostMapping
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> ack(
            @RequestHeader(value = "X-Supplier-Signature", required = false) String signature,
            @Valid @RequestBody SupplierAckWebhookRequest req) {
        signatureVerifier.verify(signature);
        PurchaseOrderView view = service.acknowledge(new AcknowledgePurchaseOrderCommand(
                req.tenantId(), req.poId(), req.supplierAckRef()));
        return ResponseEntity.ok(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }
}
