package com.example.scmplatform.logistics.adapter.inbound.web.controller;

import com.example.scmplatform.logistics.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.logistics.adapter.inbound.web.dto.DispatchResponse;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operator REST surface for carrier dispatch (rest-api facet, ADR-053 §D2/§D8).
 * Base path {@code /api/logistics/dispatches} (external {@code /api/v1/logistics/**} via gateway).
 *
 * <ul>
 *   <li>{@code GET /{id}} — inspect a dispatch by dispatch id.</li>
 *   <li>{@code GET /by-shipment/{shipmentId}} — inspect a dispatch by the shipment it dispatches
 *       ({@code dispatch.shipment_id} is unique). The relocation entry point for the wms
 *       {@code :retry-tms-notify} console action, which holds a shipment id, not a dispatch id
 *       (ADR-053 §D8).</li>
 *   <li>{@code POST /{id}:retry} — re-drive a failed dispatch. Naturally idempotent: an
 *       already-{@code DISPATCHED} shipment returns the cached ack with <b>no vendor call</b>
 *       (the {@code dispatch_request_dedupe} short-circuit).</li>
 * </ul>
 *
 * <p><b>No create-dispatch endpoint</b> — a dispatch row is created by the seam consumer
 * (BE-044); the only inbound in this slice is inspect + {@code :retry} (architecture.md § Edge
 * Cases). Tests seed dispatch rows directly.
 */
@RestController
@RequestMapping("/api/logistics/dispatches")
public class DispatchController {

    private final DispatchPersistencePort persistencePort;
    private final RetryDispatchUseCase retryDispatchUseCase;

    public DispatchController(DispatchPersistencePort persistencePort,
                             RetryDispatchUseCase retryDispatchUseCase) {
        this.persistencePort = persistencePort;
        this.retryDispatchUseCase = retryDispatchUseCase;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<DispatchResponse>> getDispatch(@PathVariable UUID id) {
        Dispatch dispatch = persistencePort.findById(id)
                .orElseThrow(() -> new DispatchNotFoundException(id));
        return ResponseEntity.ok(ApiEnvelope.of(DispatchResponse.from(dispatch)));
    }

    @GetMapping("/by-shipment/{shipmentId}")
    public ResponseEntity<ApiEnvelope<DispatchResponse>> getDispatchByShipment(
            @PathVariable UUID shipmentId) {
        Dispatch dispatch = persistencePort.findByShipmentId(shipmentId)
                .orElseThrow(() -> DispatchNotFoundException.forShipment(shipmentId));
        return ResponseEntity.ok(ApiEnvelope.of(DispatchResponse.from(dispatch)));
    }

    @PostMapping("/{id}:retry")
    public ResponseEntity<ApiEnvelope<DispatchResponse>> retryDispatch(@PathVariable UUID id) {
        Dispatch dispatch = retryDispatchUseCase.retry(id);
        return ResponseEntity.ok(ApiEnvelope.of(DispatchResponse.from(dispatch)));
    }
}
