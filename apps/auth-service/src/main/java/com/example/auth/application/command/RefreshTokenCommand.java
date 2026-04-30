package com.example.auth.application.command;

import com.example.auth.domain.session.SessionContext;

public record RefreshTokenCommand(
        String refreshToken,
        SessionContext sessionContext
) {
}
