package com.example.fanplatform.artist.application.exception;

/**
 * Thrown when registering an artist against an account that already authors as
 * another artist in the same tenant. Surfaces as 409
 * {@code ARTIST_ACCOUNT_CONFLICT} per {@code specs/contracts/http/artist-api.md}.
 *
 * <p>Whether one person may hold several artist personas is a product question
 * nobody has decided; {@code uq_artists_tenant_account_id} takes the
 * conservative side, and relaxing it later drops a constraint rather than
 * breaking the contract.
 */
public class ArtistAccountConflictException extends RuntimeException {

    public ArtistAccountConflictException(String accountId) {
        super("Account already authors as an artist in tenant: " + accountId);
    }
}
