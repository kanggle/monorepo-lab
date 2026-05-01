package com.wms.inbound.adapter.in.rest;

import com.wms.inbound.adapter.in.rest.dto.AcknowledgeDiscrepancyRequest;
import com.wms.inbound.adapter.in.rest.dto.InspectionResponse;
import com.wms.inbound.adapter.in.rest.dto.RecordInspectionRequest;
import com.wms.inbound.application.command.AcknowledgeDiscrepancyCommand;
import com.wms.inbound.application.command.RecordInspectionCommand;
import com.wms.inbound.application.command.StartInspectionCommand;
import com.wms.inbound.application.port.in.AcknowledgeDiscrepancyUseCase;
import com.wms.inbound.application.port.in.QueryInspectionUseCase;
import com.wms.inbound.application.port.in.RecordInspectionUseCase;
import com.wms.inbound.application.port.in.StartInspectionUseCase;
import com.wms.inbound.application.result.InspectionResult;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionController {

    private final StartInspectionUseCase startInspection;
    private final RecordInspectionUseCase recordInspection;
    private final AcknowledgeDiscrepancyUseCase acknowledgeDiscrepancy;
    private final QueryInspectionUseCase queryInspection;

    public InspectionController(StartInspectionUseCase startInspection,
                                 RecordInspectionUseCase recordInspection,
                                 AcknowledgeDiscrepancyUseCase acknowledgeDiscrepancy,
                                 QueryInspectionUseCase queryInspection) {
        this.startInspection = startInspection;
        this.recordInspection = recordInspection;
        this.acknowledgeDiscrepancy = acknowledgeDiscrepancy;
        this.queryInspection = queryInspection;
    }

    @PostMapping("/api/v1/inbound/asns/{asnId}/inspection:start")
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<Void> startInspection(
            @PathVariable UUID asnId,
            @AuthenticationPrincipal Jwt jwt) {
        StartInspectionCommand command = new StartInspectionCommand(asnId, AsnController.actorId(jwt));
        startInspection.start(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/inbound/asns/{asnId}/inspection")
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<InspectionResponse> recordInspection(
            @PathVariable UUID asnId,
            @Valid @RequestBody RecordInspectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        RecordInspectionCommand command = new RecordInspectionCommand(
                asnId, request.notes(),
                request.lines().stream()
                        .map(l -> new RecordInspectionCommand.Line(
                                l.asnLineId(), l.lotId(), l.lotNo(),
                                l.qtyPassed(), l.qtyDamaged(), l.qtyShort()))
                        .toList(),
                AsnController.actorId(jwt));
        InspectionResult result = recordInspection.record(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(String.valueOf(result.version()))
                .body(InspectionResponse.from(result));
    }

    @PostMapping("/api/v1/inbound/inspections/{id}/discrepancies/{discrepancyId}:acknowledge")
    @PreAuthorize("hasRole('INBOUND_ADMIN')")
    public ResponseEntity<InspectionResponse> acknowledgeDiscrepancy(
            @PathVariable UUID id,
            @PathVariable UUID discrepancyId,
            @RequestBody(required = false) AcknowledgeDiscrepancyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        AcknowledgeDiscrepancyCommand command = new AcknowledgeDiscrepancyCommand(
                id, discrepancyId,
                request != null ? request.notes() : null,
                AsnController.actorId(jwt),
                AsnController.callerRoles(authentication));
        InspectionResult result = acknowledgeDiscrepancy.acknowledge(command);
        return ResponseEntity.ok()
                .eTag(String.valueOf(result.version()))
                .body(InspectionResponse.from(result));
    }

    @GetMapping("/api/v1/inbound/inspections/{id}")
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<InspectionResponse> getInspection(@PathVariable UUID id) {
        InspectionResult result = queryInspection.findById(id);
        return ResponseEntity.ok()
                .eTag(String.valueOf(result.version()))
                .body(InspectionResponse.from(result));
    }

    @GetMapping("/api/v1/inbound/asns/{asnId}/inspection")
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<InspectionResponse> getInspectionByAsn(@PathVariable UUID asnId) {
        InspectionResult result = queryInspection.findByAsnId(asnId);
        return ResponseEntity.ok(InspectionResponse.from(result));
    }
}
