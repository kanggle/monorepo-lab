package com.example.fanplatform.artist.application.exception;

/**
 * Thrown by use cases that mutate state when the caller lacks an admin-tier
 * role. Surfaces as 403 {@code FORBIDDEN}.
 */
public class AdminRoleRequiredException extends RuntimeException {

    public AdminRoleRequiredException() {
        super("Admin role required");
    }
}
