package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;

/**
 * Maps the list-filter {@code active} flag (masterdata-api.md § list query) to
 * the persisted {@link MasterStatus} used by the filtered repository queries.
 * {@code null} = no lifecycle constraint; {@code true} = ACTIVE only;
 * {@code false} = RETIRED only. Shared by all five master-data JPA adapters so
 * the mapping lives in exactly one place.
 */
final class MasterStatusFilters {

    private MasterStatusFilters() {
    }

    static MasterStatus toStatus(Boolean active) {
        if (active == null) {
            return null;
        }
        return active ? MasterStatus.ACTIVE : MasterStatus.RETIRED;
    }
}
