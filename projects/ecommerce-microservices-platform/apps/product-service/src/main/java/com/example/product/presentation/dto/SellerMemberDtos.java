package com.example.product.presentation.dto;

import com.example.product.application.service.SellerMemberService;
import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerInvitationStatus;
import com.example.product.domain.model.SellerMember;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request / response shapes of the seller-member surface (TASK-MONO-752; product-api.md § Seller members).
 * Grouped in one holder because they only make sense together.
 */
public final class SellerMemberDtos {

    private SellerMemberDtos() {
    }

    /** {@code POST /api/admin/sellers/{sellerId}/invitations}. */
    public record InviteRequest(
            @NotBlank(message = "email은 필수입니다")
            @Email(message = "email 형식이 올바르지 않습니다")
            @Size(max = 320, message = "email은 320자 이하여야 합니다")
            String email
    ) {
    }

    /** 201 of invite — the only place the raw token is ever returned. */
    public record InviteResponse(String invitationId, String email, Instant expiresAt, String token) {
        public static InviteResponse from(SellerMemberService.IssuedInvitation issued) {
            SellerInvitation i = issued.invitation();
            return new InviteResponse(i.getId(), i.getEmail(), i.getExpiresAt(), issued.token());
        }
    }

    /** {@code POST /api/seller-invitations/accept}. */
    public record AcceptRequest(
            @NotBlank(message = "token은 필수입니다")
            @Size(max = 128, message = "token이 너무 깁니다")
            String token
    ) {
    }

    public record MemberResponse(String sellerId, String accountId, String role, String status, Instant joinedAt) {
        public static MemberResponse from(SellerMember m) {
            return new MemberResponse(m.getSellerId(), m.getAccountId(), m.getRole().name(), m.getStatus().name(),
                    m.getJoinedAt());
        }
    }

    /** 200 of accept — what the person became. */
    public record AcceptResponse(String sellerId, String role, String status, Instant joinedAt) {
        public static AcceptResponse from(SellerMember m) {
            return new AcceptResponse(m.getSellerId(), m.getRole().name(), m.getStatus().name(), m.getJoinedAt());
        }
    }

    /** Invitation row of the operator read — never the token or its hash. */
    public record InvitationResponse(String invitationId, String email, String status, boolean expired,
                                     Instant expiresAt, Instant createdAt, Instant acceptedAt) {
        public static InvitationResponse from(SellerInvitation i, Instant now) {
            boolean expired = i.getStatus() == SellerInvitationStatus.PENDING && i.isExpired(now);
            return new InvitationResponse(i.getId(), i.getEmail(), i.getStatus().name(), expired, i.getExpiresAt(),
                    i.getCreatedAt(), i.getAcceptedAt());
        }
    }

    /** {@code GET /api/admin/sellers/{sellerId}/members}. */
    public record MembersResponse(List<MemberResponse> members, List<InvitationResponse> invitations) {
        public static MembersResponse from(SellerMemberService.SellerMembersView view) {
            return new MembersResponse(
                    view.members().stream().map(MemberResponse::from).toList(),
                    view.invitations().stream().map(i -> InvitationResponse.from(i, view.asOf())).toList());
        }
    }
}
