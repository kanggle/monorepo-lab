package com.example.fanplatform.artist.adapter.in.web.dto.request;

import com.example.fanplatform.artist.domain.agency.AgencyStatus;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PATCH /api/agencies/{id}/status} — the only transition is
 * {@code ACTIVE → ARCHIVED}; {@code status: ACTIVE} is 422 (TASK-MONO-748).
 */
public record ChangeAgencyStatusRequest(
        @NotNull AgencyStatus status
) {}
