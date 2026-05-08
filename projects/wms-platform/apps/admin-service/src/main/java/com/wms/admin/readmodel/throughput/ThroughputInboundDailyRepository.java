package com.wms.admin.readmodel.throughput;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThroughputInboundDailyRepository
        extends JpaRepository<ThroughputInboundDailyEntity, ThroughputDailyId> {

    List<ThroughputInboundDailyEntity> findByWarehouseIdAndDateBetweenOrderByDateAsc(
            UUID warehouseId, LocalDate from, LocalDate to);
}
