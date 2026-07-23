package com.example.scmplatform.logistics.application.port.outbound;

import com.example.scmplatform.logistics.domain.model.Dispatch;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: persist and load {@link Dispatch} aggregates. Backed by the JPA persistence
 * adapter; the domain never sees JPA (Hexagonal).
 */
public interface DispatchPersistencePort {

    Optional<Dispatch> findById(UUID id);

    Optional<Dispatch> findByShipmentId(UUID shipmentId);

    Dispatch save(Dispatch dispatch);
}
