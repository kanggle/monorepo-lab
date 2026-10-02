package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.exception.AgencyArchivedException;
import com.example.fanplatform.artist.application.exception.AgencyNotFoundException;
import com.example.fanplatform.artist.application.port.out.AgencyRepository;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agency lookups shared by the artist / group / agency services (TASK-MONO-748).
 *
 * <p>{@link #names} is how the read paths display an agency name <em>from the
 * entity</em> (ADR-MONO-079 D1). It returns an empty map — without touching the
 * repository — when no row is affiliated, so a page with no agencies costs no query.
 */
final class AgencySupport {

    private AgencySupport() {
    }

    /** {@code agencyId → name} for the non-null ids, tenant-scoped. */
    static Map<String, String> names(AgencyRepository repo, String tenantId,
                                     Collection<AgencyId> agencyIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgencyId id : agencyIds) {
            if (id != null) ids.add(id.value());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repo.findNamesByIds(tenantId, ids);
    }

    static Map<String, String> names(AgencyRepository repo, String tenantId, AgencyId agencyId) {
        return names(repo, tenantId, java.util.Collections.singletonList(agencyId));
    }

    /** Loads the agency or 404 {@code AGENCY_NOT_FOUND} (bad format = not found — no leak). */
    static Agency load(AgencyRepository repo, String rawId, String tenantId) {
        AgencyId id;
        try {
            id = AgencyId.of(Objects.requireNonNull(rawId, "agencyId"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AgencyNotFoundException(String.valueOf(rawId));
        }
        return repo.findById(id, tenantId).orElseThrow(() -> new AgencyNotFoundException(rawId));
    }

    /** Loads an agency a NEW affiliation may point at: exists in the tenant AND is ACTIVE. */
    static Agency loadAffiliable(AgencyRepository repo, String rawId, String tenantId) {
        Agency agency = load(repo, rawId, tenantId);
        if (!agency.isActive()) {
            throw new AgencyArchivedException(rawId);
        }
        return agency;
    }
}
