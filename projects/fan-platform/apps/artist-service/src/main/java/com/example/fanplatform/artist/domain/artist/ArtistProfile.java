package com.example.fanplatform.artist.domain.artist;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Value object holding the mutable display fields of an artist. Trimmed at
 * factory; full validation lives here so the application service can build a
 * profile and trust it.
 */
public record ArtistProfile(
        String stageName,
        String realName,
        LocalDate debutDate,
        String agency,
        String bio,
        String profileImageRef
) {

    private static final int STAGE_NAME_MAX = 120;
    private static final int REAL_NAME_MAX = 120;
    private static final int AGENCY_MAX = 120;
    private static final int BIO_MAX = 4000;
    private static final int PROFILE_IMAGE_REF_MAX = 500;

    public ArtistProfile {
        Objects.requireNonNull(stageName, "stageName");
        if (stageName.isBlank()) {
            throw new IllegalArgumentException("stageName must not be blank");
        }
        if (stageName.length() > STAGE_NAME_MAX) {
            throw new IllegalArgumentException("stageName exceeds " + STAGE_NAME_MAX + " chars");
        }
        if (realName != null && realName.length() > REAL_NAME_MAX) {
            throw new IllegalArgumentException("realName exceeds " + REAL_NAME_MAX + " chars");
        }
        if (agency != null && agency.length() > AGENCY_MAX) {
            throw new IllegalArgumentException("agency exceeds " + AGENCY_MAX + " chars");
        }
        if (bio != null && bio.length() > BIO_MAX) {
            throw new IllegalArgumentException("bio exceeds " + BIO_MAX + " chars");
        }
        if (profileImageRef != null && profileImageRef.length() > PROFILE_IMAGE_REF_MAX) {
            throw new IllegalArgumentException("profileImageRef exceeds " + PROFILE_IMAGE_REF_MAX + " chars");
        }
    }

    public ArtistProfile withStageName(String newStageName) {
        return new ArtistProfile(newStageName, realName, debutDate, agency, bio, profileImageRef);
    }

    public ArtistProfile withRealName(String newRealName) {
        return new ArtistProfile(stageName, newRealName, debutDate, agency, bio, profileImageRef);
    }

    public ArtistProfile withDebutDate(LocalDate newDebutDate) {
        return new ArtistProfile(stageName, realName, newDebutDate, agency, bio, profileImageRef);
    }

    public ArtistProfile withAgency(String newAgency) {
        return new ArtistProfile(stageName, realName, debutDate, newAgency, bio, profileImageRef);
    }

    public ArtistProfile withBio(String newBio) {
        return new ArtistProfile(stageName, realName, debutDate, agency, newBio, profileImageRef);
    }

    public ArtistProfile withProfileImageRef(String newRef) {
        return new ArtistProfile(stageName, realName, debutDate, agency, bio, newRef);
    }
}
