package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateFandomRequest(
        @NotBlank @Size(max = 120) String fandomName,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorHex must match #RRGGBB") String colorHex,
        LocalDate foundedAt,
        @Size(max = 200) String slogan
) {}
