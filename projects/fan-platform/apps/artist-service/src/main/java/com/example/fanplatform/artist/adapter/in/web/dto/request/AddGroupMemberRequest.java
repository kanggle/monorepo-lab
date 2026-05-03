package com.example.fanplatform.artist.adapter.in.web.dto.request;

import com.example.fanplatform.artist.domain.group.GroupRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/artist-groups/{id}/members} body. The role uses the
 * restricted {@link AddRole} enum (LEADER | MEMBER) — sending {@code
 * FORMER_MEMBER} fails Jackson enum deserialization at the controller
 * boundary, which {@code GlobalExceptionHandler} maps to 422
 * {@code VALIDATION_ERROR}.
 */
public record AddGroupMemberRequest(
        @NotBlank String artistId,
        @NotNull AddRole role
) {

    public GroupRole toDomainRole() {
        return role.toDomain();
    }
}
