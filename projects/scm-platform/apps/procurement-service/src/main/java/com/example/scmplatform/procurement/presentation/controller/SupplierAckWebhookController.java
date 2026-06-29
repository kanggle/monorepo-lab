package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand;
import com.example.scmplatform.procurement.presentation.dto.ApiEnvelope;
import com.example.scmplatform.procurement.presentation.dto.PurchaseOrderResponse;
import com.example.scmplatform.procurement.presentation.dto.SupplierAckWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound webhook from supplier — supplier ack of a previously-submitted PO.
 *
 * <p>Signature verification (HMAC-SHA256 over {@code timestamp + "." + rawBody}
 * + timestamp freshness + signature-nonce replay rejection) is performed by
 * {@code WebhookSignatureFilter} (infrastructure/security) before the request
 * reaches this controller — it contains no security logic.
 */
@RestController
@RequestMapping("/api/procurement/webhooks/supplier-ack")
@RequiredArgsConstructor
public class SupplierAckWebhookController {

    private final PurchaseOrderApplicationService service;

    @PostMapping
    public ResponseEntity<ApiEnvelope<PurchaseOrderResponse>> ack(
            @Valid @RequestBody SupplierAckWebhookRequest req) {
        PurchaseOrderView view = service.acknowledge(new AcknowledgePurchaseOrderCommand(
                req.tenantId(), req.poId(), req.supplierAckRef()));
        return ResponseEntity.ok(ApiEnvelope.of(PurchaseOrderResponse.from(view)));
    }
}
