package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

/**
 * Inbound port: lookup a single artist by id. Visibility:
 * <ul>
 *   <li>{@code PUBLISHED} — any authenticated caller in the same tenant.</li>
 *   <li>{@code DRAFT}/{@code ARCHIVED} — admin only; ordinary callers see 404.</li>
 *   <li>cross-tenant — 404 (do not leak existence).</li>
 * </ul>
 */
public interface GetArtistUseCase {

    ArtistView getById(ActorContext actor, String artistId);
}
