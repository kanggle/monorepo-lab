package com.example.admin.infrastructure.security;

/**
 * Request-scoped carrier for the bootstrap token's resolved subject
 * (operator_id UUID v7). Populated by {@link BootstrapAuthenticationFilter}
 * for the 2FA enroll/verify sub-tree (admin-api.md Authentication Exceptions);
 * consumed by {@code AdminAuthController}.
 *
 * <p>Stored on the current {@link HttpServletRequest request attribute} rather
 * than a thread-local so that async dispatch does not leak state.
 */
public final class BootstrapContext {

    public static final String ATTRIBUTE = "admin.bootstrapContext";

    private final String operatorId;
    private final String jti;

    public BootstrapContext(String operatorId, String jti) {
        this.operatorId = operatorId;
        this.jti = jti;
    }

    public String operatorId() {
        return operatorId;
    }

    public String jti() {
        return jti;
    }
}
