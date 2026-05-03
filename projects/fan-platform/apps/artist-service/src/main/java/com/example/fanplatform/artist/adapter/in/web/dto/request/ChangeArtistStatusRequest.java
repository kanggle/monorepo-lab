package com.example.fanplatform.artist.adapter.in.web.dto.request;

import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeArtistStatusRequest(
        @NotNull ArtistStatus status,
        @Size(max = 200) String reason
) {}
