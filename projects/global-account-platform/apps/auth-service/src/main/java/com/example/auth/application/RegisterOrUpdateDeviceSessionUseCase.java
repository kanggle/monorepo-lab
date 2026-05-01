package com.example.auth.application;

import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.session.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Registers a new device_session row on first observation of an
 * (account_id, device_fingerprint) pair, or refreshes {@code last_seen_at} on a known
 * (active) row. Spec: specs/services/auth-service/device-session.md (Lifecycle).
 *
 * <p>Always runs inside the calling use-case's transaction (login or refresh) so
 * eviction failures roll the login back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterOrUpdateDeviceSessionUseCase {

    private final DeviceSessionRepository deviceSessionRepository;
    private final EnforceConcurrentLimitUseCase enforceConcurrentLimitUseCase;

    /**
     * Registers or updates a device session within the caller's transaction.
     *
     * <p>TASK-BE-248 Phase 2b: {@code tenantId} is now required so that any eviction
     * events ({@code auth.session.revoked}) carry the correct tenant context.
     *
     * @param tenantId the tenant that owns the account (required, non-blank)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegisterDeviceSessionResult execute(String accountId, String tenantId,
                                               SessionContext ctx) {
        String fingerprint = normalizeFingerprint(ctx.deviceFingerprint());
        Instant now = Instant.now();

        // D3: "unknown" fingerprint always produces a new device_id (the unique key
        // (account_id, device_fingerprint, issued_at) tolerates duplicates because
        // issued_at differs). For known fingerprints, attempt to reuse the active row.
        if (!DeviceSession.UNKNOWN_FINGERPRINT.equals(fingerprint)) {
            Optional<DeviceSession> existing = deviceSessionRepository
                    .findActiveByAccountAndFingerprint(accountId, fingerprint);
            if (existing.isPresent()) {
                DeviceSession session = existing.get();
                session.touch(now, ctx.ipAddress(), ctx.resolvedGeoCountry());
                deviceSessionRepository.save(session);
                return new RegisterDeviceSessionResult(session.getDeviceId(), false, List.of());
            }
        }

        // New device — enforce concurrent limit BEFORE inserting (D4 step 2).
        List<String> evicted = enforceConcurrentLimitUseCase.enforce(accountId, tenantId);

        String deviceId = UuidV7.randomString();
        DeviceSession created = DeviceSession.create(
                deviceId, accountId, fingerprint,
                ctx.userAgent(), ctx.ipAddress(), ctx.resolvedGeoCountry(),
                now);
        deviceSessionRepository.save(created);
        log.info("Registered new device session: account={}, deviceId={}, evicted={}",
                accountId, deviceId, evicted.size());
        return new RegisterDeviceSessionResult(deviceId, true, evicted);
    }

    private static String normalizeFingerprint(String raw) {
        return (raw == null || raw.isBlank()) ? DeviceSession.UNKNOWN_FINGERPRINT : raw;
    }
}
