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

    public static UserProfile reconstitute(UUID id, UUID userId, String email, String name,
                                            String nickname, String phone, String profileImageUrl,
                                            ProfileStatus status, Instant createdAt, Instant updatedAt) {
        UserProfile profile = new UserProfile();
        profile.id = id;
        profile.userId = userId;
        profile.email = new Email(email);
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

    private static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
    }
}
