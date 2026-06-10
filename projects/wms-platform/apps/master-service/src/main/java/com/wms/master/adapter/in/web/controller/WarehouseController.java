package com.wms.master.adapter.in.web.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.controller.support.ControllerSupport;
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
import com.example.security.jwt.AbacDataScope;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.exception.DataScopeForbiddenException;
import com.wms.master.domain.model.WarehouseStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
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
                .eTag(ControllerSupport.etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        WarehouseResult result = queryUseCase.findById(id);
        requireWarehouseInScope(jwt, result.warehouseCode());
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    /**
     * TASK-MONO-215 (ADR-MONO-025 § 3.3 step 2): the first ABAC data-scope
     * enforcement point in wms. The operator's {@code data_scope}/{@code org_scope}
     * claim (read via the shared {@link AbacDataScope}) is interpreted by wms as a
     * set of warehouse codes (per {@code platform/abac-data-scope.md} § 3).
     *
     * <p><b>Net-zero</b>: an unrestricted ({@code "*"}) or unscoped (empty/absent —
     * base authorization_code tokens and machine tokens carry no data_scope; the
     * assume-tenant producer emits {@code ["*"]} for unscoped assignments) operator
     * is NOT confined. Only a deliberately-scoped operator (a non-empty set without
     * {@code "*"}) is restricted: targeting a warehouse outside the set → 403
     * {@code DATA_SCOPE_FORBIDDEN}.
     */
    private static void requireWarehouseInScope(Jwt jwt, String warehouseCode) {
        if (jwt == null) {
            return; // unauthenticated requests are already rejected by the resource server
        }
        AbacDataScope scope = AbacDataScope.fromClaimValues(
                jwt.getClaim(AbacDataScope.CLAIM_DATA_SCOPE),
                jwt.getClaim(AbacDataScope.CLAIM_ORG_SCOPE));
        boolean restricted = !scope.isEmpty() && !scope.isUnrestricted();
        if (restricted && !scope.allows(warehouseCode)) {
            throw new DataScopeForbiddenException(
                    "warehouse " + warehouseCode + " is outside the operator's data-scope");
        }
    }

    @GetMapping
    public PageResponse<WarehouseResponse> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        WarehouseListCriteria criteria = new WarehouseListCriteria(
                ControllerSupport.parseEnum(status, WarehouseStatus.class, "status must be ACTIVE or INACTIVE"), q);
        PageQuery pageQuery = PageQuery.of(page, size, ControllerSupport.sortField(sort), ControllerSupport.sortDirection(sort));
        PageResult<WarehouseResult> result = queryUseCase.list(
                new ListWarehousesQuery(criteria, pageQuery, scopeWarehouseCodes(jwt)));
        return PageResponse.from(result, sort, WarehouseResponse::from);
    }

    /**
     * TASK-BE-349 (ADR-MONO-025 follow-on): the data-scope confinement for the
     * warehouse LIST page — the sibling of {@link #requireWarehouseInScope} for
     * the collection endpoint. Returns the set of warehouse codes a deliberately
     * data-scoped operator may see, or {@code null} when the operator is
     * unrestricted ({@code "*"}) or unscoped (empty/absent — base
     * authorization_code and machine tokens carry no scope; the assume-tenant
     * producer emits {@code ["*"]} for unscoped assignments). {@code null} = the
     * net-zero path: the query runs unfiltered, exactly as before data-scoping.
     */
    private static Set<String> scopeWarehouseCodes(Jwt jwt) {
        if (jwt == null) {
            return null; // unauthenticated requests are already rejected by the resource server
        }
        AbacDataScope scope = AbacDataScope.fromClaimValues(
                jwt.getClaim(AbacDataScope.CLAIM_DATA_SCOPE),
                jwt.getClaim(AbacDataScope.CLAIM_ORG_SCOPE));
        boolean restricted = !scope.isEmpty() && !scope.isUnrestricted();
        return restricted ? scope.tokens() : null;
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WarehouseResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.update(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<WarehouseResponse> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.deactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(WarehouseResponse.from(result));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<WarehouseResponse> reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ReactivateWarehouseRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        WarehouseResult result = crudUseCase.reactivate(request.toCommand(id, actorId));
        return ResponseEntity.ok()
                .eTag(ControllerSupport.etag(result.version()))
                .body(WarehouseResponse.from(result));
    }
}
