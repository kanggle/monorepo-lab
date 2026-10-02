package com.example.fanplatform.artist.application.port.out;

import java.util.Optional;

/**
 * Outbound port: «does the store have this seller, and in what state?» — the write-time
 * verification ADR-MONO-079 D2 requires before an agency may point at a store seller
 * (different database → no FK). TASK-MONO-748.
 *
 * <p>Caller expectations (the fan side of the cross-project seller lookup contract,
 * {@code artist-api.md} § Store seller verification):
 * <ul>
 *   <li>{@code Optional.of(status)} — the store answered and has the seller;
 *       {@code status} is the store's {@code SellerStatus} name verbatim
 *       ({@code PENDING_PROVISIONING} / {@code ACTIVE} / {@code SUSPENDED} / {@code CLOSED}).</li>
 *   <li>{@code Optional.empty()} — the store answered <b>authoritatively</b> that it has
 *       no such seller. Only a definite answer may produce this; "could not ask" must
 *       NOT be mapped here.</li>
 *   <li>throws {@link com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException}
 *       on <b>anything else</b> — transport error, timeout, auth failure, non-2xx other
 *       than a definite not-found, malformed body. The caller then refuses to save
 *       (fail-closed).</li>
 * </ul>
 */
public interface StoreSellerDirectory {

    Optional<String> findStatus(String sellerId);
}
