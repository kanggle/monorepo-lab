package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;

import java.time.Instant;
import java.time.LocalDate;

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
        String agency,
        String bio,
        String profileImageRef,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant archivedAt
) {

    public static ArtistView from(Artist a) {
        return new ArtistView(
                a.getId().value(),
                a.getTenantId(),
                a.getAccountId(),
                a.getArtistType(),
                a.getStatus(),
                a.getProfile().stageName(),
                a.getProfile().realName(),
                a.getProfile().debutDate(),
                a.getProfile().agency(),
                a.getProfile().bio(),
                a.getProfile().profileImageRef(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getPublishedAt(),
                a.getArchivedAt()
        );
    }
}
