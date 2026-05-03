package com.example.fanplatform.artist.domain.artist;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for an artist record. Framework-free POJO — JPA mapping lives
 * on the adapter-side {@code ArtistJpaEntity}.
 *
 * <p>Status transitions are encapsulated in this class — direct mutation of
 * {@link ArtistStatus} from outside is impossible. Allowed transitions:
 * <pre>
 *   DRAFT     → PUBLISHED   (publish())
 *   DRAFT     → ARCHIVED    (archive())
 *   PUBLISHED → ARCHIVED    (archive())
 * </pre>
 *
 * <p>The factory {@link #register(ArtistId, String, ArtistType, ArtistProfile)}
 * creates the artist in {@code DRAFT} state. The application service then
 * decides whether to publish immediately (admin REST flow).
 */
public final class Artist {

    private final ArtistId id;
    private final String tenantId;
    private final ArtistType artistType;
    private ArtistStatus status;
    private ArtistProfile profile;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;
    private Instant archivedAt;
    private long version;

    private Artist(ArtistId id,
                   String tenantId,
                   ArtistType artistType,
                   ArtistStatus status,
                   ArtistProfile profile,
                   Instant createdAt,
                   Instant updatedAt,
                   Instant publishedAt,
                   Instant archivedAt,
                   long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.artistType = artistType;
        this.status = status;
        this.profile = profile;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
        this.archivedAt = archivedAt;
        this.version = version;
    }

    /** Factory used by the registration use case — always begins in DRAFT. */
    public static Artist register(ArtistId id,
                                  String tenantId,
                                  ArtistType artistType,
                                  ArtistProfile profile) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(artistType, "artistType");
        Objects.requireNonNull(profile, "profile");
        Instant now = Instant.now();
        return new Artist(id, tenantId, artistType, ArtistStatus.DRAFT, profile, now, now, null, null, 0L);
    }

    /** Repository-side reconstitution — bypasses invariants intentionally. */
    public static Artist reconstitute(ArtistId id,
                                      String tenantId,
                                      ArtistType artistType,
                                      ArtistStatus status,
                                      ArtistProfile profile,
                                      Instant createdAt,
                                      Instant updatedAt,
                                      Instant publishedAt,
                                      Instant archivedAt,
                                      long version) {
        return new Artist(id, tenantId, artistType, status, profile, createdAt, updatedAt,
                publishedAt, archivedAt, version);
    }

    public void publish() {
        if (status != ArtistStatus.DRAFT) {
            throw new IllegalStateTransitionException(status, ArtistStatus.PUBLISHED);
        }
        this.status = ArtistStatus.PUBLISHED;
        Instant now = Instant.now();
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void archive() {
        if (status == ArtistStatus.ARCHIVED) {
            throw new IllegalStateTransitionException(status, ArtistStatus.ARCHIVED);
        }
        this.status = ArtistStatus.ARCHIVED;
        Instant now = Instant.now();
        this.archivedAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(ArtistProfile newProfile) {
        Objects.requireNonNull(newProfile, "newProfile");
        if (status == ArtistStatus.ARCHIVED) {
            throw new IllegalStateException("cannot update an ARCHIVED artist");
        }
        this.profile = newProfile;
        this.updatedAt = Instant.now();
    }

    /** True when the record is visible to ordinary (non-admin) callers. */
    public boolean isPublished() {
        return status == ArtistStatus.PUBLISHED;
    }

    public ArtistId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public ArtistType getArtistType() { return artistType; }
    public ArtistStatus getStatus() { return status; }
    public ArtistProfile getProfile() { return profile; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version; }

    /** Thrown when a status transition is not in the allowed matrix. */
    public static final class IllegalStateTransitionException extends RuntimeException {
        private final ArtistStatus from;
        private final ArtistStatus to;

        public IllegalStateTransitionException(ArtistStatus from, ArtistStatus to) {
            super("Forbidden artist status transition: " + from + " -> " + to);
            this.from = from;
            this.to = to;
        }

        public ArtistStatus from() { return from; }
        public ArtistStatus to() { return to; }
    }
}
