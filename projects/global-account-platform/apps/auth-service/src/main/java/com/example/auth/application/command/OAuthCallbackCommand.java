package com.example.auth.application.command;

import com.example.auth.domain.session.SessionContext;

public record OAuthCallbackCommand(
        String provider,
        String code,
        String state,
        String redirectUri,
        SessionContext sessionContext
) {
}
