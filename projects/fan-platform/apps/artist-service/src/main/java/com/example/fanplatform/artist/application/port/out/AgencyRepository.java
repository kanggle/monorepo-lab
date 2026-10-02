package com.example.fanplatform.artist.application.port.out;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound port for agency persistence (TASK-MONO-748, ADR-MONO-079 D1). Every
 * read is tenant-scoped; a cross-tenant id is indistinguishable from a missing one.
 */
public interface AgencyRepository {

    /**
     * Persist a new agency. Throws
     * {@link com.example.fanplatform.artist.application.exception.AgencyNameConflictException}
     * when {@code (tenant_id, name)} collides (the constraint is the race guard).
     */
    Agency insert(Agency agency);

    /** Persist mutable changes; same name-conflict translation as {@link #insert}. */
    Agency update(Agency agency);

    Optional<Agency> findById(AgencyId id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);

    /** Tenant's agencies, ordered by name then id. */
    PageResult<Agency> findPage(String tenantId, int page, int size);

    /**
     * {@code id → name} for the given ids within the tenant — the batch lookup the
     * read paths use to display an artist's / group's agency name from the entity.
     * Ids not found in the tenant are simply absent from the map.
     */
    Map<String, String> findNamesByIds(String tenantId, Collection<String> ids);
}
