package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand;
import com.example.scmplatform.procurement.presentation.dto.ApiEnvelope;
import com.example.scmplatform.procurement.presentation.dto.PurchaseOrderResponse;
import com.example.scmplatform.procurement.presentation.dto.SupplierAckWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Inbound webhook from supplier — supplier ack of a previously-submitted PO.
 *
 * <p>v1: shared-secret check via the {@code X-Supplier-Signature} header.
 * v2: HMAC + timestamp + replay-protection (rules/traits/integration-heavy.md
 * I6 — webhooks must verify signature + timestamp + idempotency).
 */
@RestController
@RequestMapping("/api/procurement/webhooks/supplier-ack")
@RequiredArgsConstructor
public class SupplierAckWebhookController {

    private final PurchaseOrderApplicationService service;

    @Value("${scmplatform.procurement.supplier.webhook-secret:scm-supplier-webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> ack(
            @RequestHeader(value = "X-Supplier-Signature", required = false) String signature,
            @Valid @RequestBody SupplierAckWebhookRequest req) {
        verifySignature(signature);
        PurchaseOrderView view = service.acknowledge(new AcknowledgePurchaseOrderCommand(
                req.tenantId(), req.poId(), req.supplierAckRef()));
        return ResponseEntity.ok(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }

    private void verifySignature(String signature) {
        if (!Objects.equals(signature, webhookSecret)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "WEBHOOK_SIGNATURE_INVALID");
        }
    }
}
