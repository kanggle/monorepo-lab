package com.example.fanplatform.artist.application.port.out;

import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;

import java.util.Optional;

/**
 * Outbound port: read-through Redis cache for the artist directory search.
 *
 * <p>Per task spec § Implementation Notes: TTL = 5 min, key
 * {@code cache:fan-platform:artist:directory:<query-hash>}. Invalidated on
 * publish / update / archive. Fail-open — when the cache is unavailable, the
 * adapter returns {@link Optional#empty()} on {@link #get(String, String)} and
 * a no-op on writes/invalidations. The application service then falls through
 * to the DB.
 */
public interface ArtistDirectoryCache {

    /**
     * @param tenantId scopes the key namespace
     * @param queryKey deterministic hash of the directory query parameters
     * @return cached page, or empty when missing / cache unavailable
     */
    Optional<DirectorySearchResult> get(String tenantId, String queryKey);

    /** Best-effort write. Cache unavailability is logged + counted, never thrown. */
    void put(String tenantId, String queryKey, DirectorySearchResult value);

    /**
     * Invalidates the entire directory namespace for a tenant. Called from the
     * application service after a publish / update / archive that may change
     * directory contents.
     */
    void invalidateAll(String tenantId);
}
