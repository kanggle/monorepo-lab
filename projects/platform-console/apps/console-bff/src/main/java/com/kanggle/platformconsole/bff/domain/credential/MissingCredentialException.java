package com.kanggle.platformconsole.bff.domain.credential;

/**
 * Thrown when a required outbound credential is absent at dispatch time.
 *
 * <p>This is a domain-layer exception (no Spring imports). The adapter layer
 * maps it to {@code 401 TOKEN_INVALID} per
 * {@code console-integration-contract.md} § 2.4.9 edge-case discipline
 * ("absent operator token on GAP leg = 401, not fallback to GAP OIDC token").
 */
public class MissingCredentialException extends RuntimeException {

    public MissingCredentialException(String message) {
        super(message);
    }
}
