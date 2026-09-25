package com.example.auth.application;

import com.example.auth.application.command.RevokeSessionsOnAccountLockedCommand;
import com.example.messaging.dedupe.EventDedupePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * TASK-BE-601 — when an account is locked, revoke its sessions.
 *
 * <p>Before this, a lock (operator lock or security-service auto-lock) stopped NEW logins
 * (TASK-BE-600) but every session issued before the lock kept refreshing: nothing in
 * auth-service consumed {@code account.locked}. Owner decision (2026-09-25): revoke on the
 * event; checking the account status on every refresh was rejected because an
 * account-service outage would then fail every active session's refresh. The accepted cost
 * is the propagation gap — between the lock and this use case running, a refresh still
 * succeeds. {@link Result#propagationLag()} makes that gap visible.
 *
 * <p><b>Idempotent per event.</b> The revoke runs at most once per {@code eventId}
 * ({@link EventDedupePort}, same transaction). Revoking twice would be harmless for the
 * tokens that existed at the lock, but not for the ones issued after it: if the account is
 * unlocked and the user signs in again, a redelivered copy of the old lock event would
 * revoke the NEW session. A redelivery is reported as {@link Outcome#DUPLICATE}, not as a
 * second revoke.
 *
 * <p><b>Tenant.</b> The revoke is NOT confined to {@code command.tenantId()}: it calls the
 * net-zero {@link ForceLogoutUseCase#execute(String)}. The tenant-confined overload
 * (TASK-BE-468) exists to stop an operator acting in one tenant from logging out another
 * tenant's account; here the actor is account-service reporting a fact about its own
 * account, and the account id is a UUID that names exactly one account. Passing the tenant
 * would instead turn the lock into a silent no-op whenever the confinement check cannot see
 * the account in that tenant — it looks the account up by its credential, and an account
 * that signs in only through a social provider has no credential row. The SAS revoke is
 * confined to the account id on its own terms (see {@code OAuthAuthorizationRevocationPort}).
 *
 * <p><b>Failures propagate.</b> Nothing here catches: a failed revoke rolls the dedupe row
 * back and the exception reaches the Kafka error handler, which retries and then routes the
 * record to {@code account.locked.dlq}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeSessionsOnAccountLockedUseCase {

    static final String EVENT_TYPE = "account.locked";

    private final EventDedupePort eventDedupePort;
    private final ForceLogoutUseCase forceLogoutUseCase;

    @Transactional
    public Result execute(RevokeSessionsOnAccountLockedCommand command) {
        int[] revoked = {0};
        EventDedupePort.Outcome outcome = eventDedupePort.process(
                command.eventId(), EVENT_TYPE,
                () -> revoked[0] = forceLogoutUseCase.execute(command.accountId()).revokedTokenCount());

        Duration lag = command.lockedAt() != null
                ? Duration.between(command.lockedAt(), Instant.now())
                : null;

        if (outcome == EventDedupePort.Outcome.APPLIED) {
            log.info("account.locked: revoked sessions account={} tenant={} reason={} revokedTokens={} "
                            + "propagationLagMs={} eventId={}",
                    command.accountId(), command.tenantId(), command.reasonCode(), revoked[0],
                    lag != null ? lag.toMillis() : null, command.eventId());
            return new Result(Outcome.REVOKED, revoked[0], lag);
        }
        log.info("account.locked: duplicate event, sessions already revoked for it — skipped. "
                + "account={} eventId={}", command.accountId(), command.eventId());
        return new Result(Outcome.DUPLICATE, 0, lag);
    }

    public enum Outcome { REVOKED, DUPLICATE }

    /**
     * @param outcome             revoked now, or skipped as a redelivery
     * @param revokedTokenCount   refresh tokens revoked by this call (0 on a duplicate)
     * @param propagationLag      lock → revoke, or null when the event had no {@code lockedAt}
     */
    public record Result(Outcome outcome, int revokedTokenCount, Duration propagationLag) {}
}
