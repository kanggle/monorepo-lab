package com.example.fanplatform.artist.application.exception;

/**
 * Thrown when registering an artist whose stage_name already exists in the
 * same tenant. Surfaces as 409 {@code STAGE_NAME_CONFLICT} per
 * {@code specs/contracts/http/artist-api.md}.
 */
public class StageNameConflictException extends RuntimeException {

    public StageNameConflictException(String stageName) {
        super("Stage name already exists in tenant: " + stageName);
    }
}
