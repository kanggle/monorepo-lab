package com.example.admin.presentation.dto;

import com.example.admin.application.ReceivedInvitationsQueryUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-773 OD-7 — «받은 초대» (onboarding-api.md § received-invitations). 🔴 No token, hash, link, inviter or
 * delivery field exists on this shape (772 R4).
 */
public record ReceivedInvitationsResponse(String accountState, List<Item> items) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Item(String invitationId, String tenantId, String tenantDisplayName, List<String> roles,
                       boolean firstAdmin, Instant expiresAt, Instant invitedAt) {}

    public static ReceivedInvitationsResponse from(ReceivedInvitationsQueryUseCase.Result r) {
        return new ReceivedInvitationsResponse(r.accountState().name(), r.items().stream()
                .map(i -> new Item(i.invitationId(), i.tenantId(), i.tenantDisplayName(), i.roles(), i.firstAdmin(),
                        i.expiresAt(), i.invitedAt()))
                .toList());
    }
}
