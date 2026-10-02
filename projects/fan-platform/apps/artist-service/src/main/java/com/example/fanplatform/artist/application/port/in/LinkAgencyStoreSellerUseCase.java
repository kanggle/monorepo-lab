package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

/**
 * Set or clear the store seller that sells an agency's goods — ADR-MONO-079 D2
 * (0..1, held on the fan side, verified against the store at write time).
 * TASK-MONO-748 AC-3.
 */
public interface LinkAgencyStoreSellerUseCase {

    /**
     * @param storeSellerId the ecommerce {@code seller_id}; {@code null} clears the link
     *        (no store lookup is needed to clear).
     * @throws com.example.fanplatform.artist.application.exception.StoreSellerNotFoundException store has no such seller
     * @throws com.example.fanplatform.artist.application.exception.StoreSellerClosedException seller is CLOSED
     * @throws com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException
     *         the seller could not be verified — nothing is saved (fail-closed)
     */
    AgencyView linkStoreSeller(ActorContext actor, String agencyId, String storeSellerId);
}
