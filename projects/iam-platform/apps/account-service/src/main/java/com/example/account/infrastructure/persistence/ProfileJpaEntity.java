package com.example.account.infrastructure.persistence;

import com.example.account.domain.profile.Profile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "account_id", nullable = false, unique = true, length = 36)
    private String accountId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 10, nullable = false)
    private String locale;

    @Column(length = 50, nullable = false)
    private String timezone;

    @Column(columnDefinition = "JSON")
    private String preferences;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "masked_at")
    private Instant maskedAt;

    public static ProfileJpaEntity fromDomain(Profile profile) {
        return fromDomain(profile, "fan-platform");
    }

    /**
     * TASK-BE-231: overload that accepts an explicit tenantId for provisioned accounts.
     * The single-arg variant retains {@code "fan-platform"} for backward compatibility
     * with existing self-service signup flows.
     */
    public static ProfileJpaEntity fromDomain(Profile profile, String tenantId) {
        ProfileJpaEntity entity = new ProfileJpaEntity();
        entity.id = profile.getId();
        entity.tenantId = tenantId;
        entity.accountId = profile.getAccountId();
        entity.displayName = profile.getDisplayName();
        entity.phoneNumber = profile.getPhoneNumber();
        entity.birthDate = profile.getBirthDate();
        entity.locale = profile.getLocale();
        entity.timezone = profile.getTimezone();
        entity.preferences = profile.getPreferences();
        entity.updatedAt = profile.getUpdatedAt();
        entity.maskedAt = profile.getMaskedAt();
        return entity;
    }

    public Profile toDomain() {
        return Profile.reconstitute(id, accountId, displayName, phoneNumber, birthDate,
                locale, timezone, preferences, updatedAt, maskedAt);
    }
}
