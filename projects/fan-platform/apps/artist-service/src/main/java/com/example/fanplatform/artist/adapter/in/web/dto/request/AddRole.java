package com.example.fanplatform.artist.adapter.in.web.dto.request;

import com.example.fanplatform.artist.domain.group.GroupRole;

/**
 * Restricted role enum accepted on {@code POST /api/artist-groups/{id}/members}.
 * Excludes {@link GroupRole#FORMER_MEMBER} so the controller boundary
 * deserializer rejects {@code "FORMER_MEMBER"} at Bean-Validation time —
 * the global handler then returns 422 {@code VALIDATION_ERROR} instead of
 * letting the bad role flow into the domain.
 */
public enum AddRole {

    LEADER,
    MEMBER;

    public GroupRole toDomain() {
        return switch (this) {
            case LEADER -> GroupRole.LEADER;
            case MEMBER -> GroupRole.MEMBER;
        };
    }
}
