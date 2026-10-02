package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Read model returned by all artist-facing use cases. Mirrors the public
 * contract defined in {@code specs/contracts/http/artist-api.md}.
 */
public record ArtistView(
        String id,
        String tenantId,
        // Exposed on every read endpoint on purpose: it is the identifier fans
        // follow, and the web app must send it (not the artist entity id) or the
        // follow-target validation refuses the follow. artist-api.md § accountId.
        String accountId,
        ArtistType artistType,
        ArtistStatus status,
        String stageName,
        String realName,
        LocalDate debutDate,
        // Display name of the agency. Since TASK-MONO-748 (ADR-MONO-079 D1) it is read
        // from the agency ENTITY when agencyId is set; the legacy free-text column is the
        // fallback only for rows without one. Same key and meaning as before — readers
        // (fan web, public snapshot) see no change (AC-4).
        String agency,
        // The agency entity id (nullable — unaffiliated artist). artist-api.md § Agencies.
        String agencyId,
        String bio,
        String profileImageRef,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant archivedAt
) {

    /**
     * @param agencyNames {@code agencyId → name} for this artist's tenant (see
     *        {@code AgencyNames}); an affiliated artist whose id is missing from the map
     *        falls back to the free text rather than showing nothing.
     */
    public static ArtistView from(Artist a, Map<String, String> agencyNames) {
        String agencyId = a.getAgencyId() == null ? null : a.getAgencyId().value();
        String display = agencyId == null
                ? a.getProfile().agency()
                : agencyNames.getOrDefault(agencyId, a.getProfile().agency());
        return new ArtistView(
                a.getId().value(),
                a.getTenantId(),
                a.getAccountId(),
                a.getArtistType(),
                a.getStatus(),
                a.getProfile().stageName(),
                a.getProfile().realName(),
                a.getProfile().debutDate(),
                display,
                agencyId,
                a.getProfile().bio(),
                a.getProfile().profileImageRef(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getPublishedAt(),
                a.getArchivedAt()
        );
    }
}
