package com.wms.inventory.adapter.in.web.controller;

import com.wms.inventory.adapter.in.web.JwtHelper;
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
 *
 * <h2>This is the MANUAL surface — the saga does not come through here (TASK-MONO-528)</h2>
 *
 * <p>Measured 2026-08-13: <b>zero callers repo-wide</b>. The outbound saga allocates over
 * Kafka — {@code ReceiveOrderService} publishes {@code outbound.picking.requested} in the
 * same transaction as order creation (saga step 1), and
 * {@code PickingRequestedConsumer} invokes {@code ReserveStockService}, the very use case
 * {@link #create} wraps. A Kafka consumer carries no JWT, so <b>that path evaluates no
 * role at all</b>; it is trusted by being inside the boundary.
 *
 * <p>Live proof from an untouched demo database: {@code outbound.order.received}
 * 09:42:03.860 → {@code outbound.picking.requested} 09:42:03.885 → {@code reservation}
 * row {@code RESERVED} 09:42:05.464 (+ {@code inventory_movement} {@code PICKING} ×2).
 * {@code PickingFlowIntegrationTest} pins the same path in CI.
 *
 * <p><b>Why that matters here, and why nothing was granted.</b> Two documents used to say
 * {@code outbound-service} calls this surface with an {@code INVENTORY_RESERVE}
 * service-account JWT ({@code inventory-service-api.md} § Authorization, and this
 * service's {@code SecurityConfig}); both were false — {@code outbound-service} declares
 * no HTTP client of any kind. Reading them, {@code TASK-MONO-528} was filed to find a
 * workload credential for a reserve step that was never blocked. It decided to grant
 * {@code INVENTORY_RESERVE} to <b>no</b> client: issuing it would open a surface with no
 * caller, which is how a permission ends up wider than anything that uses it.
 *
 * <p>These endpoints stay, deliberately — they are the out-of-band path for operator
 * recovery, and {@code ReservationControllerSliceTest} keeps their role gate honest.
 * If a caller ever appears, the grant is a decision to take then, with a caller to point at.
 */
@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class ReservationController {

    private static final int DEFAULT_TTL_SECONDS = 86_400;

    /** Matches {@code ReservationRepositoryImpl}'s hardcoded ORDER BY — not client-configurable (v1). */
    private static final String DEFAULT_SORT = "updatedAt,desc";

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
                null, JwtHelper.actorId(jwt), null);
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
                id, request.version(), lines, null, JwtHelper.actorId(jwt));
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
                id, request.reason(), request.version(), null, JwtHelper.actorId(jwt));
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
        return PageResponse.from(queryReservation.list(criteria), DEFAULT_SORT, ReservationResponse::from);
    }

}
