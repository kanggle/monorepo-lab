package com.example.fanplatform.artist.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fandoms")
class FandomJpaEntity {

    @Id
    @Column(name = "artist_id", length = 36, nullable = false, updatable = false)
    private String artistId;

    @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "fandom_name", length = 120, nullable = false)
    private String fandomName;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "founded_at")
    private LocalDate foundedAt;

    @Column(name = "slogan", length = 200)
    private String slogan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected FandomJpaEntity() {}

    FandomJpaEntity(String artistId, String tenantId, String fandomName, String colorHex,
                    LocalDate foundedAt, String slogan, Instant createdAt, Instant updatedAt,
                    Long version) {
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

    String getArtistId() { return artistId; }
    String getTenantId() { return tenantId; }
    String getFandomName() { return fandomName; }
    String getColorHex() { return colorHex; }
    LocalDate getFoundedAt() { return foundedAt; }
    String getSlogan() { return slogan; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Long getVersion() { return version; }

    void applyMutable(String fandomName, String colorHex, LocalDate foundedAt, String slogan,
                      Instant updatedAt) {
        this.fandomName = fandomName;
        this.colorHex = colorHex;
        this.foundedAt = foundedAt;
        this.slogan = slogan;
        this.updatedAt = updatedAt;
    }
}
