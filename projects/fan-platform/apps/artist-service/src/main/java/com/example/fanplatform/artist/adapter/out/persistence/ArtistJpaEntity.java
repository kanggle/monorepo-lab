package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity for the {@code artists} table. Package-private — only the
 * persistence adapter touches this class. Domain code uses
 * {@link com.example.fanplatform.artist.domain.artist.Artist}.
 *
 * <p>Indexes + unique constraints are defined in the Flyway migration; we do
 * NOT redeclare them here so that DB and entity stay decoupled (Flyway is the
 * single source of truth for the schema).
 */
@Entity
@Table(name = "artists")
class ArtistJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artist_type", length = 20, nullable = false, updatable = false)
    private ArtistType artistType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ArtistStatus status;

    @Column(name = "stage_name", length = 120, nullable = false)
    private String stageName;

    @Column(name = "real_name", length = 120)
    private String realName;

    @Column(name = "debut_date")
    private LocalDate debutDate;

    @Column(name = "agency", length = 120)
    private String agency;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "profile_image_ref", length = 500)
    private String profileImageRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ArtistJpaEntity() {
        // JPA-required no-arg constructor
    }

    ArtistJpaEntity(String id, String tenantId, ArtistType artistType, ArtistStatus status,
                    String stageName, String realName, LocalDate debutDate, String agency,
                    String bio, String profileImageRef, Instant createdAt, Instant updatedAt,
                    Instant publishedAt, Instant archivedAt, Long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.artistType = artistType;
        this.status = status;
        this.stageName = stageName;
        this.realName = realName;
        this.debutDate = debutDate;
        this.agency = agency;
        this.bio = bio;
        this.profileImageRef = profileImageRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
        this.archivedAt = archivedAt;
        this.version = version;
    }

    String getId() { return id; }
    String getTenantId() { return tenantId; }
    ArtistType getArtistType() { return artistType; }
    ArtistStatus getStatus() { return status; }
    String getStageName() { return stageName; }
    String getRealName() { return realName; }
    LocalDate getDebutDate() { return debutDate; }
    String getAgency() { return agency; }
    String getBio() { return bio; }
    String getProfileImageRef() { return profileImageRef; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Instant getPublishedAt() { return publishedAt; }
    Instant getArchivedAt() { return archivedAt; }
    Long getVersion() { return version; }

    void applyMutable(ArtistStatus status, String stageName, String realName,
                      LocalDate debutDate, String agency, String bio, String profileImageRef,
                      Instant updatedAt, Instant publishedAt, Instant archivedAt) {
        this.status = status;
        this.stageName = stageName;
        this.realName = realName;
        this.debutDate = debutDate;
        this.agency = agency;
        this.bio = bio;
        this.profileImageRef = profileImageRef;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
        this.archivedAt = archivedAt;
    }
}
