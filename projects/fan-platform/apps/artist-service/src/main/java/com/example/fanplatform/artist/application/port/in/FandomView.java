package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.domain.fandom.Fandom;

import java.time.Instant;
import java.time.LocalDate;

public record FandomView(
        String artistId,
        String tenantId,
        String fandomName,
        String colorHex,
        LocalDate foundedAt,
        String slogan,
        Instant createdAt,
        Instant updatedAt
) {

    public static FandomView from(Fandom f) {
        return new FandomView(
                f.getArtistId().value(),
                f.getTenantId(),
                f.getFandomName(),
                f.getColorHex(),
                f.getFoundedAt(),
                f.getSlogan(),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}
