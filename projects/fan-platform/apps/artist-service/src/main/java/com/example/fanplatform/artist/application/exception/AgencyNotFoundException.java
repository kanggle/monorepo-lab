package com.example.fanplatform.artist.application.exception;

/** 404 {@code AGENCY_NOT_FOUND} — missing OR cross-tenant agency (existence not leaked). TASK-MONO-748. */
public class AgencyNotFoundException extends RuntimeException {

    public AgencyNotFoundException(String agencyId) {
        super("Agency not found: " + agencyId);
    }
}
