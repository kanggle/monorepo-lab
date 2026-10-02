package com.example.product.presentation.controller;

import com.example.product.application.service.SellerMemberService;
import com.example.product.presentation.dto.SellerMemberDtos.InviteRequest;
import com.example.product.presentation.dto.SellerMemberDtos.InviteResponse;
import com.example.product.presentation.dto.SellerMemberDtos.MembersResponse;
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPERATOR-plane seller-member surface (ADR-MONO-079 D5, TASK-MONO-752; product-api.md § Seller members):
 * the console seller screen's member list and invite. Same plane and authorization as
 * {@link AdminSellerController} (gateway {@code /api/admin/**} OPERATOR branch + {@code X-User-Role} ∋
 * {@code ECOMMERCE_OPERATOR}); a separate controller so the seller lifecycle surface keeps its own collaborators.
 */
@RestController
@RequestMapping("/api/admin/sellers/{sellerId}")
@RequiredArgsConstructor
public class AdminSellerMemberController {

    private static final String ROLE_ADMIN = "ECOMMERCE_OPERATOR";

    private final SellerMemberService sellerMemberService;

    @GetMapping("/members")
    public ResponseEntity<MembersResponse> members(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        return ResponseEntity.ok(MembersResponse.from(sellerMemberService.list(sellerId)));
    }

    /**
     * Issues an invitation. The raw token is in this response and nowhere else (no mail path — ADR-MONO-078 R1);
     * the operator hands it to the invited person.
     */
    @PostMapping("/invitations")
    public ResponseEntity<InviteResponse> invite(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @PathVariable String sellerId,
            @Valid @RequestBody InviteRequest request) {
        validateAdminRole(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InviteResponse.from(sellerMemberService.invite(sellerId, request.email(), operatorId)));
    }

    private static void validateAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            throw new AccessDeniedException();
        }
        for (String role : userRole.split(",")) {
            if (ROLE_ADMIN.equalsIgnoreCase(role.trim())) {
                return;
            }
        }
        throw new AccessDeniedException();
    }
}
