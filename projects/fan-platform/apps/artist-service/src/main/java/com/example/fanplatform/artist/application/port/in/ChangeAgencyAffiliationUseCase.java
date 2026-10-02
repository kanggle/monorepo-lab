package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

/**
 * Change which agency an artist or a group belongs to (TASK-MONO-748 AC-2 «소속 변경»).
 * {@code agencyId == null} removes the affiliation (solo / unaffiliated — Edge Case 2).
 * The target agency must exist in the caller's tenant and be ACTIVE.
 */
public interface ChangeAgencyAffiliationUseCase {

    ArtistView changeArtistAgency(ActorContext actor, String artistId, String agencyId);

    ArtistGroupView changeGroupAgency(ActorContext actor, String groupId, String agencyId);
}
