package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateArtistGroupRequest(
        @NotBlank @Size(max = 120) String name,
        LocalDate debutDate,
        @Size(max = 120) String agency,
        @Size(max = 500) String profileImageRef
) {}
