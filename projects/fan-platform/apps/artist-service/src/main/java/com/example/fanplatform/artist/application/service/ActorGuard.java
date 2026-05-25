package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.domain.artist.ArtistId;

/**
 * Static guards shared by the artist application services.
 *
 * <p>Extracted to eliminate the 3-copy {@code requireAdmin} / 2-copy
 * {@code parseArtistId} duplication across {@link ArtistManagementService},
 * {@link ArtistGroupService}, and {@link FandomService}
 * (TASK-FAN-BE-008 L6 de-duplication).
 */
final class ActorGuard {

    private ActorGuard() {
    }

    /**
     * Throws {@link AdminRoleRequiredException} (HTTP 403) when the actor is
     * {@code null} or does not hold an admin-tier role.
     */
    static void requireAdmin(ActorContext actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AdminRoleRequiredException();
        }
    }

    /**
     * Parses {@code rawId} into an {@link ArtistId}, re-throwing a
     * {@link ArtistNotFoundException} (HTTP 404) on format error so the caller
     * never leaks the invalid identifier.
     */
    static ArtistId parseArtistId(String rawId) {
        try {
            return ArtistId.of(rawId);
        } catch (IllegalArgumentException e) {
            throw new ArtistNotFoundException(rawId);
        }
    }
}
