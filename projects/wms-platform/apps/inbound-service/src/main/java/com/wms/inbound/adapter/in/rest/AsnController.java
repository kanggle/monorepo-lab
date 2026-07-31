package com.wms.inbound.adapter.in.rest;

import com.example.common.page.PageQuery;
import com.wms.inbound.adapter.in.rest.dto.AsnResponse;
import com.wms.inbound.adapter.in.rest.dto.AsnSummaryResponse;
import com.wms.inbound.adapter.in.rest.dto.CancelAsnRequest;
import com.wms.inbound.adapter.in.rest.dto.CloseAsnRequest;
import com.wms.inbound.adapter.in.rest.dto.CloseAsnResponse;
import com.wms.inbound.adapter.in.rest.dto.CreateAsnRequest;
import com.wms.inbound.adapter.in.rest.dto.PageResponse;
import com.wms.inbound.adapter.in.rest.util.RequestContext;
import com.wms.inbound.application.command.CancelAsnCommand;
import com.wms.inbound.application.command.CloseAsnCommand;
import com.wms.inbound.application.command.ReceiveAsnCommand;
import com.wms.inbound.application.port.in.CancelAsnUseCase;
import com.wms.inbound.application.port.in.CloseAsnUseCase;
import com.wms.inbound.application.port.in.QueryAsnUseCase;
import com.wms.inbound.application.port.in.ReceiveAsnUseCase;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.application.result.CloseAsnResult;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inbound/asns")
public class AsnController {

    /** Matches {@code AsnJpaRepository}'s hardcoded ORDER BY — not client-configurable (v1). */
    private static final String DEFAULT_SORT = "createdAt,desc";

    private final ReceiveAsnUseCase receiveAsn;
    private final CancelAsnUseCase cancelAsn;
    private final CloseAsnUseCase closeAsn;
    private final QueryAsnUseCase queryAsn;

    public AsnController(ReceiveAsnUseCase receiveAsn,
                         CancelAsnUseCase cancelAsn,
                         CloseAsnUseCase closeAsn,
                         QueryAsnUseCase queryAsn) {
        this.receiveAsn = receiveAsn;
        this.cancelAsn = cancelAsn;
        this.closeAsn = closeAsn;
        this.queryAsn = queryAsn;
    }

    @PostMapping
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<AsnResponse> createAsn(
            @Valid @RequestBody CreateAsnRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        RequestContext.requireIdempotencyKey(idempotencyKey);
        ReceiveAsnCommand command = new ReceiveAsnCommand(
                request.asnNo(), "MANUAL",
                request.supplierPartnerId(), request.warehouseId(),
                request.expectedArriveDate(), request.notes(),
                request.lines().stream()
                        .map(l -> new ReceiveAsnCommand.Line(l.skuId(), l.lotId(), l.expectedQty()))
                        .toList(),
                RequestContext.actorId(jwt), RequestContext.callerRoles(authentication));
        AsnResult result = receiveAsn.receive(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(String.valueOf(result.version()))
                .body(AsnResponse.from(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<AsnResponse> getAsn(@PathVariable UUID id) {
        AsnResult result = queryAsn.findById(id);
        return ResponseEntity.ok()
                .eTag(String.valueOf(result.version()))
                .body(AsnResponse.from(result));
    }

    @GetMapping
    @PreAuthorize("hasRole('INBOUND_READ') or hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public PageResponse<AsnSummaryResponse> listAsns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Validates page >= 0, 1 <= size <= 100 per inbound-service-api.md §Pagination
        // (400 VALIDATION_ERROR on violation — GlobalExceptionHandler maps
        // IllegalArgumentException, matching the existing invalid-input behavior).
        PageQuery pageQuery = new PageQuery(page, size, null, null);
        return PageResponse.from(
                queryAsn.list(status, warehouseId, pageQuery.page(), pageQuery.size()),
                DEFAULT_SORT, AsnSummaryResponse::from);
    }

    @PostMapping("/{id}:cancel")
    @PreAuthorize("hasRole('INBOUND_ADMIN')")
    public ResponseEntity<AsnResponse> cancelAsn(
            @PathVariable UUID id,
            @Valid @RequestBody CancelAsnRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        RequestContext.requireIdempotencyKey(idempotencyKey);
        CancelAsnCommand command = new CancelAsnCommand(
                id, request.reason(), request.version(),
                RequestContext.actorId(jwt), RequestContext.callerRoles(authentication));
        AsnResult result = cancelAsn.cancel(command);
        return ResponseEntity.ok(AsnResponse.from(result));
    }

    @PostMapping("/{id}:close")
    @PreAuthorize("hasRole('INBOUND_WRITE') or hasRole('INBOUND_ADMIN')")
    public ResponseEntity<CloseAsnResponse> closeAsn(
            @PathVariable UUID id,
            @RequestBody(required = false) CloseAsnRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        RequestContext.requireIdempotencyKey(idempotencyKey);
        long version = request != null ? request.version() : 0L;
        CloseAsnCommand command = new CloseAsnCommand(
                id, version, RequestContext.actorId(jwt), RequestContext.callerRoles(authentication));
        CloseAsnResult result = closeAsn.close(command);
        return ResponseEntity.ok(CloseAsnResponse.from(result));
    }
}
