package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.dto.request.CreateSkuRequest;
import com.wms.master.adapter.in.web.dto.request.DeactivateSkuRequest;
import com.wms.master.adapter.in.web.dto.request.ReactivateSkuRequest;
import com.wms.master.adapter.in.web.dto.request.UpdateSkuRequest;
import com.wms.master.adapter.in.web.dto.response.PageResponse;
import com.wms.master.adapter.in.web.dto.response.SkuResponse;
import com.wms.master.application.port.in.SkuCrudUseCase;
import com.wms.master.application.port.in.SkuQueryUseCase;
import com.wms.master.application.query.ListSkusCriteria;
import com.wms.master.application.query.ListSkusQuery;
import com.wms.master.application.result.SkuResult;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
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
@RequestMapping("/api/v1/master/skus")
public class SkuController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final SkuCrudUseCase crudUseCase;
    private final SkuQueryUseCase queryUseCase;

    public SkuController(SkuCrudUseCase crudUseCase, SkuQueryUseCase queryUseCase) {
        this.crudUseCase = crudUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping
    public ResponseEntity<SkuResponse> create(
            @Valid @RequestBody CreateSkuRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        SkuResult result = crudUseCase.create(request.toCommand(actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/master/skus/" + result.id()))
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SkuResponse> getById(@PathVariable UUID id) {
        SkuResult result = queryUseCase.findById(id);
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
    }

    @GetMapping("/by-code/{skuCode}")
    public ResponseEntity<SkuResponse> getByCode(@PathVariable String skuCode) {
        // Service uppercases internally; controller forwards raw input.
        SkuResult result = queryUseCase.findBySkuCode(skuCode);
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
    }

    @GetMapping("/by-barcode/{barcode}")
    public ResponseEntity<SkuResponse> getByBarcode(@PathVariable String barcode) {
        SkuResult result = queryUseCase.findByBarcode(barcode);
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
    }

    @GetMapping
    public PageResponse<SkuResponse> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String trackingType,
            @RequestParam(required = false) String baseUom,
            @RequestParam(required = false) String barcode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        ListSkusCriteria criteria = new ListSkusCriteria(
                parseStatus(status),
                q,
                parseTrackingType(trackingType),
                parseBaseUom(baseUom),
                barcode);
        PageQuery pageQuery = PageQuery.of(page, size, sortField(sort), sortDirection(sort));
        PageResult<SkuResult> result = queryUseCase.list(new ListSkusQuery(criteria, pageQuery));
        return PageResponse.from(result, sort, SkuResponse::from);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SkuResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSkuRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        SkuResult result = crudUseCase.update(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<SkuResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateSkuRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        SkuResult result = crudUseCase.deactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<SkuResponse> reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ReactivateSkuRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        SkuResult result = crudUseCase.reactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(SkuResponse.from(result));
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

    private static TrackingType parseTrackingType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TrackingType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("trackingType must be one of NONE|LOT");
        }
    }

    private static BaseUom parseBaseUom(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BaseUom.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("baseUom must be one of EA|BOX|PLT|KG|L");
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
