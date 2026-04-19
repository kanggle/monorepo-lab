package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.dto.request.CreateWarehouseRequest;
import com.wms.master.adapter.in.web.dto.request.DeactivateWarehouseRequest;
import com.wms.master.adapter.in.web.dto.request.ReactivateWarehouseRequest;
import com.wms.master.adapter.in.web.dto.request.UpdateWarehouseRequest;
import com.wms.master.adapter.in.web.dto.response.PageResponse;
import com.wms.master.adapter.in.web.dto.response.WarehouseResponse;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.WarehouseStatus;
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
@RequestMapping("/api/v1/master/warehouses")
public class WarehouseController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final WarehouseCrudUseCase crudUseCase;
    private final WarehouseQueryUseCase queryUseCase;

    public WarehouseController(WarehouseCrudUseCase crudUseCase, WarehouseQueryUseCase queryUseCase) {
        this.crudUseCase = crudUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping
    public ResponseEntity<WarehouseResponse> create(
            @Valid @RequestBody CreateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.create(request.toCommand(actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/master/warehouses/" + result.id()))
                .eTag(etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getById(@PathVariable UUID id) {
        WarehouseResult result = queryUseCase.findById(id);
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @GetMapping
    public PageResponse<WarehouseResponse> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        WarehouseListCriteria criteria = new WarehouseListCriteria(parseStatus(status), q);
        PageQuery pageQuery = PageQuery.of(page, size, sortField(sort), sortDirection(sort));
        PageResult<WarehouseResult> result = queryUseCase.list(new ListWarehousesQuery(criteria, pageQuery));
        return PageResponse.from(result, sort, WarehouseResponse::from);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WarehouseResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.update(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<WarehouseResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.deactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<WarehouseResponse> reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ReactivateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.reactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(WarehouseResponse.from(result));
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

    private static String sortField(String sort) {
        int comma = sort.indexOf(',');
        return comma < 0 ? sort : sort.substring(0, comma);
    }

    private static String sortDirection(String sort) {
        int comma = sort.indexOf(',');
        return comma < 0 ? "asc" : sort.substring(comma + 1);
    }
}
