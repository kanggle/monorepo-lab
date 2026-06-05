package com.example.auth.infrastructure.oauth2.persistence;

/**
 * Thrown when {@link OAuthClientMapper} cannot convert between
 * {@link OAuthClientEntity} and SAS {@link org.springframework.security.oauth2.server.authorization.client.RegisteredClient}.
 *
 * <p>TASK-BE-252.
 */
public class OAuthClientMappingException extends RuntimeException {

    public OAuthClientMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
