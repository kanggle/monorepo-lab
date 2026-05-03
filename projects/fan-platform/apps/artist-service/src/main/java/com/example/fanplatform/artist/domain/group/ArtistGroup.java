package com.example.fanplatform.artist.domain.group;

import com.example.fanplatform.artist.domain.artist.ArtistId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate root for an artist group (e.g. a K-pop group with N members).
 *
 * <p>Membership is modelled as a separate aggregate-attached value object
 * collection — {@link GroupMembership} — but membership rows live in their own
 * table. Add/remove operations on this aggregate root validate inputs and
 * delegate the persisted state to the application service / repository port.
 */
public final class ArtistGroup {

    private static final int NAME_MAX = 120;
    private static final int AGENCY_MAX = 120;
    private static final int IMAGE_REF_MAX = 500;

    private final ArtistGroupId id;
    private final String tenantId;
    private String name;
    private LocalDate debutDate;
    private String agency;
    private String profileImageRef;
    private ArtistGroupStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private long version;

    private ArtistGroup(ArtistGroupId id, String tenantId, String name,
                        LocalDate debutDate, String agency, String profileImageRef,
                        ArtistGroupStatus status, Instant createdAt, Instant updatedAt,
                        Instant archivedAt, long version) {
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

    public static ArtistGroup create(ArtistGroupId id, String tenantId,
                                     String name, LocalDate debutDate,
                                     String agency, String profileImageRef) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        validateName(name);
        if (agency != null && agency.length() > AGENCY_MAX) {
            throw new IllegalArgumentException("agency exceeds " + AGENCY_MAX + " chars");
        }
        if (profileImageRef != null && profileImageRef.length() > IMAGE_REF_MAX) {
            throw new IllegalArgumentException("profileImageRef exceeds " + IMAGE_REF_MAX + " chars");
        }
        Instant now = Instant.now();
        return new ArtistGroup(id, tenantId, name, debutDate, agency, profileImageRef,
                ArtistGroupStatus.ACTIVE, now, now, null, 0L);
    }

    public static ArtistGroup reconstitute(ArtistGroupId id, String tenantId,
                                           String name, LocalDate debutDate,
                                           String agency, String profileImageRef,
                                           ArtistGroupStatus status,
                                           Instant createdAt, Instant updatedAt,
                                           Instant archivedAt, long version) {
        return new ArtistGroup(id, tenantId, name, debutDate, agency, profileImageRef,
                status, createdAt, updatedAt, archivedAt, version);
    }

    public void rename(String newName) {
        if (status == ArtistGroupStatus.ARCHIVED) {
            throw new IllegalStateException("cannot rename an ARCHIVED group");
        }
        validateName(newName);
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        if (status == ArtistGroupStatus.ARCHIVED) {
            throw new IllegalStateException("group is already ARCHIVED");
        }
        this.status = ArtistGroupStatus.ARCHIVED;
        Instant now = Instant.now();
        this.archivedAt = now;
        this.updatedAt = now;
    }

    /**
     * Validates that the given artist may be added as an active member.
     * Returns the membership row to be persisted.
     */
    public GroupMembership prepareMembership(ArtistId artistId, GroupRole role) {
        if (status == ArtistGroupStatus.ARCHIVED) {
            throw new IllegalStateException("cannot add members to an ARCHIVED group");
        }
        Objects.requireNonNull(artistId, "artistId");
        Objects.requireNonNull(role, "role");
        if (role == GroupRole.FORMER_MEMBER) {
            throw new IllegalArgumentException(
                    "role must be LEADER or MEMBER when adding to a group");
        }
        return GroupMembership.join(this.id, artistId, this.tenantId, role);
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("group name must not be blank");
        }
        if (name.length() > NAME_MAX) {
            throw new IllegalArgumentException("group name exceeds " + NAME_MAX + " chars");
        }
    }

    public ArtistGroupId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public LocalDate getDebutDate() { return debutDate; }
    public String getAgency() { return agency; }
    public String getProfileImageRef() { return profileImageRef; }
    public ArtistGroupStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version; }
}
