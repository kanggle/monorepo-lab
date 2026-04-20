package com.wms.master.application.port.in;

import java.time.LocalDate;

/**
 * Inbound port for the scheduled lot-expiration batch. The caller passes the
 * "today" date so tests (and the scheduler itself) can control the boundary
 * without freezing the clock globally.
 *
 * <p>Per {@code specs/services/master-service/domain-model.md} §6 and the
 * task spec §Edge Cases, the query is strict
 * ({@code expiry_date < today}) — a Lot whose {@code expiryDate == today}
 * does not expire until tomorrow's run.
 */
public interface ExpireLotsBatchUseCase {

    /**
     * @return the expiration outcome (how many lots were considered, how many
     *         successfully transitioned to EXPIRED, how many failed)
     */
    LotExpirationResult execute(LocalDate today);

    record LotExpirationResult(int considered, int expired, int failed) {
    }
}
