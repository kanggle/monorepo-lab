package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.AsnView;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.ReceiveAsnCommand;
import com.example.scmplatform.procurement.infrastructure.security.WebhookSignatureVerifier;
import com.example.scmplatform.procurement.presentation.dto.ApiEnvelope;
import com.example.scmplatform.procurement.presentation.dto.AsnResponse;
import com.example.scmplatform.procurement.presentation.dto.AsnWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound webhook from supplier — ASN (Advance Shipment Notice) delivery.
 *
 * <p>Idempotency (S2 + I6): the same {@code (tenantId, supplierAsnRef)}
 * tuple is unique in the DB; a duplicate webhook delivery returns the
 * previously-stored ASN.
 *
 * <p>Signature verification is delegated to {@link WebhookSignatureVerifier}
 * (infrastructure/security) — this controller contains no security logic.
 */
@RestController
@RequestMapping("/api/procurement/webhooks/asn")
@RequiredArgsConstructor
public class AsnWebhookController {

    private final PurchaseOrderApplicationService service;
    private final WebhookSignatureVerifier signatureVerifier;

    @PostMapping
    public ResponseEntity<ApiEnvelope<AsnResponse>> receive(
            @RequestHeader(value = "X-Supplier-Signature", required = false) String signature,
            @Valid @RequestBody AsnWebhookRequest req) {
        signatureVerifier.verify(signature);
        ReceiveAsnCommand cmd = new ReceiveAsnCommand(
                req.tenantId(),
                req.poId(),
                req.supplierAsnRef(),
                req.expectedArrivalAt(),
                req.lines().stream()
                        .map(l -> new ReceiveAsnCommand.AsnLine(l.poLineId(), l.quantityShipped()))
                        .toList()
        );
        AsnView view = service.receiveAsn(cmd);
        return ResponseEntity.ok(ApiEnvelope.of(AsnResponse.from(view)));
    }
}
