package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.domain.group.ArtistGroupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "artist_groups")
class ArtistGroupJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "debut_date")
    private LocalDate debutDate;

    @Column(name = "agency", length = 120)
    private String agency;

    /**
     * Agency affiliation (V4 — TASK-MONO-748). Nullable; composite FK
     * {@code (tenant_id, agency_id) → agencies (tenant_id, id)} in the migration.
     */
    @Column(name = "agency_id", length = 36)
    private String agencyId;

    @Column(name = "profile_image_ref", length = 500)
    private String profileImageRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ArtistGroupStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ArtistGroupJpaEntity() {}

    ArtistGroupJpaEntity(String id, String tenantId, String name, LocalDate debutDate,
                         String agency, String profileImageRef, ArtistGroupStatus status,
                         Instant createdAt, Instant updatedAt, Instant archivedAt, Long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.debutDate = debutDate;
        this.agency = agency;
        this.profileImageRef = profileImageRef;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
        this.version = version;
    }

    String getId() { return id; }
    String getTenantId() { return tenantId; }
    String getName() { return name; }
    LocalDate getDebutDate() { return debutDate; }
    String getAgency() { return agency; }
    String getProfileImageRef() { return profileImageRef; }
    ArtistGroupStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Instant getArchivedAt() { return archivedAt; }
    Long getVersion() { return version; }
    String getAgencyId() { return agencyId; }

    void setAgencyId(String agencyId) { this.agencyId = agencyId; }

    void applyMutable(String name, LocalDate debutDate, String agency, String profileImageRef,
                      ArtistGroupStatus status, Instant updatedAt, Instant archivedAt) {
        this.name = name;
        this.debutDate = debutDate;
        this.agency = agency;
        this.profileImageRef = profileImageRef;
        this.status = status;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
    }
}
