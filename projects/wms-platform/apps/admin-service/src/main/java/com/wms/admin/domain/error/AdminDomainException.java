package com.wms.admin.domain.error;

/**
 * Sealed root for admin-service domain failures.
 *
 * <p>Subtypes carry a stable {@code code} that maps 1:1 to the admin error
 * registry in {@code platform/error-handling.md § Admin}.
 *
 * <p>Permits 7 admin-domain subtypes plus the supporting NotFound + state
 * transition exceptions. Subtypes are deliberately exhaustive to allow the
 * GlobalExceptionHandler to switch over them without a default branch.
 */
public sealed class AdminDomainException extends RuntimeException
        permits UserEmailDuplicateException,
                RoleCodeDuplicateException,
                UserHasActiveAssignmentsException,
                RoleInUseException,
                RoleBuiltinImmutableException,
                SettingValidationErrorException,
                SettingImmutableFieldException,
                UserNotFoundException,
                RoleNotFoundException,
                AssignmentNotFoundException,
                SettingNotFoundException,
                StateTransitionInvalidException {

    private final String code;

    protected AdminDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
