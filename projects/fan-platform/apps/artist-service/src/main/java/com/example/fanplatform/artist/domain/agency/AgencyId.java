package com.example.fanplatform.artist.domain.agency;

import java.util.Objects;

/** Strongly-typed wrapper around the agency id (VARCHAR(36)). TASK-MONO-748. */
public record AgencyId(String value) {

    public AgencyId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AgencyId must not be blank");
        }
    }

    public static AgencyId of(String value) {
        return new AgencyId(value);
    }
}
