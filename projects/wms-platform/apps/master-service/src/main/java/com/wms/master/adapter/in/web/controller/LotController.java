package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.dto.request.CreateLotRequest;
import com.wms.master.adapter.in.web.dto.request.DeactivateLotRequest;
import com.wms.master.adapter.in.web.dto.request.ReactivateLotRequest;
import com.wms.master.adapter.in.web.dto.request.UpdateLotRequest;
import com.wms.master.adapter.in.web.dto.response.LotResponse;
import com.wms.master.adapter.in.web.dto.response.PageResponse;
import com.wms.master.application.port.in.LotCrudUseCase;
import com.wms.master.application.port.in.LotQueryUseCase;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.application.query.ListLotsQuery;
import com.wms.master.application.result.LotResult;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.LotStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

/**
 * Lot HTTP adapter. Nested creation under a SKU matches
 * {@code specs/contracts/http/master-service-api.md} §6:
 *
 * <ul>
 *   <li>POST   /api/v1/master/skus/{skuId}/lots — create
 *   <li>GET    /api/v1/master/skus/{skuId}/lots — list per SKU
 *   <li>GET    /api/v1/master/lots/{id} — get
 *   <li>GET    /api/v1/master/lots — flat list
 *   <li>PATCH  /api/v1/master/lots/{id} — update
 *   <li>POST   /api/v1/master/lots/{id}/deactivate
 *   <li>POST   /api/v1/master/lots/{id}/reactivate
 * </ul>
 *
 * <p>{@code expire} is an internal, scheduler-only transition and has no
 * HTTP surface (see {@code scheduler/LotExpirationScheduler}).
 */
@RestController
@RequestMapping("/api/v1/master")
public class LotController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final LotCrudUseCase crudUseCase;
    private final LotQueryUseCase queryUseCase;

    public LotController(LotCrudUseCase crudUseCase, LotQueryUseCase queryUseCase) {
        this.crudUseCase = crudUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping("/skus/{skuId}/lots")
    public ResponseEntity<LotResponse> create(
            @PathVariable UUID skuId,
            @Valid @RequestBody CreateLotRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LotResult result = crudUseCase.create(request.toCommand(skuId, actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/master/lots/" + result.id()))
                .eTag(etag(result.version()))
                .body(LotResponse.from(result));
    }

    @GetMapping("/lots/{id}")
    public ResponseEntity<LotResponse> getById(@PathVariable UUID id) {
        LotResult result = queryUseCase.findById(id);
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(LotResponse.from(result));
    }

    @GetMapping("/skus/{skuId}/lots")
    public PageResponse<LotResponse> listBySku(
            @PathVariable UUID skuId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expiryBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        ListLotsCriteria criteria = new ListLotsCriteria(
                skuId,
                parseStatus(status),
                parseDate(expiryBefore, "expiryBefore"),
                null);
        PageQuery pageQuery = PageQuery.of(page, size, sortField(sort), sortDirection(sort));
        PageResult<LotResult> result = queryUseCase.list(new ListLotsQuery(criteria, pageQuery));
        return PageResponse.from(result, sort, LotResponse::from);
    }

    @GetMapping("/lots")
    public PageResponse<LotResponse> list(
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expiryBefore,
            @RequestParam(required = false) String expiryAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        ListLotsCriteria criteria = new ListLotsCriteria(
                skuId,
                parseStatus(status),
                parseDate(expiryBefore, "expiryBefore"),
                parseDate(expiryAfter, "expiryAfter"));
        PageQuery pageQuery = PageQuery.of(page, size, sortField(sort), sortDirection(sort));
        PageResult<LotResult> result = queryUseCase.list(new ListLotsQuery(criteria, pageQuery));
        return PageResponse.from(result, sort, LotResponse::from);
    }

    @PatchMapping("/lots/{id}")
    public ResponseEntity<LotResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLotRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LotResult result = crudUseCase.update(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(LotResponse.from(result));
    }

    @PostMapping("/lots/{id}/deactivate")
    public ResponseEntity<LotResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateLotRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LotResult result = crudUseCase.deactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(LotResponse.from(result));
    }

    @PostMapping("/lots/{id}/reactivate")
    public ResponseEntity<LotResponse> reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ReactivateLotRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        LotResult result = crudUseCase.reactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(etag(result.version()))
                .body(LotResponse.from(result));
    }

    private static String etag(long version) {
        return "\"v" + version + "\"";
    }

    private static LotStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LotStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("status must be one of ACTIVE|INACTIVE|EXPIRED");
        }
    }

    private static LocalDate parseDate(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(fieldName + " must be ISO-8601 date (YYYY-MM-DD)");
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
