package com.example.auth.domain.service;

import java.util.UUID;

/**
 * Abstraction for parsing and validating access tokens.
 * Implementation may throw runtime exceptions (e.g. JwtException) on invalid/expired token.
 */
public interface TokenParser {

    ParsedToken parse(String token);

    default UUID parseUserId(String token) {
        return parse(token).userId();
    }

    default String parseEmail(String token) {
        return parse(token).email();
    }
}
