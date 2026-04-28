package com.wms.inventory.adapter.in.web.controller;

import com.wms.inventory.adapter.in.web.dto.request.ConfirmReservationRequest;
import com.wms.inventory.adapter.in.web.dto.request.CreateReservationRequest;
import com.wms.inventory.adapter.in.web.dto.request.ReleaseReservationRequest;
import com.wms.inventory.adapter.in.web.dto.response.PageResponse;
import com.wms.inventory.adapter.in.web.dto.response.ReservationResponse;
import com.wms.inventory.application.command.ConfirmReservationCommand;
import com.wms.inventory.application.command.ReleaseReservationCommand;
import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ConfirmReservationUseCase;
import com.wms.inventory.application.port.in.QueryReservationUseCase;
import com.wms.inventory.application.port.in.ReleaseReservationUseCase;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.exception.ReservationNotFoundException;
import com.wms.inventory.domain.model.ReservationStatus;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for reservations. Authoritative reference:
 * {@code specs/contracts/http/inventory-service-api.md} §4.
 *
 * <p>Method-level {@code @PreAuthorize} mirrors the contract's role table:
 * RESERVE for create / confirm; RESERVE or ADMIN for release; READ for queries.
 */
@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class ReservationController {

    private static final int DEFAULT_TTL_SECONDS = 86_400;

    private final ReserveStockUseCase reserveStock;
    private final ConfirmReservationUseCase confirmReservation;
    private final ReleaseReservationUseCase releaseReservation;
    private final QueryReservationUseCase queryReservation;

    public ReservationController(ReserveStockUseCase reserveStock,
                                 ConfirmReservationUseCase confirmReservation,
                                 ReleaseReservationUseCase releaseReservation,
                                 QueryReservationUseCase queryReservation) {
        this.reserveStock = reserveStock;
        this.confirmReservation = confirmReservation;
        this.releaseReservation = releaseReservation;
        this.queryReservation = queryReservation;
    }

    @PostMapping
    @PreAuthorize("hasRole('INVENTORY_RESERVE')")
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        List<ReserveStockCommand.Line> lines = request.lines().stream()
                .map(l -> new ReserveStockCommand.Line(l.inventoryId(), l.quantity()))
                .toList();
        int ttl = request.ttlSeconds() == null ? DEFAULT_TTL_SECONDS : request.ttlSeconds();
        ReserveStockCommand command = new ReserveStockCommand(
                request.pickingRequestId(), request.warehouseId(), lines, ttl,
                null, actorId(jwt), null);
        ReservationView result = reserveStock.reserve(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(result));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('INVENTORY_RESERVE')")
    public ResponseEntity<ReservationResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmReservationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        List<ConfirmReservationCommand.Line> lines = request.lines().stream()
                .map(l -> new ConfirmReservationCommand.Line(l.reservationLineId(), l.shippedQuantity()))
                .toList();
        ConfirmReservationCommand command = new ConfirmReservationCommand(
                id, request.version(), lines, null, actorId(jwt));
        ReservationView result = confirmReservation.confirm(command);
        return ResponseEntity.ok(ReservationResponse.from(result));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasRole('INVENTORY_RESERVE') or hasRole('INVENTORY_ADMIN')")
    public ResponseEntity<ReservationResponse> release(
            @PathVariable UUID id,
            @Valid @RequestBody ReleaseReservationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (request.reason() == com.wms.inventory.domain.model.ReleasedReason.EXPIRED) {
            throw new IllegalArgumentException(
                    "EXPIRED is reserved for the TTL job — callers may use CANCELLED or MANUAL");
        }
        ReleaseReservationCommand command = new ReleaseReservationCommand(
                id, request.reason(), request.version(), null, actorId(jwt));
        ReservationView result = releaseReservation.release(command);
        return ResponseEntity.ok(ReservationResponse.from(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public ResponseEntity<ReservationResponse> getById(@PathVariable UUID id) {
        ReservationView view = queryReservation.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + id));
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"v" + view.version() + "\"")
                .body(ReservationResponse.from(view));
    }

    @GetMapping
    @PreAuthorize("hasRole('INVENTORY_READ')")
    public PageResponse<ReservationResponse> list(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID pickingRequestId,
            @RequestParam(required = false) Instant expiresAfter,
            @RequestParam(required = false) Instant expiresBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ReservationListCriteria criteria = new ReservationListCriteria(
                status, warehouseId, pickingRequestId, expiresAfter, expiresBefore, page, size);
        return PageResponse.from(queryReservation.list(criteria), ReservationResponse::from);
    }

    private static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
