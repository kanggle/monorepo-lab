package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.controller.support.ControllerSupport;
import com.wms.master.adapter.in.web.dto.request.DeactivateLocationRequest;
import com.wms.master.adapter.in.web.dto.request.ReactivateLocationRequest;
import com.wms.master.adapter.in.web.dto.request.UpdateLocationRequest;
import com.wms.master.adapter.in.web.dto.response.LocationResponse;
import com.wms.master.adapter.in.web.dto.response.PageResponse;
import com.wms.master.application.port.in.LocationCrudUseCase;
import com.wms.master.application.port.in.LocationQueryUseCase;
import com.wms.master.application.query.ListLocationsCriteria;
import com.wms.master.application.query.ListLocationsQuery;
import com.wms.master.adapter.in.web.support.DataScopeSupport;
import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flat Location endpoints: get / list / patch / deactivate / reactivate. The
 * create endpoint is served by {@link LocationCreateController} under the
 * nested route — see
 * {@code specs/contracts/http/master-service-api.md §3}.
 */
@RestController
@RequestMapping("/api/v1/master/locations")
public class LocationController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final LocationCrudUseCase crudUseCase;
    private final LocationQueryUseCase queryUseCase;

    public LocationController(LocationCrudUseCase crudUseCase,
                              LocationQueryUseCase queryUseCase) {
        this.crudUseCase = crudUseCase;
        this.queryUseCase = queryUseCase;
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        LocationResult result = queryUseCase.findById(id, DataScopeSupport.warehouseScopeCodes(jwt));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(LocationResponse.from(result));
    }

    @GetMapping
    public PageResponse<LocationResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String locationType,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        ListLocationsCriteria criteria = new ListLocationsCriteria(
                warehouseId,
                zoneId,
                ControllerSupport.parseEnum(locationType, LocationType.class,
                        "locationType must be one of "
                                + "STORAGE|STAGING_INBOUND|STAGING_OUTBOUND|DAMAGED|QUARANTINE"),
                nullIfBlank(code),
                ControllerSupport.parseEnum(status, WarehouseStatus.class,
                        "status must be ACTIVE or INACTIVE"));
        PageQuery pageQuery = PageQuery.of(page, size,
                ControllerSupport.sortField(sort), ControllerSupport.sortDirection(sort));
        PageResult<LocationResult> result = queryUseCase.list(
                new ListLocationsQuery(criteria, pageQuery, DataScopeSupport.warehouseScopeCodes(jwt)));
        return PageResponse.from(result, sort, LocationResponse::from);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LocationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LocationResult result = crudUseCase.update(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(LocationResponse.from(result));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<LocationResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateLocationRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LocationResult result = crudUseCase.deactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(LocationResponse.from(result));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<LocationResponse> reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ReactivateLocationRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LocationResult result = crudUseCase.reactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(LocationResponse.from(result));
    }

    private static String nullIfBlank(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw;
    }
}
