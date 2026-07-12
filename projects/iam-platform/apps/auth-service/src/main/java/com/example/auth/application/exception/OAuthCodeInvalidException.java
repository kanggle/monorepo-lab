package com.example.auth.application.exception;

/**
 * Thrown when the provider <i>rejects the authorization code itself</i> during token
 * exchange — OAuth2 {@code invalid_grant}: the code is expired, already redeemed
 * (they are single-use), issued to another client, or forged.
 *
 * <p>This is a <b>client</b> error, not a provider outage, and it surfaces as
 * {@code 401 INVALID_CODE} (TASK-MONO-350). Before that task the adapters flattened
 * every failure — including this one — into {@link OAuthProviderException}, so a user
 * merely re-opening a stale callback URL produced a <b>502</b>. Two costs followed:
 * 5xx-keyed alerting paged an operator for a user's mistake, and retry-on-5xx clients
 * retried a single-use, expired code that could never succeed.
 *
 * <p><b>Deliberately a subclass of {@link OAuthProviderException}.</b> Every adapter
 * guards its exchange with {@code catch (OAuthProviderException e) { throw e; }} ahead
 * of a broad {@code catch (Exception e)} that converts anything else <i>back</i> into
 * an {@code OAuthProviderException}. A sibling type would slip past that guard and be
 * flattened into a 502 again — reintroducing the exact bug this class exists to fix.
 * Subclassing makes the re-throw guards carry it through unchanged, and Spring still
 * routes it to the more specific {@code @ExceptionHandler}.
 */
public class OAuthCodeInvalidException extends OAuthProviderException {

    public OAuthCodeInvalidException(String message) {
        super(message);
    }

    public OAuthCodeInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
