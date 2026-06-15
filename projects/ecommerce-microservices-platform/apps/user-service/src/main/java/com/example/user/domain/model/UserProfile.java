package com.example.user.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class UserProfile {

    private UUID id;
    private UUID userId;
    private Email email;
    private String name;
    private String nickname;
    private String phone;
    private String profileImageUrl;
    private ProfileStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private UserProfile() {
    }

    public static UserProfile create(UUID userId, String email, String name) {
        validateUserId(userId);
        Email validatedEmail = Email.of(email);

        UserProfile profile = new UserProfile();
        profile.id = UUID.randomUUID();
        profile.userId = userId;
        profile.email = validatedEmail;
        profile.name = (name == null || name.isBlank()) ? "" : name.trim();
        profile.status = ProfileStatus.ACTIVE;
        Instant now = Instant.now();
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    /**
     * Minimal profile born from an IAM {@code account.created} lifecycle event
     * (ADR-MONO-037 P1). The event carries only {@code accountId} (+ a masked
     * {@code emailHash}) — no raw email or name — so the profile is created with
     * {@code email}/{@code name} unset. They are populated lazily from the OIDC
     * id_token (profile/email scopes) at first login or via the profile-update
     * flow; the system never depends on them being non-null (ADR-MONO-037 P5/P6).
     */
    public static UserProfile createMinimal(UUID userId) {
        validateUserId(userId);

        UserProfile profile = new UserProfile();
        profile.id = UUID.randomUUID();
        profile.userId = userId;
        profile.email = null;
        profile.name = null;
        profile.status = ProfileStatus.ACTIVE;
        Instant now = Instant.now();
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    public static UserProfile reconstitute(UUID id, UUID userId, String email, String name,
                                            String nickname, String phone, String profileImageUrl,
                                            ProfileStatus status, Instant createdAt, Instant updatedAt) {
        UserProfile profile = new UserProfile();
        profile.id = id;
        profile.userId = userId;
        profile.email = (email == null) ? null : new Email(email);
        profile.name = name;
        profile.nickname = nickname;
        profile.phone = phone;
        profile.profileImageUrl = profileImageUrl;
        profile.status = status;
        profile.createdAt = createdAt;
        profile.updatedAt = updatedAt;
        return profile;
    }

    public void updateNickname(String nickname) {
        this.nickname = validateAndTrim(nickname, 50, "Nickname");
        this.updatedAt = Instant.now();
    }

    public void updatePhone(String phone) {
        this.phone = validateAndTrim(phone, 20, "Phone");
        this.updatedAt = Instant.now();
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = validateAndTrim(profileImageUrl, 500, "Profile image URL");
        this.updatedAt = Instant.now();
    }

    private static String validateAndTrim(String value, int maxLength, String fieldName) {
        if (value != null && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value == null ? null : value.trim();
    }

    public void withdraw() {
        this.status = ProfileStatus.WITHDRAWN;
        this.updatedAt = Instant.now();
    }

    public boolean isWithdrawn() {
        return this.status == ProfileStatus.WITHDRAWN;
    }

    /**
     * Anonymize the profile's PII in reaction to an IAM {@code account.deleted}
     * with {@code anonymized=true} (post-grace, ADR-MONO-037 P2/P3, aligning to the
     * IAM consumer obligation TASK-BE-258). All identity-bearing fields are cleared;
     * {@code userId} is preserved as the FK for order/audit integrity. The terminal
     * status is WITHDRAWN. Idempotent: re-applying clears already-null fields.
     *
     * <p>Scope boundary (ADR-MONO-037 P3): this anonymizes user-service-held profile
     * PII only. Order-service-held PII (shipping addresses, recipient names) is a
     * documented deferred follow-up, not cascaded here.
     */
    public void anonymize() {
        this.email = null;
        this.name = null;
        this.nickname = null;
        this.phone = null;
        this.profileImageUrl = null;
        this.status = ProfileStatus.WITHDRAWN;
        this.updatedAt = Instant.now();
    }

    private static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
    }
}
