package com.wms.admin.api.user;

import com.wms.admin.api.SecurityContextHelper;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.api.user.dto.CreateUserRequest;
import com.wms.admin.api.user.dto.DeactivateUserRequest;
import com.wms.admin.api.user.dto.DeactivateUserResponse;
import com.wms.admin.api.user.dto.UpdateUserRequest;
import com.wms.admin.api.user.dto.UserResponse;
import com.wms.admin.application.user.CreateUserCommand;
import com.wms.admin.application.user.DeactivateUserCommand;
import com.wms.admin.application.user.DeactivateUserResult;
import com.wms.admin.application.user.UpdateUserCommand;
import com.wms.admin.application.user.UserService;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import com.wms.admin.domain.error.UserNotFoundException;
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
 * User REST surface — admin-service-api.md § 2.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        User saved = userService.create(new CreateUserCommand(
                request.userCode(), request.email(), request.name(), request.phone(),
                request.defaultWarehouseId(), actorId));
        return ResponseEntity
                .created(URI.create("/api/v1/admin/users/" + saved.id()))
                .eTag(etag(saved.version()))
                .body(UserResponse.from(saved));
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        UserStatus statusEnum = parseStatus(status);
        Page<User> result = userService.search(statusEnum, warehouseId, q, pageable(page, size, sort));
        return PageResponse.from(result, sort, UserResponse::from);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        User user = userService.findById(id);
        return ResponseEntity.ok().eTag(etag(user.version())).body(UserResponse.from(user));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request,
                                               @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        User saved = userService.update(new UpdateUserCommand(
                id, request.name(), request.email(), request.phone(),
                request.defaultWarehouseId(), actorId));
        return ResponseEntity.ok().eTag(etag(saved.version())).body(UserResponse.from(saved));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<DeactivateUserResponse> deactivate(
            @PathVariable UUID id,
            @RequestBody(required = false) DeactivateUserRequest request,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        boolean force = request != null && request.force();
        boolean callerIsSuperadmin = SecurityContextHelper.isSuperadmin();
        DeactivateUserResult result = userService.deactivate(
                new DeactivateUserCommand(id, force, actorId, callerIsSuperadmin));
        return ResponseEntity.ok()
                .eTag(etag(result.user().version()))
                .body(DeactivateUserResponse.from(result));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<UserResponse> reactivate(@PathVariable UUID id,
                                                   @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        User saved = userService.reactivate(id, actorId);
        return ResponseEntity.ok().eTag(etag(saved.version())).body(UserResponse.from(saved));
    }

    private static String etag(long version) {
        return "\"v" + version + "\"";
    }

    private static UserStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(raw.toUpperCase());
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
