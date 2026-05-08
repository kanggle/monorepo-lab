package com.wms.admin.api.assignment;

import com.wms.admin.api.assignment.dto.AssignmentResponse;
import com.wms.admin.api.assignment.dto.GrantAssignmentRequest;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.application.assignment.AssignmentService;
import com.wms.admin.application.assignment.GrantAssignmentCommand;
import com.wms.admin.application.assignment.GrantAssignmentResult;
import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.UserRoleAssignment;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assignment REST surface — admin-service-api.md § 4.
 */
@RestController
@RequestMapping("/api/v1/admin/assignments")
public class AssignmentController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "grantedAt,desc";

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public ResponseEntity<AssignmentResponse> grant(@Valid @RequestBody GrantAssignmentRequest request,
                                                    @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        GrantAssignmentResult result = assignmentService.grant(new GrantAssignmentCommand(
                request.userId(), request.roleId(), request.warehouseId(), actorId));
        UserRoleAssignment a = result.assignment();
        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/v1/admin/assignments/" + a.id()))
                    .eTag(etag(a.version()))
                    .body(AssignmentResponse.from(a));
        }
        // Idempotent grant: existing active row → 200.
        return ResponseEntity.ok().eTag(etag(a.version())).body(AssignmentResponse.from(a));
    }

    @GetMapping
    public PageResponse<AssignmentResponse> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        AssignmentStatus statusEnum = parseStatus(status);
        Page<UserRoleAssignment> result =
                assignmentService.search(userId, roleId, warehouseId, statusEnum, pageable(page, size, sort));
        return PageResponse.from(result, sort, AssignmentResponse::from);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                       @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        assignmentService.revoke(id, actorId);
        return ResponseEntity.noContent().build();
    }

    private static String etag(long version) {
        return "\"v" + version + "\"";
    }

    private static AssignmentStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AssignmentStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status must be ACTIVE or REVOKED");
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
