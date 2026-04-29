package com.wms.inbound.adapter.in.rest;

import com.wms.inbound.adapter.in.rest.dto.ConfirmPutawayLineRequest;
import com.wms.inbound.adapter.in.rest.dto.InstructPutawayRequest;
import com.wms.inbound.adapter.in.rest.dto.PutawayConfirmationResponse;
import com.wms.inbound.adapter.in.rest.dto.PutawayInstructionResponse;
import com.wms.inbound.adapter.in.rest.dto.PutawaySkipResponse;
import com.wms.inbound.adapter.in.rest.dto.SkipPutawayLineRequest;
import com.wms.inbound.application.command.ConfirmPutawayLineCommand;
import com.wms.inbound.application.command.InstructPutawayCommand;
import com.wms.inbound.application.command.SkipPutawayLineCommand;
import com.wms.inbound.application.port.in.ConfirmPutawayLineUseCase;
import com.wms.inbound.application.port.in.GetPutawayInstructionUseCase;
import com.wms.inbound.application.port.in.InstructPutawayUseCase;
import com.wms.inbound.application.port.in.SkipPutawayLineUseCase;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.application.result.PutawaySkipResult;
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

/**
 * REST endpoints for the Putaway phase. Authoritative contract:
 * {@code specs/contracts/http/inbound-service-api.md} §3.
 *
 * <p>Authorization is enforced in the application layer (per BE-028 pattern):
 * commands carry {@code callerRoles} and the use-case service throws
 * {@code AccessDeniedException} on insufficient privilege. The
 * {@code @PreAuthorize} annotations are a defence-in-depth check only.
 */
@RestController
public class PutawayController {

    private final InstructPutawayUseCase instructPutaway;
    private final ConfirmPutawayLineUseCase confirmPutawayLine;
    private final SkipPutawayLineUseCase skipPutawayLine;
    private final GetPutawayInstructionUseCase queryPutaway;

    public PutawayController(InstructPutawayUseCase instructPutaway,
                              ConfirmPutawayLineUseCase confirmPutawayLine,
                              SkipPutawayLineUseCase skipPutawayLine,
                              GetPutawayInstructionUseCase queryPutaway) {
        this.instructPutaway = instructPutaway;
        this.confirmPutawayLine = confirmPutawayLine;
        this.skipPutawayLine = skipPutawayLine;
        this.queryPutaway = queryPutaway;
    }

    @PostMapping("/api/v1/inbound/asns/{asnId}/putaway:instruct")
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<PutawayInstructionResponse> instruct(
            @PathVariable UUID asnId,
            @Valid @RequestBody InstructPutawayRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        InstructPutawayCommand command = new InstructPutawayCommand(
                asnId,
                request.lines().stream()
                        .map(l -> new InstructPutawayCommand.Line(
                                l.asnLineId(), l.destinationLocationId(), l.qtyToPutaway()))
                        .toList(),
                request.version(),
                AsnController.actorId(jwt),
                AsnController.callerRoles(authentication));
        PutawayInstructionResult result = instructPutaway.instruct(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(String.valueOf(result.version()))
                .body(PutawayInstructionResponse.from(result));
    }

    @PostMapping("/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:confirm")
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<PutawayConfirmationResponse> confirmLine(
            @PathVariable UUID instructionId,
            @PathVariable UUID lineId,
            @Valid @RequestBody ConfirmPutawayLineRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        ConfirmPutawayLineCommand command = new ConfirmPutawayLineCommand(
                instructionId, lineId, request.actualLocationId(), request.qtyConfirmed(),
                AsnController.actorId(jwt), AsnController.callerRoles(authentication));
        PutawayConfirmationResult result = confirmPutawayLine.confirm(command);
        return ResponseEntity.ok(PutawayConfirmationResponse.from(result));
    }

    @PostMapping("/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:skip")
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<PutawaySkipResponse> skipLine(
            @PathVariable UUID instructionId,
            @PathVariable UUID lineId,
            @Valid @RequestBody SkipPutawayLineRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        SkipPutawayLineCommand command = new SkipPutawayLineCommand(
                instructionId, lineId, request.reason(),
                AsnController.actorId(jwt), AsnController.callerRoles(authentication));
        PutawaySkipResult result = skipPutawayLine.skip(command);
        return ResponseEntity.ok(PutawaySkipResponse.from(result));
    }

    @GetMapping("/api/v1/inbound/putaway/{instructionId}")
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<PutawayInstructionResponse> getInstruction(@PathVariable UUID instructionId) {
        PutawayInstructionResult result = queryPutaway.findByInstructionId(instructionId);
        return ResponseEntity.ok()
                .eTag(String.valueOf(result.version()))
                .body(PutawayInstructionResponse.from(result));
    }

    @GetMapping("/api/v1/inbound/asns/{asnId}/putaway")
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<PutawayInstructionResponse> getInstructionByAsn(@PathVariable UUID asnId) {
        PutawayInstructionResult result = queryPutaway.findByAsnId(asnId);
        return ResponseEntity.ok(PutawayInstructionResponse.from(result));
    }

    @GetMapping("/api/v1/inbound/putaway/lines/{lineId}/confirmation")
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<PutawayConfirmationResponse> getConfirmation(@PathVariable UUID lineId) {
        PutawayConfirmationResult result = queryPutaway.findConfirmationByLineId(lineId);
        return ResponseEntity.ok(PutawayConfirmationResponse.from(result));
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
    }
}
