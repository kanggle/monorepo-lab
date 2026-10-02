package com.example.fanplatform.artist.application.exception;

/**
 * 503 {@code STORE_SELLER_LOOKUP_UNAVAILABLE} — the seller could NOT be verified
 * (transport / auth / malformed answer / unrecognised status / no lookup wired).
 * Fail-closed: the link is NOT saved (TASK-MONO-748 AC-3, Failure Scenario 2 —
 * «셀러 조회 장애 때 검증 없이 저장해 존재하지 않는 셀러를 가리킨다»).
 */
public class StoreSellerLookupUnavailableException extends RuntimeException {

    public StoreSellerLookupUnavailableException(String message) {
        super(message);
    }

    public StoreSellerLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
