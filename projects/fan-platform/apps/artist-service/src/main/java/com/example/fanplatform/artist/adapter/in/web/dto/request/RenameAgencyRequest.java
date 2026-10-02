package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PATCH /api/agencies/{id}} — rename (TASK-MONO-748). */
public record RenameAgencyRequest(
        @NotBlank @Size(max = 120) String name
) {}
