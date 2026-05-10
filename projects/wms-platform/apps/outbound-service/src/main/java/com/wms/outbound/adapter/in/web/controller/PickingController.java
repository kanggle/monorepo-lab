package com.wms.outbound.adapter.in.web.controller;

import com.wms.outbound.adapter.in.web.dto.request.ConfirmPickingRequest;
import com.wms.outbound.adapter.in.web.dto.response.PickingConfirmationResponse;
import com.wms.outbound.adapter.in.web.util.RequestContext;
import com.wms.outbound.application.command.ConfirmPickingCommand;
import com.wms.outbound.application.command.ConfirmPickingLineCommand;
import com.wms.outbound.application.port.in.ConfirmPickingUseCase;
import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.result.PickingConfirmationResult;
import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.domain.exception.PickingRequestNotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Picking-confirmation endpoint.
 *
 * <p>Endpoints per {@code outbound-service-api.md} §2.3:
 * <ul>
 *   <li>{@code POST /api/v1/outbound/picking-requests/{id}/confirmations}</li>
 * </ul>
 *
 * <p>The controller depends only on application-layer in-ports
 * ({@link ConfirmPickingUseCase} + {@link QueryPickingRequestUseCase}); the
 * persistence out-port ({@code PickingPersistencePort}) was removed in
 * TASK-BE-040 (AC-04) so the adapter layer cannot bypass the application
 * service for read access.
 */
@RestController
@RequestMapping("/api/v1/outbound/picking-requests")
public class PickingController {

    private final ConfirmPickingUseCase confirmPicking;
    private final QueryPickingRequestUseCase queryPickingRequest;

    public PickingController(ConfirmPickingUseCase confirmPicking,
                             QueryPickingRequestUseCase queryPickingRequest) {
        this.confirmPicking = confirmPicking;
        this.queryPickingRequest = queryPickingRequest;
    }

    @PostMapping("/{id}/confirmations")
    public ResponseEntity<PickingConfirmationResponse> confirmPicking(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmPickingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        RequestContext.requireIdempotencyKey(idempotencyKey);

        // Resolve orderId via the picking-request id; the use-case operates by orderId.
        PickingRequestResult pickingRequest = queryPickingRequest.findById(id)
                .orElseThrow(() -> new PickingRequestNotFoundException(id));

        ConfirmPickingCommand command = new ConfirmPickingCommand(
                pickingRequest.orderId(),
                request.notes(),
                request.lines().stream()
                        .map(l -> new ConfirmPickingLineCommand(
                                l.orderLineId(), l.skuId(), l.lotId(),
                                l.actualLocationId(), l.qtyConfirmed()))
                        .toList(),
                RequestContext.actorId(jwt),
                RequestContext.callerRoles(authentication));
        PickingConfirmationResult result = confirmPicking.confirm(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PickingConfirmationResponse.from(result));
    }

}
