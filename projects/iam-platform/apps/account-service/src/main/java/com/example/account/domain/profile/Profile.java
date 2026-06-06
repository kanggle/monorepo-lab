package com.example.account.domain.profile;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    private Long id;
    private String accountId;
    private String displayName;
    private String phoneNumber;
    private LocalDate birthDate;
    private String locale;
    private String timezone;
    private String preferences;
    private Instant updatedAt;
    private Instant maskedAt;

    public static Profile create(String accountId, String displayName, String locale, String timezone) {
        Profile profile = new Profile();
        profile.accountId = accountId;
        profile.displayName = displayName;
        profile.locale = locale != null ? locale : "ko-KR";
        profile.timezone = timezone != null ? timezone : "Asia/Seoul";
        profile.updatedAt = Instant.now();
        return profile;
    }

    /**
     * Reconstitute a Profile from persisted state. Used by infrastructure mappers.
     */
    public static Profile reconstitute(Long id, String accountId, String displayName,
                                        String phoneNumber, LocalDate birthDate,
                                        String locale, String timezone,
                                        String preferences, Instant updatedAt,
                                        Instant maskedAt) {
        Profile profile = new Profile();
        profile.id = id;
        profile.accountId = accountId;
        profile.displayName = displayName;
        profile.phoneNumber = phoneNumber;
        profile.birthDate = birthDate;
        profile.locale = locale;
        profile.timezone = timezone;
        profile.preferences = preferences;
        profile.updatedAt = updatedAt;
        profile.maskedAt = maskedAt;
        return profile;
    }

    /**
     * GDPR PII masking per retention.md §2.5:
     * <ul>
     *   <li>displayName → fixed string {@code "탈퇴한 사용자"}</li>
     *   <li>phoneNumber, birthDate, preferences → NULL</li>
     *   <li>maskedAt stamped to the current instant</li>
     * </ul>
     */
    public void maskPii() {
        this.displayName = "탈퇴한 사용자";
        this.phoneNumber = null;
        this.birthDate = null;
        this.preferences = null;
        this.maskedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void update(String displayName, String phoneNumber, LocalDate birthDate,
                       String locale, String timezone, String preferences) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (locale != null) {
            this.locale = locale;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (preferences != null) {
            this.preferences = preferences;
        }
        this.updatedAt = Instant.now();
    }
}
