package com.example.fanplatform.artist.application.exception;

/**
 * 422 {@code AGENCY_ARCHIVED} — an ARCHIVED agency accepts no new affiliation, no
 * rename and no seller-link change. Existing affiliations keep displaying. TASK-MONO-748.
 */
public class AgencyArchivedException extends RuntimeException {

    public AgencyArchivedException(String agencyId) {
        super("Agency is archived: " + agencyId);
    }
}
