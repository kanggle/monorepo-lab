package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.dto.request.CreatePartnerRequest;
import com.wms.master.adapter.in.web.dto.request.DeactivatePartnerRequest;
import com.wms.master.adapter.in.web.dto.request.ReactivatePartnerRequest;
import com.wms.master.adapter.in.web.dto.request.UpdatePartnerRequest;
import com.wms.master.adapter.in.web.dto.response.PageResponse;
import com.wms.master.adapter.in.web.dto.response.PartnerResponse;
import com.wms.master.application.port.in.PartnerCrudUseCase;
import com.wms.master.application.port.in.PartnerQueryUseCase;
import com.wms.master.application.query.ListPartnersCriteria;
import com.wms.master.application.query.ListPartnersQuery;
import com.wms.master.application.result.PartnerResult;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.PartnerType;
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
@RequestMapping("/api/v1/master/partners")
public class PartnerController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final PartnerCrudUseCase crudUseCase;
    private final PartnerQueryUseCase queryUseCase;

    public PartnerController(PartnerCrudUseCase crudUseCase, PartnerQueryUseCase queryUseCase) {
        this.crudUseCase = crudUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping
    public ResponseEntity<PartnerResponse> create(
            @Valid @RequestBody CreatePartnerRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        PartnerResult result = crudUseCase.create(request.toCommand(actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/master/partners/" + result.id()))
                .eTag(etag(result.version()))
                .body(PartnerResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerResponse> getById(@PathVariable UUID id) {
        PartnerResult result = queryUseCase.findById(id);
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(PartnerResponse.from(result));
    }

    @GetMapping
    public PageResponse<PartnerResponse> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String partnerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        ListPartnersCriteria criteria = new ListPartnersCriteria(
                parseStatus(status),
                q,
                parsePartnerType(partnerType));
        PageQuery pageQuery = PageQuery.of(page, size, sortField(sort), sortDirection(sort));
        PageResult<PartnerResult> result = queryUseCase.list(new ListPartnersQuery(criteria, pageQuery));
        return PageResponse.from(result, sort, PartnerResponse::from);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PartnerResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePartnerRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        PartnerResult result = crudUseCase.update(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(PartnerResponse.from(result));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<PartnerResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivatePartnerRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        PartnerResult result = crudUseCase.deactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(PartnerResponse.from(result));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<PartnerResponse> reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ReactivatePartnerRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        PartnerResult result = crudUseCase.reactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(PartnerResponse.from(result));
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

    private static PartnerType parsePartnerType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PartnerType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("partnerType must be one of SUPPLIER|CUSTOMER|BOTH");
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
