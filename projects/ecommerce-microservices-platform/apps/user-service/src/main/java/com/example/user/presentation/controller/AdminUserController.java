package com.example.user.presentation.controller;

import com.example.common.summary.PeriodSummary;
import com.example.user.application.service.UserProfileService;
import com.example.web.exception.AccessDeniedException;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.presentation.dto.response.AdminUserListResponse;
import com.example.user.presentation.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserProfileService userProfileService;

    @GetMapping("/summary")
    public ResponseEntity<PeriodSummary> getUserCountSummary(
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        validateAdminRole(userRole);
        var result = userProfileService.getPeriodSummary();
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<AdminUserListResponse> listUsers(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(required = false) ProfileStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(userRole);
        var result = userProfileService.listUsers(status, email, page, size);
        return ResponseEntity.ok(AdminUserListResponse.from(result));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUser(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID userId) {
        validateAdminRole(userRole);
        var result = userProfileService.getProfile(userId);
        return ResponseEntity.ok(UserProfileResponse.from(result));
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if (ROLE_ADMIN.equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
