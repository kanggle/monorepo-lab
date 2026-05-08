package com.wms.admin.api.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.api.SecurityContextHelper;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.api.role.dto.CreateRoleRequest;
import com.wms.admin.api.role.dto.DeactivateRoleRequest;
import com.wms.admin.api.role.dto.DeactivateRoleResponse;
import com.wms.admin.api.role.dto.RoleResponse;
import com.wms.admin.api.role.dto.UpdateRoleRequest;
import com.wms.admin.application.role.CreateRoleCommand;
import com.wms.admin.application.role.DeactivateRoleCommand;
import com.wms.admin.application.role.DeactivateRoleResult;
import com.wms.admin.application.role.RoleService;
import com.wms.admin.application.role.UpdateRoleCommand;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * Role REST surface — admin-service-api.md § 3.
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final RoleService roleService;
    private final ObjectMapper objectMapper;

    public RoleController(RoleService roleService, ObjectMapper objectMapper) {
        this.roleService = roleService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request,
                                               @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        Role saved = roleService.create(new CreateRoleCommand(
                request.roleCode(), request.name(), request.description(),
                request.permissionsJson(), actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/admin/roles/" + saved.id()))
                .eTag(etag(saved.version()))
                .body(RoleResponse.from(saved, objectMapper));
    }

    @GetMapping
    public PageResponse<RoleResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        RoleStatus statusEnum = parseStatus(status);
        Page<Role> result = roleService.search(statusEnum, pageable(page, size, sort));
        return PageResponse.from(result, sort, r -> RoleResponse.from(r, objectMapper));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> getById(@PathVariable UUID id) {
        Role role = roleService.findById(id);
        return ResponseEntity.ok().eTag(etag(role.version())).body(RoleResponse.from(role, objectMapper));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RoleResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateRoleRequest request,
                                               @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        Role saved = roleService.update(new UpdateRoleCommand(
                id, request.name(), request.description(), request.permissionsJson(), actorId));
        return ResponseEntity.ok().eTag(etag(saved.version())).body(RoleResponse.from(saved, objectMapper));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<DeactivateRoleResponse> deactivate(
            @PathVariable UUID id,
            @RequestBody(required = false) DeactivateRoleRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        boolean force = request != null && request.force();
        boolean callerIsSuperadmin = SecurityContextHelper.isSuperadmin();
        DeactivateRoleResult result = roleService.deactivate(
                new DeactivateRoleCommand(id, force, actorId, callerIsSuperadmin));
        return ResponseEntity.ok()
                .eTag(etag(result.role().version()))
                .body(DeactivateRoleResponse.from(result, objectMapper));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<RoleResponse> reactivate(@PathVariable UUID id,
                                                   @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        Role saved = roleService.reactivate(id, actorId);
        return ResponseEntity.ok().eTag(etag(saved.version())).body(RoleResponse.from(saved, objectMapper));
    }

    private static String etag(long version) {
        return "\"v" + version + "\"";
    }

    private static RoleStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RoleStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status must be ACTIVE or INACTIVE");
        }
    }

    private static PageRequest pageable(int page, int size, String sort) {
        int comma = sort.indexOf(',');
        String field = comma < 0 ? sort : sort.substring(0, comma);
        String dir = comma < 0 ? "asc" : sort.substring(comma + 1);
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize, Sort.by(direction, field));
    }
}
