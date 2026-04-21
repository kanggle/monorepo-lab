package com.example.user.application.command;

import java.util.UUID;

public record UpdateProfileCommand(
        UUID userId,
        String nickname,
        String phone,
        String profileImageUrl
) {}
