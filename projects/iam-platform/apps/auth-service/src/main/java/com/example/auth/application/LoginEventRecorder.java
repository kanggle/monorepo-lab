package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.session.SessionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-599 — the login EVENTS of {@link LoginUseCase}, for the SAS form-login path.
 * TASK-BE-602 — also used by the social-login callback ({@link OAuthLoginUseCase}); the caller
 * rules below (telemetry, catch outside the proxy) apply there unchanged.
 *
 * <p>Before this class the only producer of {@code auth.login.attempted/failed/succeeded} was
 * {@link LoginUseCase}, which has had no caller since TASK-BE-398. The browser form login
 * ({@code CredentialAuthenticationProvider}) emitted none of them, so security-service's
 * VELOCITY / DEVICE_CHANGE / GEO_ANOMALY rules never received input.
 *
 * <p><b>Scope (owner decision, TASK-BE-599 AC-0, revised 2026-09-25).</b> ⓐ login events
 * ONLY. Deliberately NOT carried over from {@link LoginUseCase}:
 * <ul>
 *   <li>ⓑ the {@code auth.login.max-failure-count} rate limit — there is no failure counter
 *       here and {@code failCount} is always {@code 0} (security-service's VelocityRule keeps
 *       its own counter and does not read the field);</li>
 *   <li>ⓒ device-session registration and {@code auth.session.created}. The browser form
 *       sends no device fingerprint, so under device-session.md D3 every login would have been
 *       a brand-new device ({@code isNewDevice=true} on every login → DEVICE_CHANGE firing on
 *       every login). Revisit when a stable device-identifying cookie exists. Until then
 *       {@code auth.login.succeeded} carries {@code deviceId=null, isNewDevice=null} — the
 *       contract's "unknown (legacy)" value, on which DeviceChangeRule falls back to the
 *       fingerprint comparison and, with no fingerprint, does not fire.</li>
 * </ul>
 *
 * <p><b>Transactions.</b> Each method is its own transaction, opened here (the form-login
 * provider has none). Callers on the login path must treat every method as telemetry: catch
 * and log, never let it fail the login. The catch has to sit in the CALLER, outside this
 * proxy — catching inside a {@code @Transactional} method would leave the transaction
 * rollback-only and turn the swallowed error into an {@code UnexpectedRollbackException}
 * at commit.
 */
@Service
@RequiredArgsConstructor
public class LoginEventRecorder {

    /** {@code auth.login.failed.failCount} on a path with no auth-service failure counter. */
    static final int NO_FAILURE_COUNTER = 0;

    private final AuthEventPublisher authEventPublisher;

    /**
     * {@code auth.login.attempted}.
     *
     * @param accountId    the resolved account, or {@code null} when the email matched no single
     *                     credential
     * @param tenantId     the account's own tenant when resolved; otherwise the login's tenant
     *                     context (initiating client, or the default)
     * @param siteTenantId TASK-BE-653 — the site a consumer-pool account logged in to
     *                     ({@link com.example.auth.domain.tenant.TenantContext#siteTenantOfPoolLogin});
     *                     {@code null} for every other login (the field is then omitted)
     */
    @Transactional
    public void recordAttempted(String accountId, String emailHash, String tenantId,
                                String siteTenantId, SessionContext ctx) {
        authEventPublisher.publishLoginAttempted(accountId, emailHash, tenantId, siteTenantId, ctx);
    }

    /**
     * {@code auth.login.failed}. {@code accountId} MUST be filled whenever the credential was
     * found (e.g. a wrong password) — VelocityRule ignores failures without one.
     * {@code siteTenantId}: as {@link #recordAttempted}.
     */
    @Transactional
    public void recordFailed(String accountId, String emailHash, String tenantId,
                             String siteTenantId, String failureReason, SessionContext ctx) {
        authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId, siteTenantId,
                failureReason, NO_FAILURE_COUNTER, ctx);
    }

    /**
     * {@code auth.login.succeeded} — no device session is registered on this path (see class
     * javadoc), so {@code deviceId} and {@code isNewDevice} are {@code null}. {@code sessionJti}
     * is {@code null} too: the refresh token does not exist yet when the password is verified
     * (SAS mints it later, at {@code /oauth2/token}). No {@code loginMethod} — omitted, which the
     * contract reads as {@code EMAIL_PASSWORD}. {@code siteTenantId}: as {@link #recordAttempted}.
     */
    @Transactional
    public void recordSucceeded(String accountId, String tenantId, String siteTenantId,
                                SessionContext ctx) {
        authEventPublisher.publishLoginSucceeded(accountId, null, tenantId, siteTenantId, ctx,
                null, null, null);
    }

    /**
     * TASK-BE-602 — {@code auth.login.succeeded} for the social-login callback. Same payload as
     * {@link #recordSucceeded(String, String, String, SessionContext)} plus {@code loginMethod}
     * ({@code OAUTH_<PROVIDER>}, auth-events.md). {@code tenantId} is the account's own tenant as
     * account-service reported it — never the initiating client's. {@code siteTenantId}: as
     * {@link #recordAttempted}.
     */
    @Transactional
    public void recordSucceeded(String accountId, String tenantId, String siteTenantId,
                                SessionContext ctx, String loginMethod) {
        authEventPublisher.publishLoginSucceeded(accountId, null, tenantId, siteTenantId, ctx,
                null, null, loginMethod);
    }
}
