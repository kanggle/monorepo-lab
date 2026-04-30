package com.example.admin.application.exception;

/**
 * Raised when the {@code roles} payload of an operator create or role-patch
 * request contains a name that does not exist in {@code admin_roles}. Surfaces
 * as {@code 400 ROLE_NOT_FOUND}.
 */
public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }
}
