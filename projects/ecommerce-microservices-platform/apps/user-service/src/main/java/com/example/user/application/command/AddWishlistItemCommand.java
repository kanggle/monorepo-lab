package com.example.user.application.command;

import java.util.UUID;

public record AddWishlistItemCommand(
        UUID userId,
        UUID productId
) {
}
