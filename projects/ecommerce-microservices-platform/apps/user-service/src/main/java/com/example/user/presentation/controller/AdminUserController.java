package com.example.user.presentation.controller;

import com.example.common.summary.PeriodSummary;
import com.example.user.application.service.UserProfileService;
import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.presentation.dto.response.AdminUserListResponse;
import com.example.user.presentation.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Operator-plane member (회원) reads. TASK-MONO-795 (ADR-MONO-083 P8 · owner decision Q1): every endpoint requires
 * {@code ECOMMERCE_MEMBER_READ} — the member area is read-only (no write path, so no MEMBER_WRITE role). The entry
 * role {@code ECOMMERCE_OPERATOR} alone and the platform wildcard ({@code '*'}, no roles) are refused, as the wildcard
 * was before. Canonical table: ecommerce {@code iam-integration.md} § 업무 역할; comparison rule: {@link RoleListHeader}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    static final String MEMBER_READ = "ECOMMERCE_MEMBER_READ";

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

    private static void validateAdminRole(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, MEMBER_READ)) {
            throw new AccessDeniedException();
        }
    }
}
