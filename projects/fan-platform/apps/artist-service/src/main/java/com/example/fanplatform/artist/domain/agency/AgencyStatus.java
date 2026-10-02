package com.example.fanplatform.artist.domain.agency;

/**
 * Agency lifecycle. {@code ARCHIVED} is terminal: an archived agency keeps its
 * existing affiliations (their display does not vanish) but accepts no NEW ones and
 * no seller link change. TASK-MONO-748.
 */
public enum AgencyStatus {
    ACTIVE,
    ARCHIVED
}
