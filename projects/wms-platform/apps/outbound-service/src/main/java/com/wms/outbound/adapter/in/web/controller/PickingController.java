package com.wms.outbound.adapter.in.web.controller;

import com.wms.outbound.adapter.in.web.dto.request.ConfirmPickingRequest;
import com.wms.outbound.adapter.in.web.dto.response.PickingConfirmationResponse;
import com.wms.outbound.application.command.ConfirmPickingCommand;
import com.wms.outbound.application.command.ConfirmPickingLineCommand;
import com.wms.outbound.application.port.in.ConfirmPickingUseCase;
import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.result.PickingConfirmationResult;
import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.domain.exception.PickingRequestNotFoundException;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
        requireIdempotencyKey(idempotencyKey);

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
                actorId(jwt),
                callerRoles(authentication));
        PickingConfirmationResult result = confirmPicking.confirm(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PickingConfirmationResponse.from(result));
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
    }

    static Set<String> callerRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : authentication.getAuthorities()) {
            roles.add(a.getAuthority());
        }
        return Set.copyOf(roles);
    }

    static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
