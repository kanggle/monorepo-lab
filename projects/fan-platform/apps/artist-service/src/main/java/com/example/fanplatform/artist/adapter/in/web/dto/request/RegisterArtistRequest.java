package com.example.fanplatform.artist.adapter.in.web.dto.request;

import com.example.fanplatform.artist.domain.artist.ArtistType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterArtistRequest(
        @NotNull ArtistType artistType,
        @NotBlank @Size(max = 120) String stageName,
        @Size(max = 120) String realName,
        LocalDate debutDate,
        @Size(max = 120) String agency,
        @Size(max = 4000) String bio,
        @Size(max = 500) String profileImageRef
) {}
