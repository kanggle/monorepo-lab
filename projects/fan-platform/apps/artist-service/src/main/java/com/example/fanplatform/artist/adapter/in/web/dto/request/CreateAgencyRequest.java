package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/agencies} — artist-api.md § Agencies (TASK-MONO-748). */
public record CreateAgencyRequest(
        @NotBlank @Size(max = 120) String name
) {}
