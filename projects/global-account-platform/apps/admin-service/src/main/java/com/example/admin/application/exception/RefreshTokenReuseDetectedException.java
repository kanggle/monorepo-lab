package com.example.admin.application.exception;

/**
 * TASK-BE-040 — a refresh JWT whose registry row is already revoked has been
 * presented again. Triggers bulk-revocation of the operator's remaining
 * refresh tokens (reason=REUSE_DETECTED) and surfaces as
 * 401 REFRESH_TOKEN_REUSE_DETECTED.
 *
 * <p>TASK-BE-040-fix — carries the operator's external UUID (read from the
 * verified registry row, NOT from the presented JWT payload) so the
 * controller can populate the audit row without decoding unverified JWT
 * payload claims.
 */
public class RefreshTokenReuseDetectedException extends RuntimeException {

    private final String operatorId;

    public RefreshTokenReuseDetectedException(String message, String operatorId) {
        super(message);
        this.operatorId = operatorId;
    }

    public RefreshTokenReuseDetectedException(String message) {
        this(message, null);
    }

    /**
     * @return the operator's external UUID when resolvable from the verified
     *         registry row, otherwise {@code null}.
     */
    public String operatorId() {
        return operatorId;
    }
}
