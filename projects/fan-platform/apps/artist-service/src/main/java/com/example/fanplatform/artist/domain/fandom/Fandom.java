package com.example.fanplatform.artist.domain.fandom;

import com.example.fanplatform.artist.domain.artist.ArtistId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fandom aggregate — 1:1 with {@link ArtistId}. The artist must be in
 * {@code PUBLISHED} status when the fandom is created (enforced by the
 * application service). v1 keeps the schema simple: name, color, founded date,
 * slogan.
 */
public final class Fandom {

    private static final int NAME_MAX = 120;
    private static final int SLOGAN_MAX = 200;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final ArtistId artistId;
    private final String tenantId;
    private String fandomName;
    private String colorHex;
    private LocalDate foundedAt;
    private String slogan;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Fandom(ArtistId artistId, String tenantId, String fandomName, String colorHex,
                   LocalDate foundedAt, String slogan, Instant createdAt, Instant updatedAt,
                   long version) {
        this.artistId = artistId;
        this.tenantId = tenantId;
        this.fandomName = fandomName;
        this.colorHex = colorHex;
        this.foundedAt = foundedAt;
        this.slogan = slogan;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Fandom create(ArtistId artistId, String tenantId,
                                String fandomName, String colorHex,
                                LocalDate foundedAt, String slogan) {
        Objects.requireNonNull(artistId, "artistId");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        validateName(fandomName);
        validateColor(colorHex);
        validateSlogan(slogan);
        Instant now = Instant.now();
        return new Fandom(artistId, tenantId, fandomName, colorHex, foundedAt, slogan,
                now, now, 0L);
    }

    public static Fandom reconstitute(ArtistId artistId, String tenantId,
                                      String fandomName, String colorHex,
                                      LocalDate foundedAt, String slogan,
                                      Instant createdAt, Instant updatedAt,
                                      long version) {
        return new Fandom(artistId, tenantId, fandomName, colorHex, foundedAt, slogan,
                createdAt, updatedAt, version);
    }

    public void update(String newName, String newColorHex, LocalDate newFoundedAt, String newSlogan) {
        validateName(newName);
        validateColor(newColorHex);
        validateSlogan(newSlogan);
        this.fandomName = newName;
        this.colorHex = newColorHex;
        this.foundedAt = newFoundedAt;
        this.slogan = newSlogan;
        this.updatedAt = Instant.now();
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "fandomName");
        if (name.isBlank()) {
            throw new IllegalArgumentException("fandomName must not be blank");
        }
        if (name.length() > NAME_MAX) {
            throw new IllegalArgumentException("fandomName exceeds " + NAME_MAX + " chars");
        }
    }

    private static void validateColor(String colorHex) {
        if (colorHex == null) return;
        if (!HEX_COLOR.matcher(colorHex).matches()) {
            throw new IllegalArgumentException("colorHex must match #RRGGBB");
        }
    }

    private static void validateSlogan(String slogan) {
        if (slogan != null && slogan.length() > SLOGAN_MAX) {
            throw new IllegalArgumentException("slogan exceeds " + SLOGAN_MAX + " chars");
        }
    }

    public ArtistId getArtistId() { return artistId; }
    public FandomId getId() { return FandomId.fromArtistId(artistId); }
    public String getTenantId() { return tenantId; }
    public String getFandomName() { return fandomName; }
    public String getColorHex() { return colorHex; }
    public LocalDate getFoundedAt() { return foundedAt; }
    public String getSlogan() { return slogan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
