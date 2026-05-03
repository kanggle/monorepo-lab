package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.domain.artist.ArtistType;

import java.util.List;

/**
 * Read-heavy directory search. Returns only PUBLISHED artists in the caller's
 * tenant. Backed by a Redis read-through cache (per
 * {@code rules/traits/read-heavy.md} R3).
 */
public interface SearchArtistDirectoryUseCase {

    DirectorySearchResult search(SearchArtistDirectoryQuery query);

    record SearchArtistDirectoryQuery(
            ActorContext actor,
            String q,
            ArtistType type,
            int page,
            int size
    ) {}

    record DirectorySearchResult(
            List<ArtistView> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
