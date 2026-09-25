package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.session.SessionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TASK-BE-599 — the login side effects of {@link LoginUseCase}, minus its rate limit, for the
 * SAS form-login path.
 *
 * <p>Before this class the only producer of {@code auth.login.attempted/failed/succeeded} and
 * {@code auth.session.created} was {@link LoginUseCase}, which has had no caller since
 * TASK-BE-398. The browser form login ({@code CredentialAuthenticationProvider}) emitted none
 * of them, so security-service's VELOCITY / DEVICE_CHANGE / GEO_ANOMALY rules never received
 * input.
 *
 * <p><b>Scope (owner decision, TASK-BE-599 AC-0).</b> ⓐ login events + ⓒ device session.
 * ⓑ the {@code auth.login.max-failure-count} rate limit is deliberately NOT applied — so there
 * is no failure counter here and {@code failCount} is always {@code 0} on this path
 * (security-service's VelocityRule keeps its own counter and does not read the field).
 *
 * <p><b>Transactions.</b> Each method is its own transaction, opened here (the form-login
 * provider has none). {@link #recordSucceeded} runs the device-session upsert (which is
 * {@code MANDATORY}-propagation) and both events in ONE transaction, so a failure rolls all of
 * it back together. Callers on the login path must treat every method as telemetry: catch and
 * log, never let it fail the login. The catch has to sit in the CALLER, outside this proxy —
 * catching inside a {@code @Transactional} method would leave the transaction rollback-only
 * and turn the swallowed error into an {@code UnexpectedRollbackException} at commit.
 */
@Service
@RequiredArgsConstructor
public class LoginEventRecorder {

    /** {@code auth.login.failed.failCount} on a path with no auth-service failure counter. */
    static final int NO_FAILURE_COUNTER = 0;

    private final AuthEventPublisher authEventPublisher;
    private final RegisterOrUpdateDeviceSessionUseCase registerOrUpdateDeviceSessionUseCase;

    /**
     * {@code auth.login.attempted}.
     *
     * @param accountId the resolved account, or {@code null} when the email matched no single
     *                  credential
     * @param tenantId  the account's own tenant when resolved; otherwise the login's tenant
     *                  context (initiating client, or the default)
     */
    @Transactional
    public void recordAttempted(String accountId, String emailHash, String tenantId,
                                SessionContext ctx) {
        authEventPublisher.publishLoginAttempted(accountId, emailHash, tenantId, ctx);
    }

    /**
     * {@code auth.login.failed}. {@code accountId} MUST be filled whenever the credential was
     * found (e.g. a wrong password) — VelocityRule ignores failures without one.
     */
    @Transactional
    public void recordFailed(String accountId, String emailHash, String tenantId,
                             String failureReason, SessionContext ctx) {
        authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId,
                failureReason, NO_FAILURE_COUNTER, ctx);
    }

    /**
     * Registers (or touches) the device session, then publishes {@code auth.login.succeeded}
     * with {@code deviceId}/{@code isNewDevice} and — for a newly created session —
     * {@code auth.session.created}.
     *
     * <p>{@code sessionJti} is {@code null} in both events: on the form-login path the refresh
     * token does not exist yet when the password is verified (SAS mints it later, at
     * {@code /oauth2/token}).
     */
    @Transactional
    public RegisterDeviceSessionResult recordSucceeded(String accountId, String tenantId,
                                                       SessionContext ctx) {
        RegisterDeviceSessionResult session =
                registerOrUpdateDeviceSessionUseCase.execute(accountId, tenantId, ctx);

        authEventPublisher.publishLoginSucceeded(accountId, null, tenantId, ctx,
                session.deviceId(), session.newSession());
        if (session.newSession()) {
            authEventPublisher.publishAuthSessionCreated(
                    accountId, tenantId, session.deviceId(), null,
                    LoginHashes.fingerprintHash(ctx.deviceFingerprint()),
                    ctx.userAgentFamily(),
                    ctx.ipMasked(),
                    ctx.resolvedGeoCountry(),
                    Instant.now(),
                    session.evictedDeviceIds());
        }
        return session;
    }
}
