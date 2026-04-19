package com.wms.master.adapter.in.web.controller;

import com.wms.master.adapter.in.web.dto.request.CreateLocationRequest;
import com.wms.master.adapter.in.web.dto.response.LocationResponse;
import com.wms.master.application.port.in.LocationCrudUseCase;
import com.wms.master.application.result.LocationResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Location creation endpoint. Split out from {@link LocationController} because
 * the create path is nested under {@code /warehouses/{warehouseId}/zones/{zoneId}}
 * while get / list / patch / deactivate / reactivate are flat under
 * {@code /api/v1/master/locations} — see
 * {@code specs/contracts/http/master-service-api.md §3}.
 *
 * <p>The {@code Location} header on {@code 201} points at the flat route so
 * clients can follow it for subsequent operations.
 */
@RestController
@RequestMapping("/api/v1/master/warehouses/{warehouseId}/zones/{zoneId}/locations")
public class LocationCreateController {

    private static final String ACTOR_HEADER = "X-Actor-Id";

    private final LocationCrudUseCase crudUseCase;

    public LocationCreateController(LocationCrudUseCase crudUseCase) {
        this.crudUseCase = crudUseCase;
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(
            @PathVariable UUID warehouseId,
            @PathVariable UUID zoneId,
            @Valid @RequestBody CreateLocationRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LocationResult result = crudUseCase.create(request.toCommand(warehouseId, zoneId, actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/master/locations/" + result.id()))
                .eTag(etag(result.version()))
                .body(LocationResponse.from(result));
    }

    private static String etag(long version) {
        return "\"v" + version + "\"";
    }
}
