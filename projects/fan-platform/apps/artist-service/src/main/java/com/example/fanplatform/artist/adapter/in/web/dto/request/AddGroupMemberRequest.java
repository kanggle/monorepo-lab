package com.example.fanplatform.artist.adapter.in.web.dto.request;

import com.example.fanplatform.artist.domain.group.GroupRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddGroupMemberRequest(
        @NotBlank String artistId,
        @NotNull GroupRole role
) {}
