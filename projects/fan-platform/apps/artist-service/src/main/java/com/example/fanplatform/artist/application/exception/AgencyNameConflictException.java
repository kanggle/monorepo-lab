package com.example.fanplatform.artist.application.exception;

/** 409 {@code AGENCY_NAME_CONFLICT} — {@code (tenant_id, name)} collides after normalisation. TASK-MONO-748. */
public class AgencyNameConflictException extends RuntimeException {

    public AgencyNameConflictException(String name) {
        super("Agency name already exists in tenant: " + name);
    }
}
