package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/artists/{id}/agency} and {@code PATCH /api/artist-groups/{id}/agency}
 * — {@code agencyId: null} (or an absent key) removes the affiliation (TASK-MONO-748).
 */
public record ChangeAgencyAffiliationRequest(
        @Size(min = 1, max = 36) String agencyId
) {}
