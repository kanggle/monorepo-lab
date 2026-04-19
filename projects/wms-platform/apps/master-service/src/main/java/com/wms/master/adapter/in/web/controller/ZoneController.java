package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.dto.request.CreateZoneRequest;
import com.wms.master.adapter.in.web.dto.request.DeactivateZoneRequest;
import com.wms.master.adapter.in.web.dto.request.ReactivateZoneRequest;
import com.wms.master.adapter.in.web.dto.request.UpdateZoneRequest;
import com.wms.master.adapter.in.web.dto.response.PageResponse;
import com.wms.master.adapter.in.web.dto.response.ZoneResponse;
import com.wms.master.application.port.in.ZoneCrudUseCase;
import com.wms.master.application.port.in.ZoneQueryUseCase;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.application.query.ListZonesQuery;
import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/master/warehouses/{warehouseId}/zones")
public class ZoneController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final ZoneCrudUseCase crudUseCase;
    private final ZoneQueryUseCase queryUseCase;

    public ZoneController(ZoneCrudUseCase crudUseCase, ZoneQueryUseCase queryUseCase) {
        this.crudUseCase = crudUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping
    public ResponseEntity<ZoneResponse> create(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody CreateZoneRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        ZoneResult result = crudUseCase.create(request.toCommand(warehouseId, actorId));
        return ResponseEntity
                .created(URI.create(
                        "/api/v1/master/warehouses/" + warehouseId + "/zones/" + result.id()))
                .eTag(etag(result.version()))
                .body(ZoneResponse.from(result));
    }

    @GetMapping("/{zoneId}")
    public ResponseEntity<ZoneResponse> getById(
            @PathVariable UUID warehouseId,
            @PathVariable UUID zoneId) {
        ZoneResult result = queryUseCase.findById(zoneId);
        // Defensive: a zone's warehouseId must match the path variable. If they
        // disagree, surface a 404 rather than leaking a cross-warehouse id.
        if (!result.warehouseId().equals(warehouseId)) {
            throw new com.wms.master.domain.exception.ZoneNotFoundException(zoneId.toString());
        }
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(ZoneResponse.from(result));
    }

    @GetMapping
    public PageResponse<ZoneResponse> list(
            @PathVariable UUID warehouseId,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String zoneType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        ListZonesCriteria criteria = new ListZonesCriteria(
                warehouseId, parseStatus(status), parseZoneType(zoneType));
        PageQuery pageQuery = PageQuery.of(page, size, sortField(sort), sortDirection(sort));
        PageResult<ZoneResult> result = queryUseCase.list(new ListZonesQuery(criteria, pageQuery));
        return PageResponse.from(result, sort, ZoneResponse::from);
    }

    @PatchMapping("/{zoneId}")
    public ResponseEntity<ZoneResponse> update(
            @PathVariable UUID warehouseId,
            @PathVariable UUID zoneId,
            @Valid @RequestBody UpdateZoneRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        ZoneResult result = crudUseCase.update(request.toCommand(zoneId, actorId));
        if (!result.warehouseId().equals(warehouseId)) {
            throw new com.wms.master.domain.exception.ZoneNotFoundException(zoneId.toString());
        }
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(ZoneResponse.from(result));
    }

    @PostMapping("/{zoneId}/deactivate")
    public ResponseEntity<ZoneResponse> deactivate(
            @PathVariable UUID warehouseId,
            @PathVariable UUID zoneId,
            @Valid @RequestBody DeactivateZoneRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        ZoneResult result = crudUseCase.deactivate(request.toCommand(zoneId, actorId));
        if (!result.warehouseId().equals(warehouseId)) {
            throw new com.wms.master.domain.exception.ZoneNotFoundException(zoneId.toString());
        }
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(ZoneResponse.from(result));
    }

    @PostMapping("/{zoneId}/reactivate")
    public ResponseEntity<ZoneResponse> reactivate(
            @PathVariable UUID warehouseId,
            @PathVariable UUID zoneId,
            @Valid @RequestBody ReactivateZoneRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        ZoneResult result = crudUseCase.reactivate(request.toCommand(zoneId, actorId));
        if (!result.warehouseId().equals(warehouseId)) {
            throw new com.wms.master.domain.exception.ZoneNotFoundException(zoneId.toString());
        }
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(ZoneResponse.from(result));
    }

    private static String etag(long version) {
        return "\"v" + version + "\"";
    }

    private static WarehouseStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return WarehouseStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("status must be ACTIVE or INACTIVE");
        }
    }

    private static ZoneType parseZoneType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ZoneType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "zoneType must be one of AMBIENT|CHILLED|FROZEN|RETURNS|BULK|PICK");
        }
    }

    private static String sortField(String sort) {
        int comma = sort.indexOf(',');
        return comma < 0 ? sort : sort.substring(0, comma);
    }

    private static String sortDirection(String sort) {
        int comma = sort.indexOf(',');
        return comma < 0 ? "asc" : sort.substring(comma + 1);
    }
}
