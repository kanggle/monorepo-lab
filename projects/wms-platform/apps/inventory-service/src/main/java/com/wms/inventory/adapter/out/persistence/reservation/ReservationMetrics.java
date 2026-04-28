package com.wms.inventory.adapter.out.persistence.reservation;

import com.wms.inventory.application.port.out.ReservationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers the {@code inventory.reservation.active.count} gauge — current
 * count of {@code RESERVED} rows. Scrapes happen outside the request thread,
 * so the count query runs in its own read-only transaction.
 */
@Component
public class ReservationMetrics {

    public ReservationMetrics(ReservationRepository repository,
                              MeterRegistry meterRegistry,
                              TransactionTemplate transactionTemplate) {
        TransactionTemplate readOnly = new TransactionTemplate(
                transactionTemplate.getTransactionManager());
        readOnly.setReadOnly(true);
        Gauge.builder("inventory.reservation.active.count",
                        () -> readOnly.execute(status -> repository.countActive()))
                .description("Current count of RESERVED reservations")
                .register(meterRegistry);
    }
}
