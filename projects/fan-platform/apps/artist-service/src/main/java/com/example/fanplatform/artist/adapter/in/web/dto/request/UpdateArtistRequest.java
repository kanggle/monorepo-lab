package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PATCH semantics: every field is nullable. {@code null} means "do not change".
 * Bean Validation only checks lengths when the caller actually supplies a value.
 */
public record UpdateArtistRequest(
        @Size(max = 120) String stageName,
        @Size(max = 120) String realName,
        LocalDate debutDate,
        @Size(max = 120) String agency,
        @Size(max = 4000) String bio,
        @Size(max = 500) String profileImageRef
) {}
