package com.example.shipping.interfaces.rest.controller;

import com.example.shipping.application.command.CarrierWebhookCommand;
import com.example.shipping.application.service.ProcessCarrierWebhookService;
import com.example.shipping.domain.exception.InvalidShippingException;
import com.example.shipping.interfaces.rest.dto.request.CarrierWebhookRequest;
import com.example.shipping.interfaces.rest.security.CarrierWebhookVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound carrier webhook endpoint (TASK-BE-294). The carrier POSTs a tracking delivery
 * event; this verifies the HMAC signature over the <b>raw body</b>, parses it, and applies
 * it idempotently via {@link ProcessCarrierWebhookService}.
 *
 * <p>Authentication is the shared-secret signature (not the gateway {@code X-User-Role}),
 * so this is a separate controller from the user/admin {@code ShippingController}. Every
 * validly-signed delivery returns <b>200</b> (ADVANCED / IGNORED / DUPLICATE all ack the
 * carrier so it stops retrying); a bad signature returns 401 and a malformed body 400.
 */
@Slf4j
@RestController
@RequestMapping("/api/shippings")
@RequiredArgsConstructor
public class CarrierWebhookController {

    private final CarrierWebhookVerifier carrierWebhookVerifier;
    private final ProcessCarrierWebhookService processCarrierWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/carrier-webhook")
    public ResponseEntity<Void> handleCarrierWebhook(
            @RequestHeader(value = "X-Carrier-Signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        carrierWebhookVerifier.verify(rawBody, signature);

        CarrierWebhookRequest request = parse(rawBody);
        CarrierWebhookCommand command = new CarrierWebhookCommand(
                request.deliveryId(), request.shippingId(), request.status());

        ProcessCarrierWebhookService.WebhookOutcome outcome = processCarrierWebhookService.ingest(command);
        log.debug("Carrier webhook {} processed with outcome {}", request.deliveryId(), outcome);
        return ResponseEntity.ok().build();
    }

    private CarrierWebhookRequest parse(String rawBody) {
        CarrierWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, CarrierWebhookRequest.class);
        } catch (Exception e) {
            throw new InvalidShippingException("Malformed carrier webhook body");
        }
        if (request == null || isBlank(request.deliveryId())
                || isBlank(request.shippingId()) || isBlank(request.status())) {
            throw new InvalidShippingException("deliveryId, shippingId and status are required");
        }
        return request;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
