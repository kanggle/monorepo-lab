package com.example.fanplatform.community.application.exception;

/**
 * Thrown when a follow targets an account that artist-service does not confirm as
 * an artist account in this tenant (TASK-FAN-BE-045 AC-6).
 *
 * <p>Also thrown when artist-service cannot be reached: the check is fail-closed,
 * and an unreachable validator must refuse rather than admit. The caller cannot
 * distinguish "not an artist" from "could not ask", which is deliberate — a
 * validation that opens on error is indistinguishable from having no validation.
 */
public class UnknownArtistAccountException extends RuntimeException {

    public UnknownArtistAccountException(String artistAccountId) {
        super("Not a known artist account in this tenant: " + artistAccountId);
    }
}
